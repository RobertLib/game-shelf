package cz.gameshelf.app.data.api

import android.util.Log
import cz.gameshelf.app.data.api.dto.AuthResponse
import cz.gameshelf.app.data.api.dto.ErrorResponse
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.ErrorCode
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

    /** The API rejected [refreshToken]: the session is over – if it is still the one that sent it. */
    suspend fun onRefreshRejected(refreshToken: String)
}

/**
 * Reacts to `401` on authenticated calls: exchanges the refresh token for a new pair and retries
 * the request once. Refresh tokens are single-use, so refreshing is single-flight: concurrent 401s
 * wait on [mutex] and reuse the pair obtained by whoever refreshed first.
 *
 * Only the API's own answers count (mobile-spec.md, "API basics"): a `401` without an API error body (a
 * proxy's page) is an error of that call and is not refreshed, and only a refresh the API rejects – a `4xx`
 * with an API error body, except `408` / `429` – ends the session. Any other failure of the refresh keeps
 * the session and fails the call; a network failure is rethrown, so it fails as a connectivity problem.
 *
 * `401 ACCOUNT_DELETED` – the account was deleted, e.g. on another device; the API answers it to any access token
 * of the account, expired or not – is never refreshed: the call fails and [onAccountDeleted] gets the token it was
 * sent with, so that the session ends like a sign-out.
 *
 * [refreshTokens] must not run on the dispatcher of the client this authenticator serves: the calls
 * waiting here hold its slots (see [HttpClients.authClient]).
 */
class TokenAuthenticator(
    private val session: SessionTokens,
    private val onAccountDeleted: (accessToken: String) -> Unit,
    private val refreshTokens: suspend (refreshToken: String) -> AuthResponse,
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        val failedToken = request.bearerToken() ?: return null
        // Not from the API: says nothing about the token.
        val error = response.apiError() ?: return null
        if (error.code == ErrorCode.ACCOUNT_DELETED) {
            onAccountDeleted(failedToken)
            return null
        }
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
            val error = e.toAppError()
            when {
                // Deleted while the access token expired: like for any other call of the account.
                error is AppError.Api && error.code == ErrorCode.ACCOUNT_DELETED -> onAccountDeleted(failedToken)
                error.isRefreshRejection() -> updateSession { session.onRefreshRejected(refreshToken) }
            }
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

    /** The API error body; read without consuming it, as the caller still gets it when the call is not retried. */
    private fun Response.apiError(): ErrorResponse? =
        parseApiError(runCatching { peekBody(MAX_ERROR_BODY_BYTES).string() }.getOrNull())

    /** A `4xx` from the API itself; `408` and `429` say "later", not that the token is invalid. */
    private fun AppError.isRefreshRejection() =
        this is AppError.Api && statusCode in 400..499 && statusCode !in setOf(HTTP_REQUEST_TIMEOUT, HTTP_TOO_MANY_REQUESTS)

    private companion object {
        const val TAG = "TokenAuthenticator"
        const val HTTP_REQUEST_TIMEOUT = 408
        const val HTTP_TOO_MANY_REQUESTS = 429

        /** An API error body is small; a larger body is a page from something else. */
        const val MAX_ERROR_BODY_BYTES = 64 * 1024L
    }
}

/** The user id (`sub` claim) of a JWT access token, read without verifying it; `null` when it isn't one. */
internal fun jwtSubject(token: String): String? = runCatching {
    val payload = String(Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8)
    Json.parseToJsonElement(payload).jsonObject["sub"]?.jsonPrimitive?.contentOrNull
}.getOrNull()
