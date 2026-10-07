package cz.gameshelf.app.domain.collection

import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Completeness
import cz.gameshelf.app.domain.model.Condition
import cz.gameshelf.app.domain.model.CoverFilter
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.GameFilter
import cz.gameshelf.app.domain.model.GameFormat
import cz.gameshelf.app.domain.model.GameQuery
import cz.gameshelf.app.domain.model.GameSort
import cz.gameshelf.app.domain.model.GameSortField
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.PlatformSection
import cz.gameshelf.app.domain.model.Region
import cz.gameshelf.app.domain.model.SortOrder
import cz.gameshelf.app.testing.testGame
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** The listing cases of `api/test/games.e2e-spec.ts`, run against the local query engine. */
class GameQueryEngineTest {

    private val zelda = testGame("1", title = ZELDA, platform = Platform.N64).copy(
        region = Region.PAL,
        completeness = Completeness.CIB,
        condition = Condition.VERY_GOOD,
        genre = "Action-adventure",
        developer = "Nintendo EAD",
        publisher = "Nintendo",
        releaseYear = 1998,
        productCode = "NUS-NZLP-EUR",
        purchasePrice = BigDecimal("1299.9"),
        purchaseDate = LocalDate.of(2024, 5, 17),
        estimatedValue = BigDecimal("2500"),
        storageLocation = "Shelf A",
        rating = 10,
        favorite = true,
        coverImageUrl = "https://example.com/zelda.jpg",
    )
    private val granTurismo = testGame("2", title = GRAN_TURISMO, platform = Platform.PS2).copy(
        genre = "Racing",
        publisher = "Sony Computer Entertainment",
        releaseYear = 2001,
        purchasePrice = BigDecimal("150"),
        purchaseDate = LocalDate.of(2023, 1, 10),
        completeness = Completeness.LOOSE,
        rating = 7,
    )
    private val dizzy = testGame("3", title = DIZZY, platform = Platform.ZX_SPECTRUM).copy(
        status = CollectionStatus.WISHLIST,
        genre = "platformer",
        publisher = "Codemasters",
        releaseYear = 1991,
        notes = "Looking for the original cassette",
    )
    private val mario = testGame("4", title = MARIO, platform = Platform.N64).copy(
        genre = "Platformer",
        publisher = "Nintendo",
        releaseYear = 1996,
        estimatedValue = BigDecimal("900"),
        favorite = true,
        format = GameFormat.DIGITAL,
    )
    private val collection = listOf(mario, zelda, dizzy, granTurismo)

    private fun titles(query: GameQuery, games: List<Game> = collection) =
        GameQueryEngine.run(games, query, Locale.ENGLISH).map { it.title }

    @Test
    fun `sorts by title by default`() {
        assertEquals(listOf(DIZZY, GRAN_TURISMO, MARIO, ZELDA), titles(GameQuery()))
    }

    @Test
    fun `puts missing values last regardless of direction`() {
        assertEquals(
            listOf(ZELDA, GRAN_TURISMO, DIZZY, MARIO),
            titles(GameQuery(sort = GameSort(GameSortField.PURCHASE_PRICE, SortOrder.DESC))),
        )
        assertEquals(
            listOf(DIZZY, MARIO, ZELDA, GRAN_TURISMO),
            titles(GameQuery(sort = GameSort(GameSortField.RELEASE_YEAR, SortOrder.ASC))),
        )
    }

    @Test
    fun `filters like the API`() {
        val nintendo64Favorites = GameFilter(platforms = setOf(Platform.N64), favoritesOnly = true)
        val cases = listOf(
            Case("q=zelda ocarina", GameQuery(search = "zelda ocarina"), ZELDA),
            Case("q=NUS-NZLP", GameQuery(search = "NUS-NZLP"), ZELDA),
            Case("q=cassette", GameQuery(search = "cassette"), DIZZY),
            Case("q=mario zelda", GameQuery(search = " mario  zelda ")),
            Case("platform=N64", GameFilter(platforms = setOf(Platform.N64)), MARIO, ZELDA),
            Case(
                "platform=PS2,ZX_SPECTRUM",
                GameFilter(platforms = setOf(Platform.PS2, Platform.ZX_SPECTRUM)),
                DIZZY,
                GRAN_TURISMO,
            ),
            Case("status=WISHLIST", GameFilter(statuses = setOf(CollectionStatus.WISHLIST)), DIZZY),
            Case("format=DIGITAL", GameFilter(formats = setOf(GameFormat.DIGITAL)), MARIO),
            Case(
                "completeness=CIB,LOOSE",
                GameFilter(completeness = setOf(Completeness.CIB, Completeness.LOOSE)),
                GRAN_TURISMO,
                ZELDA,
            ),
            Case("genre=PLATFORMER", GameFilter(genres = setOf("PLATFORMER")), DIZZY, MARIO),
            Case("publisher=nintendo", GameFilter(publisher = "nintendo"), MARIO, ZELDA),
            Case("storageLocation=shelf", GameFilter(storageLocation = "shelf"), ZELDA),
            Case("favorite=true", GameFilter(favoritesOnly = true), MARIO, ZELDA),
            Case("hasCover=true", GameFilter(cover = CoverFilter.WITH_COVER), ZELDA),
            Case("hasCover=false", GameFilter(cover = CoverFilter.WITHOUT_COVER), DIZZY, GRAN_TURISMO, MARIO),
            Case("releaseYear 1995–1999", GameFilter(releaseYearFrom = 1995, releaseYearTo = 1999), MARIO, ZELDA),
            Case("purchaseDateFrom=2024-01-01", GameFilter(purchaseDateFrom = LocalDate.of(2024, 1, 1)), ZELDA),
            Case(
                "purchasePriceMin=100&purchasePriceMax=200",
                GameFilter(purchasePriceMin = BigDecimal("100"), purchasePriceMax = BigDecimal("200")),
                GRAN_TURISMO,
            ),
            Case("estimatedValueMin=1000", GameFilter(estimatedValueMin = BigDecimal("1000")), ZELDA),
            Case("ratingMin=8", GameFilter(ratingMin = 8), ZELDA),
            Case("N64 favorites with q=mario", GameQuery(search = "mario", filter = nintendo64Favorites), MARIO),
        )
        for (case in cases) assertEquals(case.name, case.expected, titles(case.query))
    }

    @Test
    fun `blank text filters and search are ignored`() {
        val query = GameQuery(search = "   ", filter = GameFilter(publisher = "  ", developer = ""))

        assertEquals(4, titles(query).size)
        assertEquals(listOf(MARIO, ZELDA), titles(GameQuery(filter = GameFilter(publisher = " Nintendo "))))
    }

    @Test
    fun `ranges are inclusive and never match a missing value`() {
        val exactPrice = GameFilter(purchasePriceMin = BigDecimal("150.00"), purchasePriceMax = BigDecimal("150"))

        assertEquals(listOf(GRAN_TURISMO), titles(GameQuery(filter = exactPrice)))
        assertEquals(listOf(DIZZY), titles(GameQuery(filter = GameFilter(releaseYearTo = 1991))))
        assertEquals(emptyList<String>(), titles(GameQuery(filter = GameFilter(ratingMin = 11))))
    }

    @Test
    fun `titles sort case-insensitively, then by id`() {
        val games = listOf(
            testGame("b", title = "banana"),
            testGame("c", title = "Cherry"),
            testGame("a2", title = "apple"),
            testGame("a1", title = "Apple"),
        )

        fun ids(order: SortOrder) =
            GameQueryEngine.run(games, GameQuery(sort = GameSort(order = order)), Locale.ENGLISH).map { it.id }

        assertEquals(listOf("a1", "a2", "b", "c"), ids(SortOrder.ASC))
        // Equal titles stay in id order in both directions.
        assertEquals(listOf("c", "b", "a1", "a2"), ids(SortOrder.DESC))
    }

    @Test
    fun `platforms sort in enum order, then by title`() {
        assertEquals(
            listOf(GRAN_TURISMO, MARIO, ZELDA, DIZZY),
            titles(GameQuery(sort = GameSort(GameSortField.PLATFORM))),
        )
        assertEquals(
            listOf(DIZZY, MARIO, ZELDA, GRAN_TURISMO),
            titles(GameQuery(sort = GameSort(GameSortField.PLATFORM, SortOrder.DESC))),
        )
    }

    @Test
    fun `rating descending keeps unrated games last and dates sort chronologically`() {
        assertEquals(
            listOf(ZELDA, GRAN_TURISMO, DIZZY, MARIO),
            titles(GameQuery(sort = GameSort(GameSortField.RATING, SortOrder.DESC))),
        )
        val dated = collection.mapIndexed { index, game -> game.copy(createdAt = Instant.ofEpochSecond(10L - index)) }
        assertEquals(
            listOf(GRAN_TURISMO, DIZZY, ZELDA, MARIO),
            titles(GameQuery(sort = GameSort(GameSortField.CREATED_AT)), dated),
        )
    }

    @Test
    fun `groups by platform in platform order, keeping the sort within a platform`() {
        fun sections(sort: GameSort) = GameQueryEngine.groupByPlatform(
            GameQueryEngine.run(collection, GameQuery(sort = sort), Locale.ENGLISH),
            sort,
        ).map { section -> section.platform to section.games.map { it.title } }

        val byRatingDescending = GameSort(GameSortField.RATING, SortOrder.DESC)
        assertEquals(
            listOf(
                Platform.PS2 to listOf(GRAN_TURISMO),
                Platform.N64 to listOf(ZELDA, MARIO),
                Platform.ZX_SPECTRUM to listOf(DIZZY),
            ),
            sections(byRatingDescending),
        )
        // Sorting by platform descending reverses the sections, titles stay ascending.
        assertEquals(
            listOf(
                Platform.ZX_SPECTRUM to listOf(DIZZY),
                Platform.N64 to listOf(MARIO, ZELDA),
                Platform.PS2 to listOf(GRAN_TURISMO),
            ),
            sections(GameSort(GameSortField.PLATFORM, SortOrder.DESC)),
        )
        assertEquals(emptyList<PlatformSection>(), GameQueryEngine.groupByPlatform(emptyList(), GameSort()))
    }

    private class Case(val name: String, val query: GameQuery, vararg expected: String) {
        val expected = expected.toList()

        constructor(name: String, filter: GameFilter, vararg expected: String) :
            this(name, GameQuery(filter = filter), *expected)
    }

    private companion object {
        const val ZELDA = "The Legend of Zelda: Ocarina of Time"
        const val GRAN_TURISMO = "Gran Turismo 3: A-Spec"
        const val DIZZY = "Dizzy: Prince of the Yolkfolk"
        const val MARIO = "Super Mario 64"
    }
}
