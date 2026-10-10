package cz.gameshelf.app.data.api

import android.util.Log
import cz.gameshelf.app.data.api.dto.AuthResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import java.io.IOException
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException

/** Token state the [TokenAuthenticator] reads and updates. */
interface SessionTokens {
    val accessToken: String?
    val refreshToken: String?

    /**
     * Stores the pair obtained for [refreshToken], but only while that is still the session's refresh token:
     * a refresh that completes after a sign-out (or a sign-in, maybe of another user) is ignored.
     * @return whether the pair was stored
     */
    suspend fun onTokensRefreshed(refreshToken: String, auth: AuthResponse): Boolean

    /** [refreshToken] was rejected: the session is over – if it is still the one that sent it. */
    suspend fun onRefreshRejected(refreshToken: String)
}

/**
 * Reacts to `401` on authenticated calls: exchanges the refresh token for a new pair and retries
 * the request once. Refresh tokens are single-use, so refreshing is single-flight: concurrent 401s
 * wait on [mutex] and reuse the pair obtained by whoever refreshed first.
 *
 * A rejected refresh ends the session. A network failure during refresh is rethrown so the
 * original call fails as a connectivity problem while the session is kept.
 *
 * [refreshTokens] must not run on the dispatcher of the client this authenticator serves: the calls
 * waiting here hold its slots (see [HttpClients.authClient]).
 */
class TokenAuthenticator(
    private val session: SessionTokens,
    private val refreshTokens: suspend (refreshToken: String) -> AuthResponse,
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        val failedToken = request.bearerToken() ?: return null
        if (response.priorResponse != null) return null // already retried once

        return runBlocking {
            mutex.withLock { refreshOrReuse(request, failedToken) }
        }
    }

    private suspend fun refreshOrReuse(request: Request, failedToken: String): Request? {
        val currentToken = session.accessToken ?: return null // signed out meanwhile
        if (currentToken != failedToken) {
            // Refreshed by another call meanwhile – unless another user signed in since: this call must
            // not be repeated in their account.
            val sameUser = jwtSubject(currentToken).let { it == null || it == jwtSubject(failedToken) }
            return if (sameUser) request.withBearer(currentToken) else null
        }

        val refreshToken = session.refreshToken ?: return null
        val auth = try {
            refreshTokens(refreshToken)
        } catch (e: IOException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() in REJECTED_REFRESH_CODES) updateSession { session.onRefreshRejected(refreshToken) }
            return null
        } catch (e: RuntimeException) {
            // e.g. an unparseable refresh response: fail this call but keep the session.
            return null
        }
        // Not stored – the session ended or changed meanwhile, or storing failed: this call is not retried.
        val stored = updateSession { session.onTokensRefreshed(refreshToken, auth) } ?: false
        return if (stored) request.withBearer(auth.accessToken) else null
    }

    /**
     * Persisting the session can fail (Keystore, DataStore). That fails only the call being authenticated;
     * an exception escaping [authenticate] would crash the app on an OkHttp thread. `null` when it failed.
     */
    private suspend fun <T> updateSession(update: suspend () -> T): T? = try {
        update()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Couldn't update the session after a token refresh", e)
        null
    }

    private companion object {
        const val TAG = "TokenAuthenticator"
        val REJECTED_REFRESH_CODES = setOf(HTTP_BAD_REQUEST, HTTP_UNAUTHORIZED, HTTP_FORBIDDEN)
    }
}

/** The user id (`sub` claim) of a JWT access token, read without verifying it; `null` when it isn't one. */
internal fun jwtSubject(token: String): String? = runCatching {
    val payload = String(Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8)
    Json.parseToJsonElement(payload).jsonObject["sub"]?.jsonPrimitive?.contentOrNull
}.getOrNull()
