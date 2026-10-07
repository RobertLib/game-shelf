package cz.gameshelf.app.data.api

import cz.gameshelf.app.data.api.dto.AuthResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import java.io.IOException
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

/** Token state the [TokenAuthenticator] reads and updates. */
interface SessionTokens {
    val accessToken: String?
    val refreshToken: String?

    suspend fun onTokensRefreshed(auth: AuthResponse)

    /** The refresh token was rejected; the session is over. */
    suspend fun onRefreshRejected()
}

/**
 * Reacts to `401` on authenticated calls: exchanges the refresh token for a new pair and retries
 * the request once. Refresh tokens are single-use, so refreshing is single-flight: concurrent 401s
 * wait on [mutex] and reuse the pair obtained by whoever refreshed first.
 *
 * A rejected refresh ends the session. A network failure during refresh is rethrown so the
 * original call fails as a connectivity problem while the session is kept.
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
        if (currentToken != failedToken) return request.withBearer(currentToken)

        val refreshToken = session.refreshToken ?: return null
        val auth = try {
            refreshTokens(refreshToken)
        } catch (e: IOException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() in REJECTED_REFRESH_CODES) session.onRefreshRejected()
            return null
        } catch (e: RuntimeException) {
            // e.g. an unparseable refresh response: fail this call but keep the session.
            return null
        }
        session.onTokensRefreshed(auth)
        return request.withBearer(auth.accessToken)
    }

    private companion object {
        val REJECTED_REFRESH_CODES = setOf(HTTP_BAD_REQUEST, HTTP_UNAUTHORIZED, HTTP_FORBIDDEN)
    }
}
