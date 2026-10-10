import Foundation
@testable import GameShelf

/// In-memory stand-in for the game endpoints with the semantics of the API: a per-user change
/// feed with tombstones, idempotent create (`201`/`200`), `PATCH` of selected fields and
/// `DELETE`. Tests can change data "on another device", make the next call of an operation fail
/// and hold a call until they release it.
actor FakeGameServer: GameAPI {
    enum Operation: Hashable, Sendable {
        case changes, create, update, delete, get
    }

    struct Request: Equatable, Sendable {
        var operation: Operation
        var gameID: Game.ID?
        var fields: Set<GameField> = []
        /// The cursor a `changes` request continued from.
        var cursor: String?
    }

    private(set) var games: [Game.ID: Game] = [:]
    private(set) var requests: [Request] = []
    /// Latest change number per game; `deleted` marks tombstones.
    private var feed: [Game.ID: (version: Int, deleted: Bool)] = [:]
    private var version = 0
    private var failures: [Operation: [(error: APIError, afterApplying: Bool)]] = [:]
    private var pendingFailure: APIError?
    private var isUnreachable = false

    private var holds: Set<Operation> = []
    private var heldRequest: CheckedContinuation<Void, Never>?
    private var arrivalWaiters: [CheckedContinuation<Void, Never>] = []

    // MARK: Test controls

    /// A game created on another device.
    func seed(_ game: Game) {
        games[game.id] = game
        record(game.id, deleted: false)
    }

    func editOnAnotherDevice(_ id: Game.ID, _ change: @Sendable (inout Game) -> Void) {
        guard var game = games[id] else { return }
        change(&game)
        game.updatedAt = timestamp()
        games[id] = game
        record(id, deleted: false)
    }

    func deleteOnAnotherDevice(_ id: Game.ID) {
        games[id] = nil
        record(id, deleted: true)
    }

    /// The server lost a game without a tombstone (e.g. restored from a backup).
    func loseWithoutTrace(_ id: Game.ID) {
        games[id] = nil
        feed[id] = nil
    }

    /// The next call of `operation` fails with `error` – after changing the data when
    /// `afterApplying` is set (the response got lost).
    func fail(_ operation: Operation, with error: APIError, afterApplying: Bool = false) {
        failures[operation, default: []].append((error, afterApplying))
    }

    /// While unreachable, every call fails like a request without a connection.
    func setUnreachable(_ unreachable: Bool) {
        isUnreachable = unreachable
    }

    /// The next call of `operation` waits until ``release()``.
    func hold(_ operation: Operation) {
        holds.insert(operation)
    }

    /// Returns once a held call has arrived.
    func waitUntilHeld() async {
        guard heldRequest == nil else { return }
        await withCheckedContinuation { arrivalWaiters.append($0) }
    }

    func release() {
        let request = heldRequest
        heldRequest = nil
        request?.resume()
    }

    func requests(_ operation: Operation) -> [Request] {
        requests.filter { $0.operation == operation }
    }

    // MARK: GameAPI

    func changes(after cursor: String?, limit: Int) async throws -> GameChanges {
        try await begin(Request(operation: .changes, gameID: nil, cursor: cursor))
        let since = cursor.flatMap(Int.init) ?? 0
        let entries = feed
            .filter { $0.value.version > since }
            .sorted { $0.value.version < $1.value.version }
        let page = entries.prefix(limit)
        return GameChanges(
            games: page.filter { !$0.value.deleted }.compactMap { games[$0.key] },
            deletedIds: page.filter { $0.value.deleted }.map(\.key),
            cursor: String(page.last?.value.version ?? since),
            hasMore: entries.count > limit
        )
    }

    func create(id: Game.ID, _ values: SaveGameRequest) async throws -> CreateGameResult {
        try await begin(Request(operation: .create, gameID: id))
        if let existing = games[id] {
            try end()
            return .alreadyExisted(existing)
        }
        let game = Game(id: id, values: values, createdAt: timestamp())
        games[id] = game
        record(id, deleted: false)
        try end()
        return .created(game)
    }

    func update(id: Game.ID, fields: Set<GameField>, from values: SaveGameRequest) async throws -> Game {
        try await begin(Request(operation: .update, gameID: id, fields: fields))
        guard var game = games[id] else { throw Self.notFound }
        game.apply(fields, from: values)
        game.updatedAt = timestamp()
        games[id] = game
        record(id, deleted: false)
        try end()
        return game
    }

    func delete(id: Game.ID) async throws {
        try await begin(Request(operation: .delete, gameID: id))
        guard games.removeValue(forKey: id) != nil else { throw Self.notFound }
        record(id, deleted: true)
        try end()
    }

    func game(id: Game.ID) async throws -> Game {
        try await begin(Request(operation: .get, gameID: id))
        guard let game = games[id] else { throw Self.notFound }
        return game
    }

    // MARK: Helpers

    static let notFound = APIError.server(statusCode: 404, code: .gameNotFound, details: [])

    private func begin(_ request: Request) async throws {
        if isUnreachable {
            throw APIError.network(.notConnectedToInternet)
        }
        requests.append(request)
        if holds.remove(request.operation) != nil {
            await withCheckedContinuation { continuation in
                heldRequest = continuation
                let waiters = arrivalWaiters
                arrivalWaiters = []
                waiters.forEach { $0.resume() }
            }
        }
        guard var queued = failures[request.operation], !queued.isEmpty else { return }
        let failure = queued.removeFirst()
        failures[request.operation] = queued
        if failure.afterApplying {
            pendingFailure = failure.error
        } else {
            throw failure.error
        }
    }

    /// Throws a failure that was set to happen after the data changed.
    private func end() throws {
        if let error = pendingFailure {
            pendingFailure = nil
            throw error
        }
    }

    private func record(_ id: Game.ID, deleted: Bool) {
        version += 1
        feed[id] = (version, deleted)
    }

    /// Server timestamps differ from the device's, so tests can tell whose copy is stored.
    private func timestamp() -> Date {
        Date(timeIntervalSince1970: 1_800_000_000 + Double(version))
    }
}
