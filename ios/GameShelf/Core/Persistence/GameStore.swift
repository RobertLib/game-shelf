import Foundation

/// The device's copy of the signed-in user's collection: games, pending changes and sync state
/// in SQLite (docs/offline-sync.md, "Local data").
///
/// Every operation that changes more than one thing – a local edit and its pending change, a
/// pulled page and its cursor, the result of a push – runs in one transaction, and every write
/// checks the caller's ``StoreAccess`` inside it. The games are also kept in memory, so the UI
/// gets a complete ``StoreSnapshot`` after each write without reading the database again.
actor GameStore {
    /// The database file; `nil` keeps everything in memory (tests, previews).
    private let url: URL?
    /// `CFBundleShortVersionString (CFBundleVersion)` of the running app, stored with the cursor.
    private let appVersion: String
    private var connection: SQLiteConnection?
    private let encoder = JSONEncoder.api()
    private let decoder = JSONDecoder.api()

    private var access: StoreAccess?
    private var generation = 0
    private var version = 0
    private var games: [Game.ID: Game] = [:]
    private var pendingCount = 0
    private var lastSyncedAt: Date?
    private var hasUndoneRejectedChanges = false

    /// Opens (or creates) the database at `url` now; `nil` keeps everything in memory (tests, previews).
    init(url: URL?, appVersion: String = AppConfiguration.appVersion) throws {
        self.url = url
        self.appVersion = appVersion
        connection = try Self.open(url)
    }

    /// The database file at `url`, opened by the first activation. A file that can't be opened then
    /// (e.g. while the device is still locked after a restart) is kept, and the next activation
    /// tries again.
    init(openingOnFirstUse url: URL, appVersion: String = AppConfiguration.appVersion) {
        self.url = url
        self.appVersion = appVersion
    }

    /// The open connection; opened (and migrated) on first use.
    private var db: SQLiteConnection {
        get throws {
            if let connection {
                return connection
            }
            let connection = try Self.open(url)
            self.connection = connection
            return connection
        }
    }

    private static func open(_ url: URL?) throws -> SQLiteConnection {
        if let url {
            try prepareDirectory(of: url)
        }
        let db = try SQLiteConnection(url: url)
        try migrate(db)
        return db
    }

    /// Schema versions (`PRAGMA user_version`): migration `n` brings the schema from version `n` to
    /// `n + 1`. Each runs in its own transaction; new versions only append to this list.
    private static let migrations = [
        // 1: games, pending changes and the sync state.
        """
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
        """,
        // 2: the version of the app that stored the cursor (an update pulls the whole collection
        // again), and undone rejected changes the user hasn't been told about yet.
        """
        ALTER TABLE sync_state ADD COLUMN app_version TEXT;
        ALTER TABLE sync_state ADD COLUMN has_undone_rejected_changes INTEGER NOT NULL DEFAULT 0;
        """,
    ]

    private static func migrate(_ db: SQLiteConnection) throws {
        try db.execute("PRAGMA journal_mode = WAL; PRAGMA synchronous = NORMAL;")
        let schemaVersion = try db.query("PRAGMA user_version") { $0.int(0) }.first ?? 0
        for (index, migration) in migrations.enumerated() where index >= schemaVersion {
            try db.transaction {
                try db.execute(migration)
                try db.execute("PRAGMA user_version = \(index + 1)")
            }
        }
    }

    var snapshot: StoreSnapshot {
        StoreSnapshot(
            version: version, games: games, pendingCount: pendingCount, lastSyncedAt: lastSyncedAt,
            hasUndoneRejectedChanges: hasUndoneRejectedChanges
        )
    }

    // MARK: - Owner

    /// Makes the data available for `ownerID`. Data of any other user is wiped first.
    ///
    /// A damaged database file (`SQLITE_CORRUPT`, `SQLITE_NOTADB`) is replaced by an empty one; any
    /// other failure is thrown and keeps the file – with the unsynced changes in it – for the next
    /// attempt.
    func activate(ownerID: User.ID) throws -> (StoreAccess, StoreSnapshot) {
        let cache: Cache
        do {
            cache = try loadReplacingDamagedFile(ownerID)
        } catch {
            // The next attempt opens the file again.
            connection = nil
            throw error
        }
        games = cache.games
        pendingCount = cache.pendingCount
        lastSyncedAt = cache.lastSyncedAt
        hasUndoneRejectedChanges = cache.hasUndoneRejectedChanges
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
        hasUndoneRejectedChanges = false
        version += 1
    }

    private func deleteAllData() throws {
        try db.run("DELETE FROM games")
        try db.run("DELETE FROM pending_changes")
        try db.run("""
        UPDATE sync_state SET owner_user_id = NULL, cursor = NULL, last_synced_at = NULL, app_version = NULL,
            has_undone_rejected_changes = 0
        WHERE id = 1
        """)
    }

    /// What ``activate(ownerID:)`` keeps in memory.
    private struct Cache {
        var games: [Game.ID: Game]
        var pendingCount: Int
        var lastSyncedAt: Date?
        var hasUndoneRejectedChanges: Bool
    }

    private func loadReplacingDamagedFile(_ ownerID: User.ID) throws -> Cache {
        do {
            return try load(ownerID)
        } catch let error as SQLiteError where error.isCorruption {
            guard let url else { throw error }
            debugLog("The local database is damaged, recreating it: \(error)")
            connection = nil
            Self.removeDatabase(at: url)
            return try load(ownerID)
        }
    }

    /// Prepares the stored data for `ownerID` in one transaction and reads it.
    private func load(_ ownerID: User.ID) throws -> Cache {
        try db.transaction {
            let state = try db.query("SELECT owner_user_id, app_version FROM sync_state WHERE id = 1") {
                (owner: $0.text(0), appVersion: $0.text(1))
            }.first
            if state?.owner != ownerID {
                try deleteAllData()
                try db.run("UPDATE sync_state SET owner_user_id = ? WHERE id = 1", [.text(ownerID)])
            } else if state?.appVersion != appVersion {
                // After an app update the whole collection is pulled again: the previous version stored
                // values it didn't know as its fallback (docs/offline-sync.md, "After an app update").
                try forgetCursor()
            }
            return try readCache()
        }
    }

    /// Reads the games and the sync state.
    ///
    /// A stored game that can't be decoded is never dropped together with its pending change: it stays
    /// in the database, and pushing its change fails as a temporary local error until an app version
    /// that can read it is installed. Unreadable games without a pending change are on the server; they
    /// are removed and the whole collection is pulled again.
    private func readCache() throws -> Cache {
        let rows = try db.query("SELECT id, data FROM games") { row in (row.text(0) ?? "", row.data(1)) }
        let pendingIDs = Set(try db.query("SELECT game_id FROM pending_changes") { $0.text(0) ?? "" })
        var loaded: [Game.ID: Game] = [:]
        loaded.reserveCapacity(rows.count)
        var replaceable: [Game.ID] = []
        for (id, data) in rows {
            do {
                loaded[id] = try decoder.decode(Game.self, from: data)
            } catch {
                let isPending = pendingIDs.contains(id)
                debugLog("Stored game \(id) can't be read\(isPending ? ", kept with its pending change" : ""): \(error)")
                if !isPending {
                    replaceable.append(id)
                }
            }
        }
        if !replaceable.isEmpty {
            for id in replaceable {
                try db.run("DELETE FROM games WHERE id = ?", [.text(id)])
            }
            try forgetCursor()
        }
        let state = try db.query("SELECT last_synced_at, has_undone_rejected_changes FROM sync_state WHERE id = 1") { row in
            (lastSyncedAt: row.isNull(0) ? nil : Date(timeIntervalSince1970: row.double(0)), hasUndone: row.int(1) != 0)
        }.first
        return Cache(
            games: loaded,
            pendingCount: try countPendingChanges(),
            lastSyncedAt: state?.lastSyncedAt,
            hasUndoneRejectedChanges: state?.hasUndone ?? false
        )
    }

    /// The next pull starts from the beginning of the change feed.
    private func forgetCursor() throws {
        try db.run("UPDATE sync_state SET cursor = NULL WHERE id = 1")
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
                    // Only the fields edited after the first attempt (also while this request was in
                    // flight) never reached the server – never all fields, which would overwrite what
                    // other devices changed since.
                    if current.fields.isEmpty {
                        try dropPendingChange(for: id)
                        try put(server, &changes)
                    } else {
                        current.kind = .update
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
                // Shown to the user until they have seen it, also after a restart.
                try db.run("UPDATE sync_state SET has_undone_rejected_changes = 1 WHERE id = 1")
                changes.hasUndoneRejectedChanges = true
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

    /// Applies one page of the change feed together with its cursor and the version of the app that
    /// stored it; the last page also records the time of the completed sync.
    ///
    /// `startingOver`: the first page of a full pull after `410 SYNC_RESET_REQUIRED`. Every game
    /// without a pending change is dropped in the same transaction, so until the first page has
    /// arrived, the collection stays as it was.
    func applyChanges(_ page: GameChanges, at now: Date, startingOver: Bool = false, access: StoreAccess) throws -> StoreSnapshot {
        try write(access) { changes in
            let pending = try pendingChangesByGame()
            if startingOver {
                for id in games.keys where pending[id] == nil {
                    try remove(id, &changes)
                }
            }
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
            try db.run("UPDATE sync_state SET cursor = ?, app_version = ? WHERE id = 1", [.text(page.cursor), .text(appVersion)])
            if !page.hasMore {
                try db.run("UPDATE sync_state SET last_synced_at = ? WHERE id = 1", [.real(now.timeIntervalSince1970)])
                changes.lastSyncedAt = now
            }
        }.snapshot
    }

    /// The user has been told that rejected changes were undone.
    func acknowledgeUndoneRejectedChanges(access: StoreAccess) throws -> StoreSnapshot {
        try write(access) { changes in
            try db.run("UPDATE sync_state SET has_undone_rejected_changes = 0 WHERE id = 1")
            changes.hasUndoneRejectedChanges = false
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
        var hasUndoneRejectedChanges: Bool?
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
        if let hasUndone = changes.hasUndoneRejectedChanges {
            hasUndoneRejectedChanges = hasUndone
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
    /// The app's database in Application Support, excluded from backups, opened by the first activation.
    static func makeDefault(erasingExisting: Bool = false) -> GameStore {
        let url = URL.applicationSupportDirectory.appending(path: "OfflineData/games.sqlite")
        if erasingExisting {
            removeDatabase(at: url)
        }
        return GameStore(openingOnFirstUse: url)
    }

    /// Creates the database's directory: readable after the first unlock (like the session in the
    /// Keychain) and not backed up – the collection is restored from the server after a device restore.
    private static func prepareDirectory(of url: URL) throws {
        let fileManager = FileManager.default
        var directory = url.deletingLastPathComponent()
        let protection: [FileAttributeKey: Any] = [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication]
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true, attributes: protection)
        try fileManager.setAttributes(protection, ofItemAtPath: directory.path(percentEncoded: false))
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try directory.setResourceValues(values)
    }

    private static func removeDatabase(at url: URL) {
        for suffix in ["", "-wal", "-shm"] {
            try? FileManager.default.removeItem(at: URL(filePath: url.path(percentEncoded: false) + suffix))
        }
    }
}
