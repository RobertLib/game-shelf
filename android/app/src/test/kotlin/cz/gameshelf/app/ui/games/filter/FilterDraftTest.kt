package cz.gameshelf.app.ui.games.filter

import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.ActiveFilter
import cz.gameshelf.app.domain.model.CoverFilter
import cz.gameshelf.app.domain.model.GameFilter
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.ui.common.UiText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

class FilterDraftTest {

    private val defaultLocale = Locale.getDefault()

    @Before
    fun setUp() = Locale.setDefault(Locale.US)

    @After
    fun tearDown() = Locale.setDefault(defaultLocale)

    @Test
    fun `round-trips a filter`() {
        val filter = GameFilter(
            platforms = setOf(Platform.SNES),
            publisher = "Nintendo",
            favoritesOnly = true,
            cover = CoverFilter.WITH_COVER,
            releaseYearFrom = 1990,
            purchasePriceMax = BigDecimal("499.5"),
            purchaseDateFrom = LocalDate.of(2020, 1, 1),
            ratingMin = 8,
        )

        val draft = FilterDraft.from(filter)

        // Prefilled with the device locale's decimal separator (pinned to US English in this test).
        assertEquals("499.5", draft.purchasePriceMax)
        assertEquals(filter, draft.toFilter())
    }

    @Test
    fun `invalid and inverted ranges are reported and block applying`() {
        val draft = FilterDraft(
            releaseYearFrom = "2005",
            releaseYearTo = "1995",
            purchasePriceMin = "10,5555",
            purchaseDateFrom = LocalDate.of(2024, 2, 1),
            purchaseDateTo = LocalDate.of(2024, 1, 1),
        )

        val errors = draft.errors
        assertEquals(UiText(R.string.validation_range), errors.releaseYear)
        assertEquals(UiText(R.string.validation_price), errors.purchasePrice)
        assertEquals(UiText(R.string.validation_range), errors.purchaseDate)
        assertNull(errors.estimatedValue)
        assertNull(draft.toFilter())
    }

    @Test
    fun `price bounds read decimals and grouping the same way in every locale`() {
        val filter = FilterDraft(purchasePriceMin = "1,000", purchasePriceMax = "1.299,50", estimatedValueMax = "2 500,5")
            .toFilter()!!

        assertEquals(BigDecimal("1000"), filter.purchasePriceMin)
        assertEquals(BigDecimal("1299.50"), filter.purchasePriceMax)
        assertEquals(BigDecimal("2500.5"), filter.estimatedValueMax)
    }

    @Test
    fun `year outside the API range is invalid`() {
        assertEquals(UiText(R.string.validation_year), FilterDraft(releaseYearFrom = "1900").errors.releaseYear)
    }

    @Test
    fun `blank text filters are dropped`() {
        val filter = FilterDraft(publisher = "   ", developer = " Rare ").toFilter()!!

        assertNull(filter.publisher)
        assertEquals("Rare", filter.developer)
    }
}

class GameFilterTest {

    @Test
    fun `active filters produce one chip per value and one per range`() {
        val filter = GameFilter(
            platforms = setOf(Platform.PS2, Platform.N64),
            genres = setOf("RPG"),
            favoritesOnly = true,
            releaseYearFrom = 1990,
            releaseYearTo = 1999,
            ratingMin = 7,
        )

        assertEquals(
            listOf(
                ActiveFilter.PlatformValue(Platform.PS2),
                ActiveFilter.PlatformValue(Platform.N64),
                ActiveFilter.Genre("RPG"),
                ActiveFilter.FavoritesOnly,
                ActiveFilter.ReleaseYear(1990, 1999),
                ActiveFilter.RatingMin(7),
            ),
            filter.activeFilters,
        )
    }

    @Test
    fun `removing every chip yields an empty filter`() {
        var filter = GameFilter(
            platforms = setOf(Platform.PS2),
            cover = CoverFilter.WITHOUT_COVER,
            purchasePriceMin = BigDecimal.ONE,
            storageLocation = "Shelf A",
        )

        filter.activeFilters.forEach { filter = filter.without(it) }

        assertTrue(filter.isEmpty)
    }
}
