package cz.gameshelf.app.domain.collection

import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.FacetValue
import cz.gameshelf.app.domain.model.GameFacets
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.testing.testGame
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class GameFacetsCalculatorTest {

    @Test
    fun `facets of the API e2e collection`() {
        val games = listOf(
            testGame("1", platform = Platform.N64).copy(
                genre = "Action-adventure",
                developer = "Nintendo EAD",
                publisher = "Nintendo",
                storageLocation = "Shelf A",
                releaseYear = 1998,
            ),
            testGame("2", platform = Platform.PS2).copy(
                genre = "Racing",
                publisher = "Sony Computer Entertainment",
                releaseYear = 2001,
            ),
            testGame("3", platform = Platform.ZX_SPECTRUM).copy(
                status = CollectionStatus.WISHLIST,
                genre = "platformer",
                publisher = "Codemasters",
                releaseYear = 1991,
            ),
            testGame("4", platform = Platform.N64).copy(
                genre = "Platformer",
                publisher = "Nintendo",
                releaseYear = 1996,
            ),
        )

        val facets = GameFacetsCalculator.calculate(games, Locale.ENGLISH)

        assertEquals(
            GameFacets(
                totalItems = 4,
                platforms = listOf(FacetValue("N64", 2), FacetValue("PS2", 1), FacetValue("ZX_SPECTRUM", 1)),
                statuses = listOf(FacetValue("OWNED", 3), FacetValue("WISHLIST", 1)),
                genres = listOf(
                    FacetValue("platformer", 2),
                    FacetValue("Action-adventure", 1),
                    FacetValue("Racing", 1),
                ),
                publishers = listOf(
                    FacetValue("Nintendo", 2),
                    FacetValue("Codemasters", 1),
                    FacetValue("Sony Computer Entertainment", 1),
                ),
                developers = listOf(FacetValue("Nintendo EAD", 1)),
                storageLocations = listOf(FacetValue("Shelf A", 1)),
                releaseYearMin = 1991,
                releaseYearMax = 2001,
            ),
            facets,
        )
        assertEquals(mapOf(Platform.N64 to 2, Platform.PS2 to 1, Platform.ZX_SPECTRUM to 1), facets.platformCounts)
    }

    @Test
    fun `case variants are one facet labelled with the most common spelling`() {
        val games = listOf("RPG", "rpg", "RPG", "Rpg", "Action").mapIndexed { index, genre ->
            testGame(index.toString()).copy(genre = genre)
        }

        val facets = GameFacetsCalculator.calculate(games, Locale.ENGLISH)

        assertEquals(listOf(FacetValue("RPG", 4), FacetValue("Action", 1)), facets.genres)
    }

    @Test
    fun `an empty collection has no facets`() {
        assertEquals(GameFacets.Empty, GameFacetsCalculator.calculate(emptyList(), Locale.ENGLISH))
    }
}
