import UIKit

/// Composition root: builds the object graph once per process, shared by all windows.
@MainActor
final class AppContainer {
    let session: SessionStore
    /// The offline collection (``SyncEngine/repository``) and its sync.
    let sync: SyncEngine
    let barcodeLookup: any BarcodeLookupService
    let gameSearch: any GameSearchService
    /// Where the session is read from when it couldn't be at launch (``SessionStore/State/restoring``).
    private let sessionStorage: any SessionStorage

    init(
        session: SessionStore,
        sync: SyncEngine,
        barcodeLookup: any BarcodeLookupService,
        gameSearch: any GameSearchService,
        sessionStorage: any SessionStorage
    ) {
        self.session = session
        self.sync = sync
        self.barcodeLookup = barcodeLookup
        self.gameSearch = gameSearch
        self.sessionStorage = sessionStorage
    }

    static func live() -> AppContainer {
        let storage = KeychainSessionStorage()
        #if DEBUG
        let erasesLocalData = DebugLaunchOptions.current.resetSession
        if erasesLocalData {
            try? storage.clear()
        }
        #else
        let erasesLocalData = false
        #endif

        // Before the device is first unlocked after a restart (iOS may launch the app in advance), the
        // session can't be read; it is restored once it can be (``start()``) instead of signing out.
        let restored: StoredSession?
        let isRestoring: Bool
        do {
            restored = try restoredSession(from: storage, databaseURL: GameStore.defaultURL)
            isRestoring = false
        } catch {
            debugLog("The session can't be read yet: \(error)")
            restored = nil
            isRestoring = true
        }
        let tokens = TokenManager(storage: storage, session: restored)
        let api = APIClient(baseURL: AppConfiguration.apiBaseURL, tokens: tokens)
        let repository = GameRepository(store: GameStore.makeDefault(erasingExisting: erasesLocalData), status: SyncStatus())
        let sync = SyncEngine(repository: repository, api: RemoteGameAPI(api: api))
        return AppContainer(
            session: SessionStore(
                auth: RemoteAuthService(api: api),
                tokens: tokens,
                restoredSession: restored,
                isRestoring: isRestoring,
                userData: sync
            ),
            sync: sync,
            barcodeLookup: RemoteBarcodeLookupService(api: api),
            gameSearch: RemoteGameSearchService(api: api),
            sessionStorage: storage
        )
    }

    /// Starts what runs once per process, whichever windows are open: restoring the session and
    /// preparing its data, ending an expired session and following the network.
    func start() {
        let session = session
        let sync = sync
        let storage = sessionStorage
        Task {
            if session.state == .restoring {
                await session.finishRestoring(await Self.storedSession(from: storage))
            }
            await session.resumeSession()
            Self.removeResponsesCachedByEarlierVersions()
            #if DEBUG
            await DebugLaunchOptions.current.autoLoginIfNeeded(session)
            #endif
        }
        Task { await session.observeSessionEnds() }
        Task { await sync.observeNetwork() }
    }

    /// The stored session at launch. A sign-out or account deletion that didn't finish – the app was killed
    /// half-way, or the data couldn't be wiped – is finished first, before anything is shown: its stored
    /// session is cleared, never restored, and the local data and the cached cover images are wiped
    /// (docs/offline-sync.md, "Local data"). The wipe stays marked pending until the session is cleared too.
    /// Throws while the session can't be read yet (before the device's first unlock after a restart).
    static func restoredSession(
        from storage: any SessionStorage,
        databaseURL: URL,
        coverCache: URLCache = .shared
    ) throws -> StoredSession? {
        let session = try storage.load()
        guard GameStore.isWipePending(at: databaseURL) else { return session }
        var isSessionCleared = true
        do {
            try storage.clear()
        } catch {
            debugLog("Failed to clear the session of an unfinished sign-out: \(error)")
            isSessionCleared = false
        }
        coverCache.removeAllCachedResponses()
        do {
            try GameStore.finishPendingWipe(at: databaseURL, removingMark: isSessionCleared)
        } catch {
            // Finished by the next activation, before it reads anything.
            debugLog("Failed to finish wiping the local data: \(error)")
        }
        return nil
    }

    /// Reads the stored session, waiting until the device has been unlocked while it can't be read.
    private static func storedSession(from storage: any SessionStorage) async -> StoredSession? {
        let (unlocks, continuation) = AsyncStream.makeStream(of: Void.self, bufferingPolicy: .bufferingNewest(1))
        let observer = NotificationCenter.default.addObserver(
            forName: UIApplication.protectedDataDidBecomeAvailableNotification, object: nil, queue: nil
        ) { _ in
            continuation.yield()
        }
        defer { NotificationCenter.default.removeObserver(observer) }
        // Tried at once, and again whenever protected data becomes available.
        continuation.yield()
        for await _ in unlocks {
            do {
                return try restoredSession(from: storage, databaseURL: GameStore.defaultURL)
            } catch {
                debugLog("The session can't be read yet: \(error)")
            }
        }
        return nil
    }

    /// Versions before the API client stopped caching let API responses – the collection – into the
    /// shared URL cache, which the cover images use; it is emptied once (docs/mobile-spec.md, "API basics").
    private static func removeResponsesCachedByEarlierVersions() {
        let key = "removedCachedAPIResponses"
        guard UIApplication.shared.isProtectedDataAvailable, !UserDefaults.standard.bool(forKey: key) else { return }
        URLCache.shared.removeAllCachedResponses()
        UserDefaults.standard.set(true, forKey: key)
    }
}
