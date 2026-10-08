import Foundation

/// Composition root: builds the object graph once at launch.
@MainActor
final class AppContainer {
    let session: SessionStore
    /// The offline collection (``SyncEngine/repository``) and its sync.
    let sync: SyncEngine
    let barcodeLookup: any BarcodeLookupService
    let gameSearch: any GameSearchService

    init(session: SessionStore, sync: SyncEngine, barcodeLookup: any BarcodeLookupService, gameSearch: any GameSearchService) {
        self.session = session
        self.sync = sync
        self.barcodeLookup = barcodeLookup
        self.gameSearch = gameSearch
    }

    static func live() -> AppContainer {
        let storage = KeychainSessionStorage()
        clearKeychainAfterReinstall(storage)
        #if DEBUG
        let erasesLocalData = DebugLaunchOptions.current.resetSession
        if erasesLocalData {
            storage.clear()
        }
        #else
        let erasesLocalData = false
        #endif

        let restored = storage.load()
        let tokens = TokenManager(storage: storage, session: restored)
        let api = APIClient(baseURL: AppConfiguration.apiBaseURL, tokens: tokens)
        let repository = GameRepository(store: GameStore.makeDefault(erasingExisting: erasesLocalData), status: SyncStatus())
        let sync = SyncEngine(repository: repository, api: RemoteGameAPI(api: api))
        return AppContainer(
            session: SessionStore(
                auth: RemoteAuthService(api: api),
                tokens: tokens,
                restoredSession: restored,
                userData: sync
            ),
            sync: sync,
            barcodeLookup: RemoteBarcodeLookupService(api: api),
            gameSearch: RemoteGameSearchService(api: api)
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
