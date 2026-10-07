import Foundation

/// The device's copy of the signed-in user's collection: games, pending changes and sync state
/// in SQLite (docs/offline-sync.md, "Local data").
///
/// Every operation that changes more than one thing – a local edit and its pending change, a
/// pulled page and its cursor, the result of a push – runs in one transaction, and every write
/// checks the caller's ``StoreAccess`` inside it. The games are also kept in memory, so the UI
/// gets a complete ``StoreSnapshot`` after each write without reading the database again.
actor GameStore {
    private let db: SQLiteConnection
    private let encoder = JSONEncoder.api()
    private let decoder = JSONDecoder.api()

    private var access: StoreAccess?
    private var generation = 0
    private var version = 0
    private var games: [Game.ID: Game] = [:]
    private var pendingCount = 0
    private var lastSyncedAt: Date?

    /// Opens (or creates) the database at `url`; `nil` keeps everything in memory (tests, previews).
    init(url: URL?) throws {
        let db = try SQLiteConnection(url: url)
        try Self.migrate(db)
        self.db = db
    }

    private static func migrate(_ db: SQLiteConnection) throws {
        try db.execute("PRAGMA journal_mode = WAL; PRAGMA synchronous = NORMAL;")
        let schemaVersion = try db.query("PRAGMA user_version") { $0.int(0) }.first ?? 0
        guard schemaVersion < 1 else { return }
        try db.transaction {
            try db.execute("""
            CREATE TABLE IF NOT EXISTS games (
                id TEXT PRIMARY KEY NOT NULL,
                data BLOB NOT NULL
            );
            CREATE TABLE IF NOT EXISTS pending_changes (
                game_id TEXT PRIMARY KEY NOT NULL,
                kind TEXT NOT NULL CHECK (kind IN ('CREATE', 'UPDATE', 'DELETE')),
                fields TEXT NOT NULL DEFAULT '[]',
                revision INTEGER NOT NULL,
                attempted INTEGER NOT NULL DEFAULT 0,
                queued_at REAL NOT NULL
            );
            CREATE TABLE IF NOT EXISTS sync_state (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                owner_user_id TEXT,
                cursor TEXT,
                last_synced_at REAL
            );
            INSERT OR IGNORE INTO sync_state (id) VALUES (1);
            PRAGMA user_version = 1;
            """)
        }
    }

    var snapshot: StoreSnapshot {
        StoreSnapshot(version: version, games: games, pendingCount: pendingCount, lastSyncedAt: lastSyncedAt)
    }

    // MARK: - Owner

    /// Makes the data available for `ownerID`. Data of any other user is wiped first.
    func activate(ownerID: User.ID) throws -> (StoreAccess, StoreSnapshot) {
        let storedOwner = try db.query("SELECT owner_user_id FROM sync_state WHERE id = 1") { $0.text(0) }.first ?? nil
        if storedOwner != ownerID {
            try db.transaction {
                try deleteAllData()
                try db.run("UPDATE sync_state SET owner_user_id = ? WHERE id = 1", [.text(ownerID)])
            }
        }
        try loadCache()
        generation += 1
        version += 1
        let access = StoreAccess(ownerID: ownerID, generation: generation)
        self.access = access
        return (access, snapshot)
    }

    /// Wipes everything (sign-out, account deletion) and revokes the current access.
    func erase() throws {
        generation += 1
        access = nil
        try db.transaction {
            try deleteAllData()
        }
        games = [:]
        pendingCount = 0
        lastSyncedAt = nil
        version += 1
    }

    private func deleteAllData() throws {
        try db.run("DELETE FROM games")
        try db.run("DELETE FROM pending_changes")
        try db.run("UPDATE sync_state SET owner_user_id = NULL, cursor = NULL, last_synced_at = NULL WHERE id = 1")
    }

    private func loadCache() throws {
        let rows = try db.query("SELECT id, data FROM games") { row in (row.text(0) ?? "", row.data(1)) }
        var loaded: [Game.ID: Game] = [:]
        loaded.reserveCapacity(rows.count)
        for (id, data) in rows {
            do {
                loaded[id] = try decoder.decode(Game.self, from: data)
            } catch {
                debugLog("Skipping unreadable stored game \(id): \(error)")
            }
        }
        games = loaded
        pendingCount = try countPendingChanges()
        lastSyncedAt = try db.query("SELECT last_synced_at FROM sync_state WHERE id = 1") { row in
            row.isNull(0) ? nil : Date(timeIntervalSince1970: row.double(0))
        }.first ?? nil
    }

    // MARK: - Local changes

    /// Adds a game created on this device.
    func insert(_ game: Game, at now: Date, access: StoreAccess) throws -> StoreSnapshot {
        try write(access) { changes in
            try put(game, &changes)
            try save(PendingChange(gameID: game.id, kind: .create, fields: [], revision: 1, attempted: false, queuedAt: now))
        }.snapshot
    }

    /// Applies an edit: only the fields whose value differs from `base` (the state the edit started
    /// from; the stored game by default) are changed and queued. Returns `nil` when nothing changed.
    func update(
        id: Game.ID,
        with request: SaveGameRequest,
        basedOn base: SaveGameRequest? = nil,
        at now: Date,
        access: StoreAccess
    ) throws -> StoreSnapshot? {
        try checkAccess(access)
        guard let stored = games[id] else { throw LocalStoreError.gameNotFound }
        let fields = request.changedFields(comparedTo: base ?? SaveGameRequest(game: stored))
        guard !fields.isEmpty else { return nil }
        return try write(access) { changes in
            var game = stored
            game.apply(fields, from: request)
            game.updatedAt = now
            try put(game, &changes)
            try queueEdit(of: id, fields: fields, at: now)
        }.snapshot
    }

    func setFavorite(_ favorite: Bool, id: Game.ID, at now: Date, access: StoreAccess) throws -> StoreSnapshot? {
        guard let stored = games[id] else { throw LocalStoreError.gameNotFound }
        var request = SaveGameRequest(game: stored)
        request.favorite = favorite
        return try update(id: id, with: request, at: now, access: access)
    }

    /// Removes the game at once and queues its deletion. Returns `nil` when there is no such game.
    func delete(id: Game.ID, at now: Date, access: StoreAccess) throws -> StoreSnapshot? {
        try checkAccess(access)
        guard games[id] != nil else { return nil }
        return try write(access) { changes in
            try remove(id, &changes)
            guard var change = try pendingChange(for: id) else {
                try save(PendingChange(gameID: id, kind: .delete, fields: [], revision: 1, attempted: false, queuedAt: now))
                return
            }
            switch change.kind {
            case .create where !change.attempted:
                // The server never saw the game.
                try dropPendingChange(for: id)
                return
            case .create, .update:
                change.kind = .delete
                change.fields = []
                change.revision += 1
                try save(change)
            case .delete:
                break
            }
        }.snapshot
    }

    /// Coalesces an edit of `fields` into the game's pending change.
    private func queueEdit(of id: Game.ID, fields: Set<GameField>, at now: Date) throws {
        guard var change = try pendingChange(for: id) else {
            try save(PendingChange(gameID: id, kind: .update, fields: fields, revision: 1, attempted: false, queuedAt: now))
            return
        }
        switch change.kind {
        case .update:
            change.fields.formUnion(fields)
        case .create where change.attempted:
            // An earlier attempt may have reached the server without these fields.
            change.fields.formUnion(fields)
        case .create:
            break
        case .delete:
            return
        }
        change.revision += 1
        try save(change)
    }

    // MARK: - Push

    /// Games with a pending change, oldest change first.
    func pendingGameIDs(access: StoreAccess) throws -> [Game.ID] {
        try checkAccess(access)
        return try db.query("SELECT game_id FROM pending_changes ORDER BY queued_at, rowid") { $0.text(0) ?? "" }
    }

    /// Starts pushing the game's pending change: marks it attempted and returns it with the local
    /// game. Returns `nil` when nothing is pending for the game any more.
    func beginPush(of id: Game.ID, access: StoreAccess) throws -> PendingPush? {
        try checkAccess(access)
        guard var change = try pendingChange(for: id) else { return nil }
        if !change.attempted {
            try db.run("UPDATE pending_changes SET attempted = 1 WHERE game_id = ?", [.text(id)])
            change.attempted = true
        }
        return PendingPush(change: change, game: games[id])
    }

    /// Records the server's answer to a pushed change (docs/offline-sync.md, "Sync run").
    ///
    /// If the game was not changed locally while the request was in flight, the pending change is
    /// dropped and the server's game stored. Otherwise the change stays (a `CREATE` becomes an
    /// `UPDATE` of all fields) and the server's game is stored with the local values of its fields.
    func completePush(_ push: PendingPush, with result: PushResult, access: StoreAccess) throws -> PushCompletion {
        let sent = push.change
        let id = sent.gameID
        var pushAgain = false
        let snapshot = try write(access) { changes in
            let current = try pendingChange(for: id)
            let local = games[id]
            let isUnchanged = current?.kind == sent.kind && current?.revision == sent.revision

            switch result {
            case .created(let server), .alreadyExisted(let server), .updated(let server):
                guard var current else {
                    if local != nil { try put(server, &changes) }
                    return
                }
                // Deleted locally in the meantime: the DELETE is pushed next.
                guard current.kind != .delete else { return }

                if case .alreadyExisted = result {
                    // Fields edited after the first attempt never reached the server.
                    if current.fields.isEmpty, isUnchanged {
                        try dropPendingChange(for: id)
                        try put(server, &changes)
                    } else {
                        current.kind = .update
                        if current.fields.isEmpty { current.fields = Set(GameField.allCases) }
                        try save(current)
                        try put(local.map { server.merging(current.fields, from: $0) } ?? server, &changes)
                        pushAgain = true
                    }
                } else if isUnchanged {
                    try dropPendingChange(for: id)
                    try put(server, &changes)
                } else {
                    if current.kind == .create {
                        current.kind = .update
                        current.fields = Set(GameField.allCases)
                        try save(current)
                    }
                    try put(local.map { server.merging(current.fields, from: $0) } ?? server, &changes)
                }

            case .deleted:
                if current?.kind == .delete {
                    try dropPendingChange(for: id)
                }

            case .gameGone:
                try remove(id, &changes)
                try dropPendingChange(for: id)

            case .rejected(let restored):
                // A local delete made in the meantime still wins.
                if current?.kind == .delete, sent.kind != .delete { return }
                try dropPendingChange(for: id)
                switch sent.kind {
                case .create:
                    try remove(id, &changes)
                case .update, .delete:
                    if let restored {
                        try put(restored, &changes)
                    } else {
                        try remove(id, &changes)
                    }
                }
            }
        }.snapshot
        return PushCompletion(snapshot: snapshot, pushAgain: pushAgain)
    }

    // MARK: - Pull

    /// The position in the change feed; `nil` before the first pulled page.
    func cursor(access: StoreAccess) throws -> String? {
        try checkAccess(access)
        return try db.query("SELECT cursor FROM sync_state WHERE id = 1") { $0.text(0) }.first ?? nil
    }

    /// Applies one page of the change feed together with its cursor; the last page also records
    /// the time of the completed sync.
    func applyChanges(_ page: GameChanges, at now: Date, access: StoreAccess) throws -> StoreSnapshot {
        try write(access) { changes in
            let pending = try pendingChangesByGame()
            for game in page.games {
                switch pending[game.id] {
                case nil:
                    try put(game, &changes)
                case let change? where change.kind == .update:
                    try put(games[game.id].map { game.merging(change.fields, from: $0) } ?? game, &changes)
                case .some:
                    // A pending CREATE keeps the local game; a pending DELETE ignores the server's.
                    continue
                }
            }
            for id in page.deletedIds {
                // Delete wins over any local change.
                try remove(id, &changes)
                if pending[id] != nil {
                    try dropPendingChange(for: id)
                }
            }
            try db.run("UPDATE sync_state SET cursor = ? WHERE id = 1", [.text(page.cursor)])
            if !page.hasMore {
                try db.run("UPDATE sync_state SET last_synced_at = ? WHERE id = 1", [.real(now.timeIntervalSince1970)])
                changes.lastSyncedAt = now
            }
        }.snapshot
    }

    /// `410 SYNC_RESET_REQUIRED`: drops every game without a pending change and forgets the cursor,
    /// so the next pull starts from the beginning.
    func resetForFullPull(access: StoreAccess) throws -> StoreSnapshot {
        try write(access) { changes in
            let pending = try pendingChangesByGame()
            for id in games.keys where pending[id] == nil {
                try remove(id, &changes)
            }
            try db.run("UPDATE sync_state SET cursor = NULL WHERE id = 1")
        }.snapshot
    }

    /// All pending changes, oldest first.
    func pendingChanges() throws -> [PendingChange] {
        try db.query("SELECT game_id, kind, fields, revision, attempted, queued_at FROM pending_changes ORDER BY queued_at, rowid") {
            try pendingChange(from: $0)
        }
    }

    // MARK: - Transactions

    /// Changes of one transaction, applied to the in-memory copy after it commits.
    private struct Changes {
        var upserted: [Game.ID: Game] = [:]
        var removed: Set<Game.ID> = []
        var lastSyncedAt: Date?
        var pendingCount = 0
    }

    private func checkAccess(_ access: StoreAccess) throws {
        guard access == self.access else { throw LocalStoreError.accessRevoked }
    }

    private func write<Result>(
        _ access: StoreAccess,
        _ body: (inout Changes) throws -> Result
    ) throws -> (result: Result, snapshot: StoreSnapshot) {
        try checkAccess(access)
        var changes = Changes()
        let result = try db.transaction {
            let result = try body(&changes)
            changes.pendingCount = try countPendingChanges()
            return result
        }
        for id in changes.removed {
            games[id] = nil
        }
        games.merge(changes.upserted) { _, new in new }
        pendingCount = changes.pendingCount
        if let date = changes.lastSyncedAt {
            lastSyncedAt = date
        }
        version += 1
        return (result, snapshot)
    }

    private func put(_ game: Game, _ changes: inout Changes) throws {
        try db.run("INSERT OR REPLACE INTO games (id, data) VALUES (?, ?)", [.text(game.id), .blob(encoder.encode(game))])
        changes.upserted[game.id] = game
        changes.removed.remove(game.id)
    }

    private func remove(_ id: Game.ID, _ changes: inout Changes) throws {
        try db.run("DELETE FROM games WHERE id = ?", [.text(id)])
        changes.upserted[id] = nil
        changes.removed.insert(id)
    }

    // MARK: - Pending changes

    private func countPendingChanges() throws -> Int {
        try db.query("SELECT COUNT(*) FROM pending_changes") { $0.int(0) }.first ?? 0
    }

    private func pendingChange(for id: Game.ID) throws -> PendingChange? {
        try db.query(
            "SELECT game_id, kind, fields, revision, attempted, queued_at FROM pending_changes WHERE game_id = ?",
            [.text(id)]
        ) { try pendingChange(from: $0) }.first
    }

    private func pendingChangesByGame() throws -> [Game.ID: PendingChange] {
        Dictionary(try pendingChanges().map { ($0.gameID, $0) }) { first, _ in first }
    }

    private func pendingChange(from row: SQLiteRow) throws -> PendingChange {
        let names = try decoder.decode([String].self, from: Data((row.text(2) ?? "[]").utf8))
        return PendingChange(
            gameID: row.text(0) ?? "",
            kind: PendingChange.Kind(rawValue: row.text(1) ?? "") ?? .update,
            fields: Set(names.compactMap(GameField.init(rawValue:))),
            revision: row.int(3),
            attempted: row.int(4) != 0,
            queuedAt: Date(timeIntervalSince1970: row.double(5))
        )
    }

    private func save(_ change: PendingChange) throws {
        let fields = String(decoding: try encoder.encode(change.fields.map(\.rawValue).sorted()), as: UTF8.self)
        try db.run(
            """
            INSERT INTO pending_changes (game_id, kind, fields, revision, attempted, queued_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (game_id) DO UPDATE SET
                kind = excluded.kind, fields = excluded.fields, revision = excluded.revision,
                attempted = excluded.attempted
            """,
            [
                .text(change.gameID), .text(change.kind.rawValue), .text(fields), .integer(Int64(change.revision)),
                .integer(change.attempted ? 1 : 0), .real(change.queuedAt.timeIntervalSince1970),
            ]
        )
    }

    private func dropPendingChange(for id: Game.ID) throws {
        try db.run("DELETE FROM pending_changes WHERE game_id = ?", [.text(id)])
    }
}

// MARK: - Location

extension GameStore {
    /// The app's database in Application Support, excluded from backups. If the file can't be
    /// used it is recreated; as a last resort the data is kept in memory for this launch.
    static func makeDefault(erasingExisting: Bool = false) -> GameStore {
        do {
            let url = try databaseURL()
            if erasingExisting {
                removeDatabase(at: url)
            }
            do {
                return try GameStore(url: url)
            } catch {
                debugLog("The local database can't be opened, recreating it: \(error)")
                removeDatabase(at: url)
                return try GameStore(url: url)
            }
        } catch {
            debugLog("The local database is unavailable, keeping data in memory: \(error)")
            guard let store = try? GameStore(url: nil) else {
                preconditionFailure("SQLite can't open an in-memory database.")
            }
            return store
        }
    }

    private static func databaseURL() throws -> URL {
        let fileManager = FileManager.default
        var directory = try fileManager
            .url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            .appending(path: "OfflineData", directoryHint: .isDirectory)
        let protection: [FileAttributeKey: Any] = [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication]
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true, attributes: protection)
        try fileManager.setAttributes(protection, ofItemAtPath: directory.path(percentEncoded: false))
        // The collection is restored from the server after a device restore; don't back it up.
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try directory.setResourceValues(values)
        return directory.appending(path: "games.sqlite")
    }

    private static func removeDatabase(at url: URL) {
        for suffix in ["", "-wal", "-shm"] {
            try? FileManager.default.removeItem(at: URL(filePath: url.path(percentEncoded: false) + suffix))
        }
    }
}
