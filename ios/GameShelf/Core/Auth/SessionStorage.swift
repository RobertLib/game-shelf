import Foundation
import Security

/// Persistence of the signed-in session.
protocol SessionStorage: Sendable {
    /// The stored session, `nil` when there is none. Throws while it can't be read yet – before the
    /// device is first unlocked after a restart; then it has to be read again later.
    func load() throws -> StoredSession?
    /// Throws when the session couldn't be stored.
    func save(_ session: StoredSession) throws
    /// Removes the stored session. Throws when it couldn't be removed.
    func clear() throws
}

/// Stores the session as one Keychain item available after first unlock, on this device only.
struct KeychainSessionStorage: SessionStorage {
    private static let account = "session"
    /// Set in `UserDefaults` (which an app's deletion removes) once this installation stored a session.
    private static let installationKey = "hasLaunchedBefore"

    let keychain: KeychainStore

    init(keychain: KeychainStore = KeychainStore(service: "cz.gameshelf.app.session")) {
        self.keychain = keychain
    }

    func load() throws -> StoredSession? {
        let data: Data
        do {
            guard let stored = try keychain.data(forAccount: Self.account) else { return nil }
            data = stored
        } catch let error as KeychainStore.UnexpectedStatus where error.status == errSecInteractionNotAllowed {
            // The device hasn't been unlocked since it restarted (e.g. iOS launched the app in advance):
            // the session is still there, it just can't be read yet.
            throw error
        } catch {
            // An unreadable item is treated as signed out; the next sign-in overwrites it.
            debugLog("Failed to load the session: \(error)")
            return nil
        }
        // Keychain items survive the app's deletion: an item this installation didn't store must not sign
        // a fresh install in. Read only once the item could be, the flag is readable then too.
        guard UserDefaults.standard.bool(forKey: Self.installationKey) else {
            do {
                try clear()
            } catch {
                debugLog("Failed to clear the session of an earlier installation: \(error)")
            }
            return nil
        }
        do {
            return try JSONDecoder.api().decode(StoredSession.self, from: data)
        } catch {
            debugLog("Failed to decode the session: \(error)")
            return nil
        }
    }

    func save(_ session: StoredSession) throws {
        UserDefaults.standard.set(true, forKey: Self.installationKey)
        try keychain.set(JSONEncoder.api().encode(session), forAccount: Self.account)
    }

    func clear() throws {
        try keychain.removeValue(forAccount: Self.account)
    }
}

/// Non-persistent storage for previews and tests.
final class InMemorySessionStorage: SessionStorage, @unchecked Sendable {
    private let lock = NSLock()
    private var session: StoredSession?
    private var savingFails = false
    private var clearingFails = false

    init(_ session: StoredSession? = nil) {
        self.session = session
    }

    /// Makes ``save(_:)`` fail, like a Keychain that can't be written.
    func setSavingFails(_ fails: Bool) { lock.withLock { savingFails = fails } }

    /// Makes ``clear()`` fail and keep the session, like a Keychain whose item can't be deleted.
    func setClearingFails(_ fails: Bool) { lock.withLock { clearingFails = fails } }

    func load() -> StoredSession? { lock.withLock { session } }

    func save(_ session: StoredSession) throws {
        try lock.withLock {
            guard !savingFails else { throw CocoaError(.fileWriteUnknown) }
            self.session = session
        }
    }

    func clear() throws {
        try lock.withLock {
            guard !clearingFails else { throw CocoaError(.fileWriteUnknown) }
            session = nil
        }
    }
}

func debugLog(_ message: @autoclosure () -> String) {
    #if DEBUG
    print("[GameShelf] \(message())")
    #endif
}
