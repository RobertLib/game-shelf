package cz.gameshelf.app.domain.collection

import cz.gameshelf.app.domain.model.CoverFilter
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.GameFilter
import cz.gameshelf.app.domain.model.GameQuery
import cz.gameshelf.app.domain.model.GameSort
import cz.gameshelf.app.domain.model.GameSortField
import cz.gameshelf.app.domain.model.SortOrder
import java.text.CollationKey
import java.text.Collator
import java.util.Locale

/**
 * Search, filters and sorting of the collection, computed on the device with the semantics of the
 * API's `GET games` (mobile-spec.md, "API basics"). Pure and CPU-bound: call it off the main thread.
 */
object GameQueryEngine {

    private val Whitespace = Regex("\\s+")

    fun run(games: List<Game>, query: GameQuery, locale: Locale = Locale.getDefault()): List<Game> {
        val words = query.search.trim().split(Whitespace).filter { it.isNotEmpty() }
        val criteria = Criteria(query.filter)
        return sort(games.filter { it.matches(words) && criteria.matches(it) }, query.sort, locale)
    }

    /**
     * Sorts by [sort]; games without a value in the sorted field come last in both directions,
     * ties are broken by title (ascending) and id. Titles compare locale-aware and case-insensitive.
     */
    fun sort(games: List<Game>, sort: GameSort, locale: Locale = Locale.getDefault()): List<Game> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        val rows = games.map { Row(it, collator.getCollationKey(it.title)) }
        val byTitle = compareBy<Row> { it.titleKey }
        val byId = compareBy<Row> { it.game.id }
        val primary: Comparator<Row>? = when (sort.field) {
            GameSortField.TITLE -> null
            GameSortField.PLATFORM -> compareBy<Row> { it.game.platform.ordinal }.directed(sort.order)
            GameSortField.RELEASE_YEAR -> nullsLastBy(sort.order) { it.game.releaseYear }
            GameSortField.PURCHASE_DATE -> nullsLastBy(sort.order) { it.game.purchaseDate }
            GameSortField.PURCHASE_PRICE -> nullsLastBy(sort.order) { it.game.purchasePrice }
            GameSortField.ESTIMATED_VALUE -> nullsLastBy(sort.order) { it.game.estimatedValue }
            GameSortField.RATING -> nullsLastBy(sort.order) { it.game.rating }
            GameSortField.CREATED_AT -> compareBy<Row> { it.game.createdAt }.directed(sort.order)
            GameSortField.UPDATED_AT -> compareBy<Row> { it.game.updatedAt }.directed(sort.order)
        }
        val comparator = primary?.then(byTitle) ?: byTitle.directed(sort.order)
        return rows.sortedWith(comparator.then(byId)).map { it.game }
    }

    /** Every search word must be contained in at least one of the searchable texts. */
    private fun Game.matches(words: List<String>): Boolean {
        if (words.isEmpty()) return true
        val texts = listOfNotNull(title, edition, developer, publisher, genre, productCode, barcode, notes)
        return words.all { word -> texts.any { it.contains(word, ignoreCase = true) } }
    }

    /** [GameFilter] prepared once per query (trimmed texts). */
    private class Criteria(private val filter: GameFilter) {
        private val publisherPart = filter.publisher?.trim()?.ifEmpty { null }
        private val developerPart = filter.developer?.trim()?.ifEmpty { null }
        private val storageLocationPart = filter.storageLocation?.trim()?.ifEmpty { null }

        fun matches(game: Game): Boolean = with(filter) {
            platforms.allows(game.platform) &&
                statuses.allows(game.status) &&
                formats.allows(game.format) &&
                regions.allows(game.region) &&
                completeness.allows(game.completeness) &&
                conditions.allows(game.condition) &&
                playStatuses.allows(game.playStatus) &&
                (genres.isEmpty() || genres.any { game.genre.equals(it, ignoreCase = true) }) &&
                game.publisher.containsIgnoringCase(publisherPart) &&
                game.developer.containsIgnoringCase(developerPart) &&
                game.storageLocation.containsIgnoringCase(storageLocationPart) &&
                (!favoritesOnly || game.favorite) &&
                when (cover) {
                    CoverFilter.ANY -> true
                    CoverFilter.WITH_COVER -> game.coverImageUrl != null
                    CoverFilter.WITHOUT_COVER -> game.coverImageUrl == null
                } &&
                game.releaseYear.inRange(releaseYearFrom, releaseYearTo) &&
                game.purchaseDate.inRange(purchaseDateFrom, purchaseDateTo) &&
                game.purchasePrice.inRange(purchasePriceMin, purchasePriceMax) &&
                game.estimatedValue.inRange(estimatedValueMin, estimatedValueMax) &&
                game.rating.inRange(ratingMin, null)
        }

        private fun <T> Set<T>.allows(value: T?): Boolean = isEmpty() || value in this

        private fun String?.containsIgnoringCase(part: String?): Boolean =
            part == null || this?.contains(part, ignoreCase = true) == true

        /** Inclusive; a game without a value never matches a range. */
        private fun <T : Comparable<T>> T?.inRange(from: T?, to: T?): Boolean {
            if (from == null && to == null) return true
            if (this == null) return false
            return (from == null || this >= from) && (to == null || this <= to)
        }
    }

    private class Row(val game: Game, val titleKey: CollationKey)

    private fun <T> Comparator<T>.directed(order: SortOrder): Comparator<T> =
        if (order == SortOrder.ASC) this else reversed()

    private fun <V : Comparable<V>> nullsLastBy(order: SortOrder, selector: (Row) -> V?): Comparator<Row> {
        val values: Comparator<V> = if (order == SortOrder.ASC) naturalOrder() else reverseOrder()
        return compareBy(nullsLast(values), selector)
    }
}
