package cz.gameshelf.app.data.api

import cz.gameshelf.app.data.api.dto.AuthResponse
import cz.gameshelf.app.data.api.dto.RefreshTokenRequest
import cz.gameshelf.app.data.auth.SessionManager
import cz.gameshelf.app.data.auth.SessionState
import cz.gameshelf.app.data.auth.StoredSession
import cz.gameshelf.app.domain.model.User
import cz.gameshelf.app.testing.FakeSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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
import java.security.GeneralSecurityException
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
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
        server.dispatcher = object : mockwebserver3.Dispatcher() {
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
    fun `a refresh that completes after the session changed is not applied`() = runBlocking {
        serve(validToken = "access-a2") { MockResponse(body = authJson("access-a2", "refresh-a2")) }
        val sessions = FakeSessionStore(StoredSession("access-a", "refresh-a", USER_A))
        val manager = SessionManager(sessions, this)
        manager.state.first { it != SessionState.Loading }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { manager.accessToken })
            .authenticator(
                TokenAuthenticator(manager) { refreshToken ->
                    // A signs out and B signs in while A's refresh is on its way.
                    manager.signOut()
                    manager.signIn(AuthResponse("access-b", 900, "refresh-b", USER_B))
                    authApi.refresh(RefreshTokenRequest(refreshToken))
                },
            )
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(gamesRequest()).execute().use { response -> assertEquals(401, response.code) }
        }

        assertEquals(SessionState.SignedIn(USER_B), manager.state.value)
        assertEquals("access-b", manager.accessToken)
        assertEquals("refresh-b", sessions.session?.refreshToken)
    }

    @Test
    fun `a session that can't be stored fails only this call`() {
        serve(validToken = "new-access") { MockResponse(body = authJson("new-access", "new-refresh")) }
        session.updateFailure = GeneralSecurityException("keystore unavailable")

        client().newCall(gamesRequest()).execute().use { response -> assertEquals(401, response.code) }

        assertFalse(session.signedOut)
    }

    @Test
    fun `a session that can't be ended fails only this call`() {
        serve(validToken = "new-access") {
            MockResponse(code = 401, body = """{"statusCode":401,"code":"INVALID_REFRESH_TOKEN","message":"Invalid"}""")
        }
        session.updateFailure = IOException("disk full")

        client().newCall(gamesRequest()).execute().use { response -> assertEquals(401, response.code) }
    }

    @Test
    fun `the refresh never waits for a dispatcher slot held by the calls waiting for it`() {
        serve(validToken = "new-access") { MockResponse(body = authJson("new-access", "new-refresh")) }
        // As in AppContainer: the authenticated client shares the base dispatcher, the auth client does not.
        val base = OkHttpClient.Builder().dispatcher(Dispatcher().apply { maxRequestsPerHost = 2 }).build()
        val authApi: AuthApi = HttpClients
            .retrofit(server.url("/api/v1/").toString(), HttpClients.authClient(base, debugLogging = false))
            .create()
        val client = base.newBuilder()
            .addInterceptor(AuthInterceptor { session.accessToken })
            .authenticator(TokenAuthenticator(session) { authApi.refresh(RefreshTokenRequest(it)) })
            .build()
        val codes = CopyOnWriteArrayList<Int>()
        val finished = CountDownLatch(2)

        // Both calls take a slot of the shared dispatcher and wait in the authenticator for the refresh.
        repeat(2) {
            client.newCall(gamesRequest()).enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        response.use { codes += it.code }
                        finished.countDown()
                    }

                    override fun onFailure(call: Call, e: IOException) = finished.countDown()
                },
            )
        }

        assertTrue("deadlocked", finished.await(10, TimeUnit.SECONDS))
        assertEquals(listOf(200, 200), codes)
        assertEquals(1, refreshBodies.size)
    }

    @Test
    fun `a call of a user who was replaced by another one meanwhile is not repeated with their token`() {
        val tokenA = jwt(sub = "u1", nonce = 1)
        val tokenB = jwt(sub = "u2", nonce = 1)
        session.accessToken = tokenA
        val authorizations = CopyOnWriteArrayList<String?>()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                authorizations += request.headers["Authorization"]
                // A signs out and B signs in while A's call is on its way.
                session.accessToken = tokenB
                return MockResponse(code = 401, body = """{"statusCode":401,"code":"UNAUTHORIZED","message":"x"}""")
            }
        }

        client().newCall(gamesRequest()).execute().use { response -> assertEquals(401, response.code) }

        assertEquals(listOf("Bearer $tokenA"), authorizations)
        assertTrue(refreshBodies.isEmpty())
    }

    @Test
    fun `a call of the same user is repeated with the token another call refreshed meanwhile`() {
        val oldToken = jwt(sub = "u1", nonce = 1)
        val newToken = jwt(sub = "u1", nonce = 2)
        session.accessToken = oldToken
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.headers["Authorization"] == "Bearer $newToken") {
                    MockResponse(body = """{"ok":true}""")
                } else {
                    session.accessToken = newToken // refreshed by a concurrent call
                    MockResponse(code = 401, body = """{"statusCode":401,"code":"UNAUTHORIZED","message":"x"}""")
                }
        }

        client().newCall(gamesRequest()).execute().use { response -> assertEquals(200, response.code) }

        assertTrue(refreshBodies.isEmpty())
    }

    @Test
    fun `jwtSubject reads the sub claim and rejects other tokens`() {
        assertEquals("u1", jwtSubject(jwt(sub = "u1", nonce = 1)))
        assertNull(jwtSubject("opaque-token"))
        assertNull(jwtSubject("a.%%%.c"))
    }

    @Test
    fun `requests without a session are not refreshed`() {
        session.accessToken = null
        serve(validToken = "new-access") { MockResponse(body = authJson("new-access", "new-refresh")) }

        client().newCall(gamesRequest()).execute().use { response -> assertEquals(401, response.code) }

        assertTrue(refreshBodies.isEmpty())
    }

    /** An access token shaped like the API's (only the payload matters here). */
    private fun jwt(sub: String, nonce: Int): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val payload = encoder.encodeToString("""{"sub":"$sub","n":$nonce}""".toByteArray())
        return "eyJhbGciOiJIUzI1NiJ9.$payload.signature"
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

        /** Thrown by every update, like a Keystore or DataStore failure. */
        @Volatile
        var updateFailure: Exception? = null

        override suspend fun onTokensRefreshed(refreshToken: String, auth: AuthResponse): Boolean {
            updateFailure?.let { throw it }
            if (refreshToken != this.refreshToken) return false
            accessToken = auth.accessToken
            this.refreshToken = auth.refreshToken
            return true
        }

        override suspend fun onRefreshRejected(refreshToken: String) {
            updateFailure?.let { throw it }
            signedOut = true
            accessToken = null
            this.refreshToken = null
        }
    }

    private companion object {
        val USER_A = User("u1", "a@b.cz", null, Instant.parse("2026-01-01T00:00:00Z"))
        val USER_B = User("u2", "b@b.cz", null, Instant.parse("2026-01-01T00:00:00Z"))
    }
}
