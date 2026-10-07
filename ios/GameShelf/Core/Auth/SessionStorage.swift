import Foundation

/// Persistence of the signed-in session.
protocol SessionStorage: Sendable {
    func load() -> StoredSession?
    func save(_ session: StoredSession)
    func clear()
}

/// Stores the session as one Keychain item available after first unlock, on this device only.
struct KeychainSessionStorage: SessionStorage {
    private static let account = "session"

    let keychain: KeychainStore

    init(keychain: KeychainStore = KeychainStore(service: "cz.gameshelf.app.session")) {
        self.keychain = keychain
    }

    func load() -> StoredSession? {
        do {
            guard let data = try keychain.data(forAccount: Self.account) else { return nil }
            return try JSONDecoder.api().decode(StoredSession.self, from: data)
        } catch {
            // An unreadable item is treated as signed out; the next sign-in overwrites it.
            debugLog("Failed to load the session: \(error)")
            return nil
        }
    }

    func save(_ session: StoredSession) {
        do {
            try keychain.set(JSONEncoder.api().encode(session), forAccount: Self.account)
        } catch {
            debugLog("Failed to save the session: \(error)")
        }
    }

    func clear() {
        do {
            try keychain.removeValue(forAccount: Self.account)
        } catch {
            debugLog("Failed to clear the session: \(error)")
        }
    }
}

/// Non-persistent storage for previews and tests.
final class InMemorySessionStorage: SessionStorage, @unchecked Sendable {
    private let lock = NSLock()
    private var session: StoredSession?

    init(_ session: StoredSession? = nil) {
        self.session = session
    }

    func load() -> StoredSession? { lock.withLock { session } }
    func save(_ session: StoredSession) { lock.withLock { self.session = session } }
    func clear() { lock.withLock { session = nil } }
}

func debugLog(_ message: @autoclosure () -> String) {
    #if DEBUG
    print("[GameShelf] \(message())")
    #endif
}
