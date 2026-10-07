package cz.gameshelf.app.data.api

import cz.gameshelf.app.data.api.dto.AuthResponse
import cz.gameshelf.app.data.api.dto.RefreshTokenRequest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.create
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TokenAuthenticatorTest {

    private val server = MockWebServer()
    private val session = FakeSessionTokens(accessToken = "old-access", refreshToken = "old-refresh")
    private val refreshBodies = CopyOnWriteArrayList<String>()
    private lateinit var authApi: AuthApi

    @Before
    fun setUp() {
        server.start()
        authApi = HttpClients.retrofit(server.url("/api/v1/").toString(), OkHttpClient()).create()
    }

    @After
    fun tearDown() = server.close()

    private fun client(refresh: suspend (String) -> AuthResponse = { authApi.refresh(RefreshTokenRequest(it)) }) =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { session.accessToken })
            .authenticator(TokenAuthenticator(session, refresh))
            .build()

    private fun gamesRequest() = Request.Builder().url(server.url("/api/v1/games")).build()

    /** `games` accepts only [validToken]; `auth/refresh` answers with [refreshResponse]. */
    private fun serve(validToken: String?, refreshResponse: () -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/api/v1/auth/refresh" -> {
                    refreshBodies += request.body?.utf8().orEmpty()
                    refreshResponse()
                }
                else -> if (validToken != null && request.headers["Authorization"] == "Bearer $validToken") {
                    MockResponse(body = """{"ok":true}""")
                } else {
                    MockResponse(code = 401, body = """{"statusCode":401,"code":"UNAUTHORIZED","message":"Unauthorized"}""")
                }
            }
        }
    }

    @Test
    fun `refreshes on 401 and retries with the new access token`() {
        serve(validToken = "new-access") { MockResponse(body = authJson("new-access", "new-refresh")) }

        client().newCall(gamesRequest()).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("Bearer new-access", response.request.header("Authorization"))
        }
        assertEquals(1, refreshBodies.size)
        assertTrue(refreshBodies.single().contains("\"refreshToken\":\"old-refresh\""))
        assertEquals("new-access", session.accessToken)
        assertEquals("new-refresh", session.refreshToken)
        assertFalse(session.signedOut)
    }

    @Test
    fun `concurrent 401s share a single refresh`() {
        serve(validToken = "new-access") {
            MockResponse.Builder()
                .body(authJson("new-access", "new-refresh"))
                .headersDelay(300, TimeUnit.MILLISECONDS)
                .build()
        }
        val client = client()
        val executor = Executors.newFixedThreadPool(5)

        val codes = (1..5)
            .map { executor.submit<Int> { client.newCall(gamesRequest()).execute().use { it.code } } }
            .map { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(List(5) { 200 }, codes)
        assertEquals("refresh tokens are single-use, so only one refresh may happen", 1, refreshBodies.size)
    }

    @Test
    fun `rejected refresh token ends the session`() {
        serve(validToken = "new-access") {
            MockResponse(
                code = 401,
                body = """{"statusCode":401,"code":"INVALID_REFRESH_TOKEN","message":"Invalid refresh token"}""",
            )
        }

        client().newCall(gamesRequest()).execute().use { response -> assertEquals(401, response.code) }

        assertTrue(session.signedOut)
        assertNull(session.accessToken)
    }

    @Test
    fun `retries only once when the new token is rejected as well`() {
        serve(validToken = null) { MockResponse(body = authJson("new-access", "new-refresh")) }

        client().newCall(gamesRequest()).execute().use { response -> assertEquals(401, response.code) }

        assertEquals(1, refreshBodies.size)
    }

    @Test
    fun `network failure during refresh keeps the session`() {
        serve(validToken = "new-access") { MockResponse(body = authJson("new-access", "new-refresh")) }

        try {
            client(refresh = { throw IOException("offline") }).newCall(gamesRequest()).execute().close()
            fail("expected the call to fail with IOException")
        } catch (expected: IOException) {
            // surfaces as a connectivity error
        }
        assertFalse(session.signedOut)
        assertEquals("old-access", session.accessToken)
    }

    @Test
    fun `server error during refresh fails the call without signing out`() {
        serve(validToken = "new-access") { MockResponse(code = 503) }

        client().newCall(gamesRequest()).execute().use { response -> assertEquals(401, response.code) }

        assertFalse(session.signedOut)
    }

    @Test
    fun `requests without a session are not refreshed`() {
        session.accessToken = null
        serve(validToken = "new-access") { MockResponse(body = authJson("new-access", "new-refresh")) }

        client().newCall(gamesRequest()).execute().use { response -> assertEquals(401, response.code) }

        assertTrue(refreshBodies.isEmpty())
    }

    private fun authJson(access: String, refresh: String) =
        """{"accessToken":"$access","expiresIn":900,"refreshToken":"$refresh",
           "user":{"id":"u1","email":"a@b.cz","displayName":null,"createdAt":"2026-01-01T00:00:00Z"}}"""

    private class FakeSessionTokens(
        @Volatile override var accessToken: String?,
        @Volatile override var refreshToken: String?,
    ) : SessionTokens {
        @Volatile
        var signedOut = false

        override suspend fun onTokensRefreshed(auth: AuthResponse) {
            accessToken = auth.accessToken
            refreshToken = auth.refreshToken
        }

        override suspend fun onRefreshRejected() {
            signedOut = true
            accessToken = null
            refreshToken = null
        }
    }

}
