package cz.gameshelf.app.di

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStoreFile
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
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
import cz.gameshelf.app.data.auth.PendingWipe
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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.create
import java.io.File

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
class AppContainer(private val context: Context) {

    private val appScope = createAppScope()

    /** Exists while a sign-out's wipe is not done (offline-sync.md, "Local data"); see [PendingWipe]. */
    private val pendingWipe = PendingWipe(File(context.noBackupFilesDir, PENDING_WIPE_FILE))

    private val sessionFile: File = context.preferencesDataStoreFile(SESSION_FILE)

    /** Coil's disk cache of cover images; wiped with the local data. */
    private val imageCacheDirectory = File(context.cacheDir, IMAGE_CACHE_DIRECTORY)

    init {
        // Before the stored session is read and before the database and the image cache are opened.
        finishPendingWipe()
    }

    val sessionManager = SessionManager(
        store = EncryptedSessionStore(
            dataStore = sessionDataStore { sessionFile },
            cipher = KeystorePayloadCipher(),
            json = ApiJson,
        ),
        scope = appScope,
    )

    /** Shared connection pool and dispatcher; Coil uses it as is, without API auth or logging. */
    val imageHttpClient: OkHttpClient = HttpClients.base()

    /** Loads the cover images (Coil's singleton, see `GameShelfApplication`); wiped with the local data. */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient })) }
            .diskCache { DiskCache.Builder().directory(imageCacheDirectory).build() }
            .crossfade(true)
            .apply { if (BuildConfig.DEBUG) logger(DebugLogger()) }
            .build()
    }

    private val publicApiClient: OkHttpClient = HttpClients.authClient(imageHttpClient, BuildConfig.DEBUG)

    private val authApi: AuthApi =
        HttpClients.retrofit(BuildConfig.API_BASE_URL, publicApiClient).create()

    private val authenticatedApiClient: OkHttpClient = imageHttpClient.newBuilder()
        .addInterceptor(AuthInterceptor { sessionManager.accessToken })
        .authenticator(
            TokenAuthenticator(
                session = sessionManager,
                onAccountDeleted = { accessToken -> authRepository.onAccountDeleted(accessToken) },
                refreshTokens = { refreshToken -> authApi.refresh(RefreshTokenRequest(refreshToken)) },
            ),
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
        pendingWipe = pendingWipe,
        prepareUserData = syncEngine::prepareUserData,
        clearUserData = {
            syncEngine.clearUserData()
            clearImageCaches()
        },
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

    /**
     * A wipe a sign-out couldn't finish – it failed, or the app was killed half-way – is finished before the session is
     * restored or anything is shown (offline-sync.md, "Local data"): the stored session, the database and the cover
     * cache are deleted. What can't be deleted now is tried again at the next start.
     */
    private fun finishPendingWipe() {
        pendingWipe.finishAtStart(
            { sessionFile.delete() || !sessionFile.exists() },
            {
                context.deleteDatabase(GameShelfDatabase.FILE_NAME)
                !context.getDatabasePath(GameShelfDatabase.FILE_NAME).exists()
            },
            { imageCacheDirectory.deleteRecursively() },
        )
    }

    /** The cover images, from memory and from the disk (mobile-spec.md, "Profile & settings"). */
    private suspend fun clearImageCaches() = withContext(Dispatchers.IO) {
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
    }

    private companion object {
        const val PENDING_WIPE_FILE = "pending-wipe"
        const val SESSION_FILE = "session"

        /** Where Coil keeps its disk cache by default. */
        const val IMAGE_CACHE_DIRECTORY = "coil3_disk_cache"
    }
}
