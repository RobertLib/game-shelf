package cz.gameshelf.app.data.lookup

import cz.gameshelf.app.data.api.HttpClients
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.ErrorCode
import cz.gameshelf.app.domain.model.GameSearchResponse
import cz.gameshelf.app.domain.model.Platform
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.create

class RemoteGameSearchRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: RemoteGameSearchRepository

    @Before
    fun setUp() {
        server.start()
        repository = RemoteGameSearchRepository(
            HttpClients.retrofit(server.url("/api/v1/").toString(), OkHttpClient()).create(),
        )
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `sends the text and the API name of the platform`() = runTest {
        server.enqueue(MockResponse(body = """{"items":[],"sources":["IGDB"]}"""))

        val outcome = repository.search("mario kart", Platform.SWITCH_2)

        val url = server.takeRequest().url
        assertEquals("/api/v1/lookup/games", url.encodedPath)
        assertEquals("mario kart", url.queryParameter("q"))
        assertEquals("SWITCH_2", url.queryParameter("platform"))
        assertEquals(GameSearchOutcome.Found(GameSearchResponse(emptyList(), listOf("IGDB"))), outcome)
    }

    @Test
    fun `leaves out the platform when the form has none`() = runTest {
        server.enqueue(MockResponse(body = """{"items":[],"sources":["IGDB"]}"""))

        repository.search("zelda", null)

        assertNull(server.takeRequest().url.queryParameter("platform"))
    }

    @Test
    fun `reports an unavailable database`() = runTest {
        server.enqueue(
            MockResponse(
                code = 503,
                body = """{"statusCode":503,"code":"LOOKUP_UNAVAILABLE","message":"Game database unavailable"}""",
            ),
        )

        val outcome = repository.search("zelda", null)

        assertEquals(GameSearchOutcome.Failed(AppError.Api(503, ErrorCode.LOOKUP_UNAVAILABLE)), outcome)
    }
}
