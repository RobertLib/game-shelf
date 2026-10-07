import Foundation
import SQLite3

struct SQLiteError: Error, CustomStringConvertible {
    let code: Int32
    let message: String

    var description: String { "SQLite error \(code): \(message)" }
}

/// A value bound to or read from a statement.
enum SQLiteValue: Sendable {
    case null
    case integer(Int64)
    case real(Double)
    case text(String)
    case blob(Data)
}

/// Minimal wrapper around one SQLite connection: prepared (and cached) statements,
/// parameter binding and transactions.
///
/// Not thread-safe; it is owned and serialized by one actor (``GameStore``).
final class SQLiteConnection {
    private var handle: OpaquePointer?
    private var statements: [String: SQLiteStatement] = [:]

    /// Opens (or creates) the database at `url`, or a private in-memory database when `url` is `nil`.
    init(url: URL?) throws {
        var flags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_NOMUTEX
        if url != nil {
            // Readable in the background after the first unlock, like the session in the Keychain.
            flags |= SQLITE_OPEN_FILEPROTECTION_COMPLETEUNTILFIRSTUSERAUTHENTICATION
        }
        let path = url?.path(percentEncoded: false) ?? ":memory:"
        let status = sqlite3_open_v2(path, &handle, flags, nil)
        guard status == SQLITE_OK else {
            let error = SQLiteError(code: status, message: handle.map { String(cString: sqlite3_errmsg($0)) } ?? "cannot open")
            sqlite3_close_v2(handle)
            handle = nil
            throw error
        }
        sqlite3_busy_timeout(handle, 2_000)
    }

    deinit {
        statements.removeAll()
        sqlite3_close_v2(handle)
    }

    /// Runs one or more statements without parameters (schema changes, pragmas).
    func execute(_ sql: String) throws {
        let status = sqlite3_exec(handle, sql, nil, nil, nil)
        guard status == SQLITE_OK else { throw lastError(status) }
    }

    /// Runs a statement that returns no rows.
    func run(_ sql: String, _ values: [SQLiteValue] = []) throws {
        let statement = try prepared(sql)
        defer { statement.reset() }
        try statement.bind(values)
        while try statement.step() {}
    }

    /// Runs a query and maps every row.
    func query<Row>(_ sql: String, _ values: [SQLiteValue] = [], row map: (SQLiteRow) throws -> Row) throws -> [Row] {
        let statement = try prepared(sql)
        defer { statement.reset() }
        try statement.bind(values)
        var rows: [Row] = []
        while try statement.step() {
            rows.append(try map(SQLiteRow(statement: statement)))
        }
        return rows
    }

    /// Runs `body` in one transaction: committed when it returns, rolled back when it throws.
    func transaction<Result>(_ body: () throws -> Result) throws -> Result {
        try execute("BEGIN IMMEDIATE")
        do {
            let result = try body()
            try execute("COMMIT")
            return result
        } catch {
            try? execute("ROLLBACK")
            throw error
        }
    }

    private func prepared(_ sql: String) throws -> SQLiteStatement {
        if let statement = statements[sql] {
            return statement
        }
        var pointer: OpaquePointer?
        let status = sqlite3_prepare_v3(handle, sql, -1, UInt32(SQLITE_PREPARE_PERSISTENT), &pointer, nil)
        guard status == SQLITE_OK, let pointer else { throw lastError(status) }
        let statement = SQLiteStatement(handle: pointer, connection: self)
        statements[sql] = statement
        return statement
    }

    fileprivate func lastError(_ status: Int32) -> SQLiteError {
        SQLiteError(code: status, message: handle.map { String(cString: sqlite3_errmsg($0)) } ?? "no connection")
    }
}

/// A prepared statement, reused through the connection's cache.
final class SQLiteStatement {
    fileprivate let handle: OpaquePointer
    private unowned let connection: SQLiteConnection

    /// Makes SQLite copy bound text and blobs, so Swift buffers may be released right after binding.
    private static var transient: sqlite3_destructor_type {
        unsafeBitCast(-1, to: sqlite3_destructor_type.self)
    }

    fileprivate init(handle: OpaquePointer, connection: SQLiteConnection) {
        self.handle = handle
        self.connection = connection
    }

    deinit {
        sqlite3_finalize(handle)
    }

    fileprivate func bind(_ values: [SQLiteValue]) throws {
        for (offset, value) in values.enumerated() {
            let index = Int32(offset + 1)
            let status: Int32 = switch value {
            case .null:
                sqlite3_bind_null(handle, index)
            case .integer(let integer):
                sqlite3_bind_int64(handle, index, integer)
            case .real(let real):
                sqlite3_bind_double(handle, index, real)
            case .text(let text):
                sqlite3_bind_text(handle, index, text, -1, Self.transient)
            case .blob(let data):
                data.withUnsafeBytes { buffer in
                    sqlite3_bind_blob(handle, index, buffer.baseAddress, Int32(buffer.count), Self.transient)
                }
            }
            guard status == SQLITE_OK else { throw connection.lastError(status) }
        }
    }

    /// Returns `true` while there is a row to read.
    fileprivate func step() throws -> Bool {
        switch sqlite3_step(handle) {
        case SQLITE_ROW: return true
        case SQLITE_DONE: return false
        case let status: throw connection.lastError(status)
        }
    }

    fileprivate func reset() {
        sqlite3_reset(handle)
        sqlite3_clear_bindings(handle)
    }
}

/// Column accessors of the current row.
struct SQLiteRow {
    fileprivate let statement: SQLiteStatement

    func isNull(_ column: Int32) -> Bool {
        sqlite3_column_type(statement.handle, column) == SQLITE_NULL
    }

    func int(_ column: Int32) -> Int {
        Int(sqlite3_column_int64(statement.handle, column))
    }

    func double(_ column: Int32) -> Double {
        sqlite3_column_double(statement.handle, column)
    }

    func text(_ column: Int32) -> String? {
        guard let pointer = sqlite3_column_text(statement.handle, column) else { return nil }
        return String(cString: pointer)
    }

    func data(_ column: Int32) -> Data {
        let count = Int(sqlite3_column_bytes(statement.handle, column))
        guard count > 0, let pointer = sqlite3_column_blob(statement.handle, column) else { return Data() }
        return Data(bytes: pointer, count: count)
    }
}
