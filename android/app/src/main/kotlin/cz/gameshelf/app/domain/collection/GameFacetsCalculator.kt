package cz.gameshelf.app.domain.collection

import cz.gameshelf.app.domain.model.FacetValue
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.GameFacets
import java.text.Collator
import java.util.Locale

/**
 * Facets of the local collection with the semantics of the API's `GET games/facets`: distinct values
 * with counts, sorted by count (descending) and then by value. Text values that differ only in letter
 * case are one facet, labelled with the most common spelling (ties: the spelling that sorts first).
 */
object GameFacetsCalculator {

    fun calculate(games: List<Game>, locale: Locale = Locale.getDefault()): GameFacets {
        val collator = Collator.getInstance(locale)
        val byValue = Comparator<String> { a, b -> collator.compare(a, b) }
        val mostCommonFirst = compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy(byValue) { it.key }
        fun facets(values: List<String?>): List<FacetValue> = values
            .filterNotNull()
            .groupingBy { it }
            .eachCount()
            .entries
            .groupBy { it.key.lowercase(Locale.ROOT) }
            .values
            .map { spellings -> FacetValue(spellings.minWith(mostCommonFirst).key, spellings.sumOf { it.value }) }
            .sortedWith(compareByDescending<FacetValue> { it.count }.thenBy(byValue) { it.value })

        val releaseYears = games.mapNotNull { it.releaseYear }
        return GameFacets(
            totalItems = games.size,
            platforms = facets(games.map { it.platform.apiValue }),
            statuses = facets(games.map { it.status.apiValue }),
            genres = facets(games.map { it.genre }),
            publishers = facets(games.map { it.publisher }),
            developers = facets(games.map { it.developer }),
            storageLocations = facets(games.map { it.storageLocation }),
            releaseYearMin = releaseYears.minOrNull(),
            releaseYearMax = releaseYears.maxOrNull(),
        )
    }
}
