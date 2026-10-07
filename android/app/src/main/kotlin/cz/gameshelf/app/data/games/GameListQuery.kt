package cz.gameshelf.app.data.games

import cz.gameshelf.app.domain.model.CoverFilter
import cz.gameshelf.app.domain.model.GameQuery
import cz.gameshelf.app.domain.serialization.ApiEnum
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

/** Query parameters of `GET games` in wire format; empty lists and `null`s are not sent. */
data class GameListQuery(
    val q: String? = null,
    val platform: List<String> = emptyList(),
    val status: List<String> = emptyList(),
    val format: List<String> = emptyList(),
    val region: List<String> = emptyList(),
    val completeness: List<String> = emptyList(),
    val condition: List<String> = emptyList(),
    val playStatus: List<String> = emptyList(),
    val genre: List<String> = emptyList(),
    val publisher: String? = null,
    val developer: String? = null,
    val storageLocation: String? = null,
    val favorite: Boolean? = null,
    val hasCover: Boolean? = null,
    val releaseYearFrom: Int? = null,
    val releaseYearTo: Int? = null,
    val purchaseDateFrom: String? = null,
    val purchaseDateTo: String? = null,
    val purchasePriceMin: String? = null,
    val purchasePriceMax: String? = null,
    val estimatedValueMin: String? = null,
    val estimatedValueMax: String? = null,
    val ratingMin: Int? = null,
    val ratingMax: Int? = null,
    val sort: String? = null,
    val order: String? = null,
    val page: Int = 1,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 25

        fun from(query: GameQuery, page: Int, pageSize: Int = DEFAULT_PAGE_SIZE): GameListQuery {
            val filter = query.filter
            return GameListQuery(
                q = query.search.trim().ifEmpty { null },
                platform = filter.platforms.toWire(),
                status = filter.statuses.toWire(),
                format = filter.formats.toWire(),
                region = filter.regions.toWire(),
                completeness = filter.completeness.toWire(),
                condition = filter.conditions.toWire(),
                playStatus = filter.playStatuses.toWire(),
                genre = filter.genres.sorted(),
                publisher = filter.publisher.trimToNull(),
                developer = filter.developer.trimToNull(),
                storageLocation = filter.storageLocation.trimToNull(),
                favorite = if (filter.favoritesOnly) true else null,
                hasCover = when (filter.cover) {
                    CoverFilter.ANY -> null
                    CoverFilter.WITH_COVER -> true
                    CoverFilter.WITHOUT_COVER -> false
                },
                releaseYearFrom = filter.releaseYearFrom,
                releaseYearTo = filter.releaseYearTo,
                purchaseDateFrom = filter.purchaseDateFrom?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                purchaseDateTo = filter.purchaseDateTo?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                purchasePriceMin = filter.purchasePriceMin.toWire(),
                purchasePriceMax = filter.purchasePriceMax.toWire(),
                estimatedValueMin = filter.estimatedValueMin.toWire(),
                estimatedValueMax = filter.estimatedValueMax.toWire(),
                ratingMin = filter.ratingMin,
                sort = query.sort.field.apiValue,
                order = query.sort.order.apiValue,
                page = page,
                pageSize = pageSize,
            )
        }

        private fun <T> Set<T>.toWire(): List<String> where T : Enum<T>, T : ApiEnum =
            sortedBy { it.ordinal }.map { it.apiValue }

        private fun BigDecimal?.toWire(): String? = this?.stripTrailingZeros()?.toPlainString()

        private fun String?.trimToNull(): String? = this?.trim()?.ifEmpty { null }
    }
}
