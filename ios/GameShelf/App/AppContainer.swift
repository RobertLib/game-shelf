import Foundation

/// Composition root: builds the object graph once at launch.
@MainActor
final class AppContainer {
    let session: SessionStore
    let games: any GameService

    init(session: SessionStore, games: any GameService) {
        self.session = session
        self.games = games
    }

    static func live() -> AppContainer {
        let storage = KeychainSessionStorage()
        clearKeychainAfterReinstall(storage)
        #if DEBUG
        if DebugLaunchOptions.current.resetSession {
            storage.clear()
        }
        #endif

        let restored = storage.load()
        let tokens = TokenManager(storage: storage, session: restored)
        let api = APIClient(baseURL: AppConfiguration.apiBaseURL, tokens: tokens)
        return AppContainer(
            session: SessionStore(auth: RemoteAuthService(api: api), tokens: tokens, restoredSession: restored),
            games: RemoteGameService(api: api)
        )
    }

    /// Keychain items survive app deletion; a fresh install must not resurrect an old session.
    private static func clearKeychainAfterReinstall(_ storage: some SessionStorage) {
        let key = "hasLaunchedBefore"
        guard !UserDefaults.standard.bool(forKey: key) else { return }
        storage.clear()
        UserDefaults.standard.set(true, forKey: key)
    }
}
