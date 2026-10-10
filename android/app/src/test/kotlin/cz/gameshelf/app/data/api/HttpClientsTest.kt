package cz.gameshelf.app.data.api

import cz.gameshelf.app.data.api.HttpClients.addDebugLogging
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class HttpClientsTest {

    private val server = MockWebServer()
    private val logged = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun client(enabled: Boolean = true) = OkHttpClient.Builder().addDebugLogging(enabled) { logged += it }.build()

    private fun post(path: String, json: String) = Request.Builder()
        .url(server.url(path))
        .post(json.toRequestBody("application/json".toMediaType()))
        .build()

    @Test
    fun `debug logging never shows passwords or tokens`() {
        server.enqueue(MockResponse(body = """{"accessToken":"access-secret","refreshToken":"refresh-secret"}"""))
        server.enqueue(MockResponse(body = """{"accessToken":"access-secret-2","refreshToken":"refresh-secret-2"}"""))
        server.enqueue(MockResponse(body = """{"title":"Banjo-Kazooie"}"""))
        val client = client()

        client.newCall(post("/api/v1/auth/login", """{"email":"a@b.cz","password":"hunter2hunter2"}""")).execute().close()
        client.newCall(post("/api/v1/auth/refresh", """{"refreshToken":"refresh-secret"}""")).execute().close()
        val games = Request.Builder().url(server.url("/api/v1/games/1")).header("Authorization", "Bearer bearer-secret").build()
        client.newCall(games).execute().close()

        val log = logged.joinToString("\n")
        listOf("hunter2hunter2", "access-secret", "refresh-secret", "bearer-secret").forEach { secret ->
            assertFalse("$secret was logged", secret in log)
        }
        // Still useful: every request is logged, other bodies included.
        assertTrue(log.contains("--> POST ${server.url("/api/v1/auth/login")}"))
        assertTrue(log.contains("<-- 200"))
        assertTrue(log.contains("Banjo-Kazooie"))
    }

    @Test
    fun `release builds log nothing`() {
        server.enqueue(MockResponse(body = "{}"))

        client(enabled = false).newCall(Request.Builder().url(server.url("/api/v1/games/1")).build()).execute().close()

        assertEquals(emptyList<String>(), logged)
    }
}
