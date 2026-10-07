package cz.gameshelf.app.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import cz.gameshelf.app.BuildConfig
import cz.gameshelf.app.data.api.AccountApi
import cz.gameshelf.app.data.api.ApiJson
import cz.gameshelf.app.data.api.AuthApi
import cz.gameshelf.app.data.api.AuthInterceptor
import cz.gameshelf.app.data.api.GamesApi
import cz.gameshelf.app.data.api.HttpClients
import cz.gameshelf.app.data.api.HttpClients.addDebugLogging
import cz.gameshelf.app.data.api.TokenAuthenticator
import cz.gameshelf.app.data.api.dto.RefreshTokenRequest
import cz.gameshelf.app.data.auth.AuthRepository
import cz.gameshelf.app.data.auth.EncryptedSessionStore
import cz.gameshelf.app.data.auth.KeystorePayloadCipher
import cz.gameshelf.app.data.auth.SessionManager
import cz.gameshelf.app.data.games.GamesRepository
import cz.gameshelf.app.data.games.NetworkGamesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import retrofit2.create

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/** Manual dependency graph, created once by the Application. */
class AppContainer(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionManager = SessionManager(
        store = EncryptedSessionStore(context.sessionDataStore, KeystorePayloadCipher(), ApiJson),
        scope = appScope,
    )

    /** Shared connection pool; Coil uses it as is, without API auth or logging. */
    val imageHttpClient: OkHttpClient = HttpClients.base()

    private val publicApiClient: OkHttpClient = imageHttpClient.newBuilder()
        .addDebugLogging(BuildConfig.DEBUG)
        .build()

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

    val authRepository = AuthRepository(
        authApi = authApi,
        accountApi = authenticatedRetrofit.create<AccountApi>(),
        sessionManager = sessionManager,
        appScope = appScope,
    )

    val gamesRepository: GamesRepository = NetworkGamesRepository(authenticatedRetrofit.create<GamesApi>())
}
