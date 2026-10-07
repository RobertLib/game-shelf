package cz.gameshelf.app.data.games

import cz.gameshelf.app.data.api.GamesApi
import cz.gameshelf.app.data.api.HttpClients
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.CoverFilter
import cz.gameshelf.app.domain.model.GameFilter
import cz.gameshelf.app.domain.model.GameQuery
import cz.gameshelf.app.domain.model.GameSort
import cz.gameshelf.app.domain.model.GameSortField
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.PlayStatus
import cz.gameshelf.app.domain.model.SortOrder
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.create
import java.math.BigDecimal
import java.time.LocalDate

class GameListQueryTest {

    private val fullFilter = GameFilter(
        platforms = setOf(Platform.PS5, Platform.PS2),
        statuses = setOf(CollectionStatus.WISHLIST),
        playStatuses = setOf(PlayStatus.PLAYING, PlayStatus.UNPLAYED),
        genres = setOf("RPG", "Action"),
        publisher = "  Nintendo ",
        developer = "   ",
        favoritesOnly = true,
        cover = CoverFilter.WITHOUT_COVER,
        releaseYearFrom = 1990,
        releaseYearTo = 2000,
        purchasePriceMin = BigDecimal("100.50"),
        estimatedValueMax = BigDecimal("2000"),
        purchaseDateFrom = LocalDate.of(2024, 1, 1),
        purchaseDateTo = LocalDate.of(2024, 12, 31),
        ratingMin = 7,
    )

    @Test
    fun `maps every filter to its query parameter`() {
        val query = GameListQuery.from(
            GameQuery(
                search = "  zelda ocarina ",
                filter = fullFilter,
                sort = GameSort(GameSortField.RELEASE_YEAR, SortOrder.DESC),
            ),
            page = 3,
        )

        assertEquals("zelda ocarina", query.q)
        assertEquals(listOf("PS2", "PS5"), query.platform)
        assertEquals(listOf("WISHLIST"), query.status)
        assertEquals(listOf("UNPLAYED", "PLAYING"), query.playStatus)
        assertEquals(listOf("Action", "RPG"), query.genre)
        assertEquals("Nintendo", query.publisher)
        assertNull("blank text filters are not sent", query.developer)
        assertEquals(true, query.favorite)
        assertEquals(false, query.hasCover)
        assertEquals(1990, query.releaseYearFrom)
        assertEquals(2000, query.releaseYearTo)
        assertEquals("100.5", query.purchasePriceMin)
        assertEquals("2000", query.estimatedValueMax)
        assertEquals("2024-01-01", query.purchaseDateFrom)
        assertEquals("2024-12-31", query.purchaseDateTo)
        assertEquals(7, query.ratingMin)
        assertEquals("releaseYear", query.sort)
        assertEquals("desc", query.order)
        assertEquals(3, query.page)
        assertEquals(25, query.pageSize)
    }

    @Test
    fun `empty filter sends only sorting and paging`() {
        val query = GameListQuery.from(GameQuery(search = "   "), page = 1)

        assertEquals(
            GameListQuery(sort = "title", order = "asc", page = 1, pageSize = 25),
            query,
        )
    }

    @Test
    fun `favorites toggle off and any cover are omitted`() {
        val query = GameListQuery.from(GameQuery(filter = GameFilter(favoritesOnly = false, cover = CoverFilter.ANY)), 1)

        assertNull(query.favorite)
        assertNull(query.hasCover)
        assertEquals(true, GameListQuery.from(GameQuery(filter = GameFilter(cover = CoverFilter.WITH_COVER)), 1).hasCover)
    }
}

/** The same mapping, verified on the wire through Retrofit. */
class GameListQueryHttpTest {
    private val server = MockWebServer()
    private lateinit var repository: NetworkGamesRepository

    @Before
    fun setUp() {
        server.start()
        val api: GamesApi = HttpClients.retrofit(server.url("/api/v1/").toString(), OkHttpClient()).create()
        repository = NetworkGamesRepository(api)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `multi-value filters are sent as repeated parameters`() = runTest {
        server.enqueue(MockResponse(body = """{"items":[],"page":2,"pageSize":25,"totalItems":0,"totalPages":0}"""))
        val filter = GameFilter(
            platforms = setOf(Platform.PS5, Platform.PS2),
            genres = setOf("Action, Adventure", "RPG"),
            favoritesOnly = true,
        )

        val result = repository.listGames(GameQuery(search = "zelda ocarina", filter = filter), page = 2)

        assertTrue(result is ApiResult.Success)
        val url = server.takeRequest().url
        assertEquals("/api/v1/games", url.encodedPath)
        assertEquals(listOf("PS2", "PS5"), url.queryParameterValues("platform"))
        assertEquals(listOf("Action, Adventure", "RPG"), url.queryParameterValues("genre"))
        assertEquals("zelda ocarina", url.queryParameter("q"))
        assertEquals("true", url.queryParameter("favorite"))
        assertEquals("2", url.queryParameter("page"))
        assertEquals("25", url.queryParameter("pageSize"))
        assertEquals("title", url.queryParameter("sort"))
        assertTrue("unused filters are not sent", "status" !in url.queryParameterNames)
        assertTrue("hasCover" !in url.queryParameterNames)
    }
}
