package cz.gameshelf.app.di

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStoreFile
import cz.gameshelf.app.BuildConfig
import cz.gameshelf.app.data.api.AccountApi
import cz.gameshelf.app.data.api.ApiJson
import cz.gameshelf.app.data.api.AuthApi
import cz.gameshelf.app.data.api.AuthInterceptor
import cz.gameshelf.app.data.api.GamesApi
import cz.gameshelf.app.data.api.HttpClients
import cz.gameshelf.app.data.api.HttpClients.addDebugLogging
import cz.gameshelf.app.data.api.LookupApi
import cz.gameshelf.app.data.api.TokenAuthenticator
import cz.gameshelf.app.data.api.dto.RefreshTokenRequest
import cz.gameshelf.app.data.auth.AuthRepository
import cz.gameshelf.app.data.auth.EncryptedSessionStore
import cz.gameshelf.app.data.auth.KeystorePayloadCipher
import cz.gameshelf.app.data.auth.SessionManager
import cz.gameshelf.app.data.auth.SessionState
import cz.gameshelf.app.data.auth.sessionDataStore
import cz.gameshelf.app.data.games.GamesRepository
import cz.gameshelf.app.data.games.OfflineGamesRepository
import cz.gameshelf.app.data.local.GameShelfDatabase
import cz.gameshelf.app.data.local.LocalGameStore
import cz.gameshelf.app.data.lookup.BarcodeLookupRepository
import cz.gameshelf.app.data.lookup.GameSearchRepository
import cz.gameshelf.app.data.lookup.RemoteBarcodeLookupRepository
import cz.gameshelf.app.data.lookup.RemoteGameSearchRepository
import cz.gameshelf.app.data.sync.ForegroundMonitor
import cz.gameshelf.app.data.sync.NetworkMonitor
import cz.gameshelf.app.data.sync.SyncController
import cz.gameshelf.app.data.sync.SyncEngine
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import retrofit2.create

private const val TAG = "AppContainer"

/**
 * The scope of work that outlives every screen. An exception escaping one of its coroutines is logged
 * instead of crashing the app; the others keep running.
 */
internal fun createAppScope(): CoroutineScope {
    val logUncaught = CoroutineExceptionHandler { _, e -> Log.e(TAG, "Uncaught exception in the app scope", e) }
    return CoroutineScope(SupervisorJob() + Dispatchers.Default + logUncaught)
}

/** Manual dependency graph, created once by the Application (on the main thread). */
class AppContainer(context: Context) {

    private val appScope = createAppScope()

    val sessionManager = SessionManager(
        store = EncryptedSessionStore(
            dataStore = sessionDataStore { context.preferencesDataStoreFile("session") },
            cipher = KeystorePayloadCipher(),
            json = ApiJson,
        ),
        scope = appScope,
    )

    /** Shared connection pool and dispatcher; Coil uses it as is, without API auth or logging. */
    val imageHttpClient: OkHttpClient = HttpClients.base()

    private val publicApiClient: OkHttpClient = HttpClients.authClient(imageHttpClient, BuildConfig.DEBUG)

    private val authApi: AuthApi =
        HttpClients.retrofit(BuildConfig.API_BASE_URL, publicApiClient).create()

    private val authenticatedApiClient: OkHttpClient = imageHttpClient.newBuilder()
        .addInterceptor(AuthInterceptor { sessionManager.accessToken })
        .authenticator(
            TokenAuthenticator(sessionManager) { refreshToken ->
                authApi.refresh(RefreshTokenRequest(refreshToken))
            },
        )
        .addDebugLogging(BuildConfig.DEBUG)
        .build()

    private val authenticatedRetrofit = HttpClients.retrofit(BuildConfig.API_BASE_URL, authenticatedApiClient)

    private val localGameStore = LocalGameStore(GameShelfDatabase.create(context))

    private val syncEngine = SyncEngine(
        api = authenticatedRetrofit.create<GamesApi>(),
        store = localGameStore,
        signedInUserId = sessionManager.state
            .map { (it as? SessionState.SignedIn)?.user?.id }
            .stateIn(appScope, SharingStarted.Eagerly, null),
        isConnected = NetworkMonitor(context).isConnected,
        isForeground = ForegroundMonitor().isForeground,
        scope = appScope,
        appVersion = BuildConfig.VERSION_CODE,
    )

    val syncController: SyncController = syncEngine

    val authRepository = AuthRepository(
        authApi = authApi,
        accountApi = authenticatedRetrofit.create<AccountApi>(),
        sessionManager = sessionManager,
        appScope = appScope,
        prepareUserData = syncEngine::prepareUserData,
        clearUserData = syncEngine::clearUserData,
    )

    val gamesRepository: GamesRepository = OfflineGamesRepository(
        store = localGameStore,
        requestSync = syncEngine::requestSync,
        scope = appScope,
    )

    private val lookupApi: LookupApi = authenticatedRetrofit.create()

    val barcodeLookupRepository: BarcodeLookupRepository = RemoteBarcodeLookupRepository(lookupApi)

    val gameSearchRepository: GameSearchRepository = RemoteGameSearchRepository(lookupApi)

    init {
        syncEngine.start()
    }
}
