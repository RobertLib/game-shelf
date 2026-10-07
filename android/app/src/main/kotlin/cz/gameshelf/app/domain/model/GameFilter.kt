package cz.gameshelf.app.domain.model

import java.math.BigDecimal
import java.time.LocalDate

enum class CoverFilter { ANY, WITH_COVER, WITHOUT_COVER }

/** Filters of the collection list; an empty set / `null` means "not filtered by this attribute". */
data class GameFilter(
    val platforms: Set<Platform> = emptySet(),
    val statuses: Set<CollectionStatus> = emptySet(),
    val formats: Set<GameFormat> = emptySet(),
    val regions: Set<Region> = emptySet(),
    val completeness: Set<Completeness> = emptySet(),
    val conditions: Set<Condition> = emptySet(),
    val playStatuses: Set<PlayStatus> = emptySet(),
    val genres: Set<String> = emptySet(),
    val publisher: String? = null,
    val developer: String? = null,
    val storageLocation: String? = null,
    val favoritesOnly: Boolean = false,
    val cover: CoverFilter = CoverFilter.ANY,
    val releaseYearFrom: Int? = null,
    val releaseYearTo: Int? = null,
    val purchasePriceMin: BigDecimal? = null,
    val purchasePriceMax: BigDecimal? = null,
    val estimatedValueMin: BigDecimal? = null,
    val estimatedValueMax: BigDecimal? = null,
    val purchaseDateFrom: LocalDate? = null,
    val purchaseDateTo: LocalDate? = null,
    val ratingMin: Int? = null,
) {
    val isEmpty: Boolean get() = this == EMPTY

    /** One entry per removable chip; its size is the badge count on the filter button. */
    val activeFilters: List<ActiveFilter>
        get() = buildList {
            platforms.sortedBy { it.ordinal }.forEach { add(ActiveFilter.PlatformValue(it)) }
            statuses.sortedBy { it.ordinal }.forEach { add(ActiveFilter.StatusValue(it)) }
            formats.sortedBy { it.ordinal }.forEach { add(ActiveFilter.FormatValue(it)) }
            regions.sortedBy { it.ordinal }.forEach { add(ActiveFilter.RegionValue(it)) }
            completeness.sortedBy { it.ordinal }.forEach { add(ActiveFilter.CompletenessValue(it)) }
            conditions.sortedBy { it.ordinal }.forEach { add(ActiveFilter.ConditionValue(it)) }
            playStatuses.sortedBy { it.ordinal }.forEach { add(ActiveFilter.PlayStatusValue(it)) }
            genres.sorted().forEach { add(ActiveFilter.Genre(it)) }
            publisher?.let { add(ActiveFilter.Publisher(it)) }
            developer?.let { add(ActiveFilter.Developer(it)) }
            storageLocation?.let { add(ActiveFilter.StorageLocation(it)) }
            if (favoritesOnly) add(ActiveFilter.FavoritesOnly)
            if (cover != CoverFilter.ANY) add(ActiveFilter.Cover(cover))
            if (releaseYearFrom != null || releaseYearTo != null) {
                add(ActiveFilter.ReleaseYear(releaseYearFrom, releaseYearTo))
            }
            if (purchasePriceMin != null || purchasePriceMax != null) {
                add(ActiveFilter.PurchasePrice(purchasePriceMin, purchasePriceMax))
            }
            if (estimatedValueMin != null || estimatedValueMax != null) {
                add(ActiveFilter.EstimatedValue(estimatedValueMin, estimatedValueMax))
            }
            if (purchaseDateFrom != null || purchaseDateTo != null) {
                add(ActiveFilter.PurchaseDate(purchaseDateFrom, purchaseDateTo))
            }
            ratingMin?.let { add(ActiveFilter.RatingMin(it)) }
        }

    fun without(filter: ActiveFilter): GameFilter = when (filter) {
        is ActiveFilter.PlatformValue -> copy(platforms = platforms - filter.platform)
        is ActiveFilter.StatusValue -> copy(statuses = statuses - filter.status)
        is ActiveFilter.FormatValue -> copy(formats = formats - filter.format)
        is ActiveFilter.RegionValue -> copy(regions = regions - filter.region)
        is ActiveFilter.CompletenessValue -> copy(completeness = completeness - filter.completeness)
        is ActiveFilter.ConditionValue -> copy(conditions = conditions - filter.condition)
        is ActiveFilter.PlayStatusValue -> copy(playStatuses = playStatuses - filter.playStatus)
        is ActiveFilter.Genre -> copy(genres = genres - filter.genre)
        is ActiveFilter.Publisher -> copy(publisher = null)
        is ActiveFilter.Developer -> copy(developer = null)
        is ActiveFilter.StorageLocation -> copy(storageLocation = null)
        ActiveFilter.FavoritesOnly -> copy(favoritesOnly = false)
        is ActiveFilter.Cover -> copy(cover = CoverFilter.ANY)
        is ActiveFilter.ReleaseYear -> copy(releaseYearFrom = null, releaseYearTo = null)
        is ActiveFilter.PurchasePrice -> copy(purchasePriceMin = null, purchasePriceMax = null)
        is ActiveFilter.EstimatedValue -> copy(estimatedValueMin = null, estimatedValueMax = null)
        is ActiveFilter.PurchaseDate -> copy(purchaseDateFrom = null, purchaseDateTo = null)
        is ActiveFilter.RatingMin -> copy(ratingMin = null)
    }

    companion object {
        val EMPTY = GameFilter()
    }
}

sealed interface ActiveFilter {
    data class PlatformValue(val platform: Platform) : ActiveFilter
    data class StatusValue(val status: CollectionStatus) : ActiveFilter
    data class FormatValue(val format: GameFormat) : ActiveFilter
    data class RegionValue(val region: Region) : ActiveFilter
    data class CompletenessValue(val completeness: Completeness) : ActiveFilter
    data class ConditionValue(val condition: Condition) : ActiveFilter
    data class PlayStatusValue(val playStatus: PlayStatus) : ActiveFilter
    data class Genre(val genre: String) : ActiveFilter
    data class Publisher(val value: String) : ActiveFilter
    data class Developer(val value: String) : ActiveFilter
    data class StorageLocation(val value: String) : ActiveFilter
    data object FavoritesOnly : ActiveFilter
    data class Cover(val cover: CoverFilter) : ActiveFilter
    data class ReleaseYear(val from: Int?, val to: Int?) : ActiveFilter
    data class PurchasePrice(val min: BigDecimal?, val max: BigDecimal?) : ActiveFilter
    data class EstimatedValue(val min: BigDecimal?, val max: BigDecimal?) : ActiveFilter
    data class PurchaseDate(val from: LocalDate?, val to: LocalDate?) : ActiveFilter
    data class RatingMin(val min: Int) : ActiveFilter
}

data class GameSort(
    val field: GameSortField = GameSortField.TITLE,
    val order: SortOrder = SortOrder.ASC,
)
