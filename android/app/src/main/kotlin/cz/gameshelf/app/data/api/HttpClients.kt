package cz.gameshelf.app.data.api

import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object HttpClients {

    /** Shared base: connection pool, dispatcher and timeouts. Also used by Coil for cover images. */
    fun base(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Client for the public auth endpoints, the token refresh included. It has its own dispatcher:
     * [TokenAuthenticator] blocks a call of the authenticated client (on the [base] dispatcher) while it
     * refreshes, so the refresh must never wait for a dispatcher slot that such blocked calls hold.
     */
    fun authClient(base: OkHttpClient, debugLogging: Boolean): OkHttpClient =
        base.newBuilder()
            .dispatcher(Dispatcher())
            .addDebugLogging(debugLogging)
            .build()

    /**
     * Logs requests and responses (debug builds). Bodies are logged, except those of the `auth/…` endpoints,
     * which hold passwords and tokens; the `Authorization` header is always redacted.
     */
    fun OkHttpClient.Builder.addDebugLogging(
        enabled: Boolean,
        logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
    ): OkHttpClient.Builder = apply {
        if (enabled) addInterceptor(DebugLoggingInterceptor(logger))
    }

    fun retrofit(baseUrl: String, client: OkHttpClient, json: Json = ApiJson): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    private class DebugLoggingInterceptor(logger: HttpLoggingInterceptor.Logger) : Interceptor {
        private val withBodies = logging(logger, HttpLoggingInterceptor.Level.BODY)
        private val headersOnly = logging(logger, HttpLoggingInterceptor.Level.HEADERS)

        override fun intercept(chain: Interceptor.Chain): Response {
            val hasSecrets = AUTH_PATH_SEGMENT in chain.request().url.pathSegments
            return (if (hasSecrets) headersOnly else withBodies).intercept(chain)
        }

        private fun logging(logger: HttpLoggingInterceptor.Logger, level: HttpLoggingInterceptor.Level) =
            HttpLoggingInterceptor(logger).apply {
                this.level = level
                redactHeader(AUTHORIZATION)
            }
    }

    private const val AUTH_PATH_SEGMENT = "auth"
}
