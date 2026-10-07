import Foundation
import Network

/// Pushes local changes to the API and pulls changes made on other devices
/// (docs/offline-sync.md, "Sync run" and "When it runs").
///
/// Only one run is active at a time; requests made while one is active schedule exactly one more
/// run after it. After a temporary failure the engine retries with exponential backoff while the
/// app is in the foreground; any other trigger retries at once. Every write of a run goes through
/// the ``GameStore`` with the access of the user the engine was activated for, so a run that is
/// still in flight after sign-out can't write anything.
@MainActor
final class SyncEngine {
    /// Page size of the change feed.
    static let pageSize = 500

    let status: SyncStatus
    let repository: GameRepository

    private let store: GameStore
    private let api: any GameAPI
    private let backoff: SyncBackoff
    private let now: @Sendable () -> Date

    private var access: StoreAccess?
    private var activations = 0
    private var isAppActive = false

    private var driver: Task<Void, Never>?
    private var driverID = 0
    private var requestedRuns = 0
    private var completedRuns = 0
    /// Callers of ``syncNow()`` waiting for the run that starts after their request.
    private var waiters: [(run: Int, continuation: CheckedContinuation<Result<Void, any Error>, Never>)] = []
    private var consecutiveFailures = 0
    private var retryTask: Task<Void, Never>?

    init(
        repository: GameRepository,
        api: any GameAPI,
        backoff: SyncBackoff = SyncBackoff(),
        now: @escaping @Sendable () -> Date = { .now }
    ) {
        self.repository = repository
        self.status = repository.status
        self.store = repository.store
        self.api = api
        self.backoff = backoff
        self.now = now
        repository.onLocalChange = { [weak self] in
            self?.requestSync()
        }
    }

    // MARK: - Triggers

    /// Asks for a run: after a local change, sign-in, returning to the foreground or regaining the
    /// network. A scheduled retry runs now instead.
    func requestSync() {
        guard access != nil else { return }
        requestedRuns += 1
        cancelRetry()
        startDriverIfNeeded()
    }

    /// A run on behalf of the user (pull-to-refresh, "Sync now"): waits for a run that starts after
    /// this request and throws its failure.
    func syncNow() async throws {
        guard access != nil else { throw CancellationError() }
        requestSync()
        let run = requestedRuns
        let result = await withCheckedContinuation { continuation in
            waiters.append((run, continuation))
        }
        try result.get()
    }

    /// Follows the scene phase: becoming active syncs; backoff retries only happen while active.
    func setAppActive(_ isActive: Bool) {
        guard isActive != isAppActive else { return }
        isAppActive = isActive
        if isActive {
            requestSync()
        } else {
            cancelRetry()
        }
    }

    func setNetworkAvailable(_ isAvailable: Bool) {
        let regained = isAvailable && status.isNetworkUnavailable
        status.isNetworkUnavailable = !isAvailable
        if regained {
            requestSync()
        }
    }

    /// Follows the device's network connection for the lifetime of the calling task.
    func observeNetwork() async {
        for await path in NWPathMonitor() {
            setNetworkAvailable(path.status == .satisfied)
        }
    }

    // MARK: - Owner

    /// Loads the local data of `ownerID` (wiping another user's data first) and starts syncing.
    func activate(ownerID: User.ID) async {
        if access?.ownerID == ownerID {
            requestSync()
            return
        }
        access = nil
        repository.deactivate()
        await stopRunning()
        activations += 1
        let activation = activations
        do {
            let (access, snapshot) = try await store.activate(ownerID: ownerID)
            guard activation == activations else { return }
            self.access = access
            consecutiveFailures = 0
            status.isServerUnreachable = false
            status.lastErrorMessage = nil
            status.hasUndoneRejectedChanges = false
            repository.activate(access, with: snapshot)
            requestSync()
        } catch {
            debugLog("Failed to open the local data: \(error)")
            status.lastErrorMessage = ErrorMessage.message(for: error)
        }
    }

    /// Stops syncing. With `erasingData`, the local data is wiped after the running sync has ended.
    func deactivate(erasingData: Bool) async {
        access = nil
        activations += 1
        repository.deactivate()
        await stopRunning()
        guard erasingData else { return }
        do {
            try await store.erase()
        } catch {
            debugLog("Failed to erase the local data: \(error)")
        }
    }

    // MARK: - Runs

    private func startDriverIfNeeded() {
        guard driver == nil, let access else { return }
        driverID += 1
        let id = driverID
        driver = Task {
            await drive(access: access, id: id)
        }
    }

    private func drive(access: StoreAccess, id: Int) async {
        status.isSyncing = true
        while completedRuns < requestedRuns, !Task.isCancelled {
            let run = requestedRuns
            let result = await perform(access)
            // A cancelled driver's waiters have already been answered.
            guard !Task.isCancelled else { break }
            completedRuns = run
            finish(result)
            resumeWaiters(upTo: run, with: result)
        }
        if driverID == id {
            driver = nil
            status.isSyncing = false
        }
    }

    private func perform(_ access: StoreAccess) async -> Result<Void, any Error> {
        do {
            try await push(access)
            try await pull(access)
            return .success(())
        } catch {
            return .failure(error)
        }
    }

    private func finish(_ result: Result<Void, any Error>) {
        switch result {
        case .success:
            consecutiveFailures = 0
            status.isServerUnreachable = false
            status.lastErrorMessage = nil
        case .failure(let error):
            if ErrorMessage.isCancellation(error) || error as? LocalStoreError == .accessRevoked { return }
            debugLog("Sync failed: \(error)")
            status.lastErrorMessage = ErrorMessage.message(for: error)
            status.isServerUnreachable = Self.isUnreachable(error)
            // An ended session stops syncing until the next sign-in.
            guard error as? APIError != .sessionExpired else { return }
            consecutiveFailures += 1
            scheduleRetry()
        }
    }

    private func resumeWaiters(upTo run: Int, with result: Result<Void, any Error>) {
        let ready = waiters.filter { $0.run <= run }
        waiters.removeAll { $0.run <= run }
        for waiter in ready {
            waiter.continuation.resume(returning: result)
        }
    }

    /// Cancels the running sync and waits until it has ended.
    private func stopRunning() async {
        cancelRetry()
        let running = driver
        driver = nil
        running?.cancel()
        status.isSyncing = false
        completedRuns = requestedRuns
        let pending = waiters
        waiters = []
        for waiter in pending {
            waiter.continuation.resume(returning: .failure(CancellationError()))
        }
        await running?.value
    }

    private func scheduleRetry() {
        guard isAppActive, access != nil else { return }
        let delay = backoff.delay(afterFailures: consecutiveFailures)
        retryTask?.cancel()
        retryTask = Task {
            do {
                try await Task.sleep(for: delay)
            } catch {
                return
            }
            retryTask = nil
            requestSync()
        }
    }

    private func cancelRetry() {
        retryTask?.cancel()
        retryTask = nil
    }

    // MARK: - Push

    /// Pushes every pending change, oldest first. A temporary failure ends the run.
    private func push(_ access: StoreAccess) async throws {
        for id in try await store.pendingGameIDs(access: access) {
            var pushAgain = true
            while pushAgain {
                try Task.checkCancellation()
                guard let pending = try await store.beginPush(of: id, access: access) else { break }
                let result = try await send(pending)
                let completion = try await store.completePush(pending, with: result, access: access)
                repository.apply(completion.snapshot)
                if case .rejected = result {
                    status.hasUndoneRejectedChanges = true
                }
                pushAgain = completion.pushAgain
            }
        }
    }

    /// Sends one change. Answers that settle the change are returned; temporary failures are thrown.
    private func send(_ pending: PendingPush) async throws -> PushResult {
        let change = pending.change
        let id = change.gameID
        do {
            switch change.kind {
            case .create:
                guard let game = pending.game else { return .gameGone }
                switch try await api.create(id: id, SaveGameRequest(game: game)) {
                case .created(let server): return .created(server)
                case .alreadyExisted(let server): return .alreadyExisted(server)
                }
            case .update:
                guard let game = pending.game else { return .gameGone }
                return .updated(try await api.update(id: id, fields: change.fields, from: SaveGameRequest(game: game)))
            case .delete:
                try await api.delete(id: id)
                return .deleted
            }
        } catch let error as APIError {
            if change.kind == .delete, error.statusCode == 404 {
                return .deleted
            }
            switch Self.classify(error) {
            case .temporary:
                throw error
            case .gameGone:
                return .gameGone
            case .rejected:
                debugLog("The server rejected \(change.kind.rawValue) of \(id): \(error)")
                return .rejected(restored: change.kind == .create ? nil : try await serverVersion(of: id))
            }
        }
    }

    /// The server's current version of a game whose update or delete was rejected; `nil` when it is gone.
    private func serverVersion(of id: Game.ID) async throws -> Game? {
        do {
            return try await api.game(id: id)
        } catch let error as APIError where error.statusCode == 404 {
            return nil
        }
    }

    enum FailureKind: Equatable {
        /// Keep the change and retry later (`429`, `5xx`, network failure, timeout, ended session).
        case temporary
        /// `404 GAME_NOT_FOUND`: the game was deleted on another device.
        case gameGone
        /// Any other `4xx`: the change can never succeed.
        case rejected
    }

    static func classify(_ error: APIError) -> FailureKind {
        switch error {
        case .server(404, .gameNotFound, _):
            .gameGone
        case .server(let statusCode, _, _) where statusCode == 401 || statusCode == 429:
            .temporary
        case .server(let statusCode, _, _) where (400..<500).contains(statusCode):
            .rejected
        case .server, .network, .invalidResponse, .sessionExpired:
            .temporary
        }
    }

    private static func isUnreachable(_ error: any Error) -> Bool {
        switch error {
        case APIError.network: true
        case is URLError: true
        default: false
        }
    }

    // MARK: - Pull

    /// Pulls the change feed until it has no more pages, each page with its cursor in one transaction.
    private func pull(_ access: StoreAccess) async throws {
        var cursor = try await store.cursor(access: access)
        var hasMore = true
        while hasMore {
            try Task.checkCancellation()
            let page: GameChanges
            do {
                page = try await api.changes(after: cursor, limit: Self.pageSize)
            } catch let error as APIError where cursor != nil && Self.requiresReset(error) {
                // The cursor can't be continued: start over, keeping games with pending changes.
                debugLog("Change feed reset: \(error)")
                repository.apply(try await store.resetForFullPull(access: access))
                cursor = nil
                continue
            }
            repository.apply(try await store.applyChanges(page, at: now(), access: access))
            cursor = page.cursor
            hasMore = page.hasMore
        }
    }

    /// `410 SYNC_RESET_REQUIRED`, or `400 VALIDATION_FAILED` for a malformed cursor.
    private static func requiresReset(_ error: APIError) -> Bool {
        error.statusCode == 410 || error.code == .syncResetRequired
            || (error.statusCode == 400 && error.code == .validationFailed)
    }
}

/// Exponential backoff after temporary failures: 2 s, 4 s, 8 s … at most 5 minutes.
struct SyncBackoff: Sendable {
    var initial: Duration = .seconds(2)
    var maximum: Duration = .seconds(300)

    func delay(afterFailures failures: Int) -> Duration {
        let exponent = min(max(failures - 1, 0), 16)
        return min(initial * (1 << exponent), maximum)
    }
}

// MARK: - Session

extension SyncEngine: UserDataLifecycle {
    func userDidSignIn(_ user: User) async {
        await activate(ownerID: user.id)
    }

    func userWillSignOut() async {
        await deactivate(erasingData: true)
    }

    func sessionDidExpire() async {
        await deactivate(erasingData: false)
    }
}
