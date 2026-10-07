package cz.gameshelf.app.ui.games.filter

import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Completeness
import cz.gameshelf.app.domain.model.Condition
import cz.gameshelf.app.domain.model.CoverFilter
import cz.gameshelf.app.domain.model.GameFilter
import cz.gameshelf.app.domain.model.GameFormat
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.PlayStatus
import cz.gameshelf.app.domain.model.Region
import cz.gameshelf.app.ui.common.Formatters
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.hasAtMostTwoDecimals
import cz.gameshelf.app.ui.common.parseDecimalInput
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Editable copy of [GameFilter] shown in the filter sheet. Numeric bounds are kept as typed so that
 * partial input survives; [toFilter] validates and converts them when the draft is applied.
 */
data class FilterDraft(
    val platforms: Set<Platform> = emptySet(),
    val statuses: Set<CollectionStatus> = emptySet(),
    val formats: Set<GameFormat> = emptySet(),
    val regions: Set<Region> = emptySet(),
    val completeness: Set<Completeness> = emptySet(),
    val conditions: Set<Condition> = emptySet(),
    val playStatuses: Set<PlayStatus> = emptySet(),
    val genres: Set<String> = emptySet(),
    val publisher: String = "",
    val developer: String = "",
    val storageLocation: String = "",
    val favoritesOnly: Boolean = false,
    val cover: CoverFilter = CoverFilter.ANY,
    val releaseYearFrom: String = "",
    val releaseYearTo: String = "",
    val purchasePriceMin: String = "",
    val purchasePriceMax: String = "",
    val estimatedValueMin: String = "",
    val estimatedValueMax: String = "",
    val purchaseDateFrom: LocalDate? = null,
    val purchaseDateTo: LocalDate? = null,
    val ratingMin: Int? = null,
) {
    val errors: FilterDraftErrors
        get() = FilterDraftErrors(
            releaseYear = yearRangeError(releaseYearFrom, releaseYearTo),
            purchasePrice = amountRangeError(purchasePriceMin, purchasePriceMax),
            estimatedValue = amountRangeError(estimatedValueMin, estimatedValueMax),
            purchaseDate = if (purchaseDateFrom != null && purchaseDateTo != null && purchaseDateFrom > purchaseDateTo) {
                UiText(R.string.validation_range)
            } else {
                null
            },
        )

    /** The validated filter, or `null` while [errors] contains anything. */
    fun toFilter(): GameFilter? {
        if (errors.hasAny) return null
        return GameFilter(
            platforms = platforms,
            statuses = statuses,
            formats = formats,
            regions = regions,
            completeness = completeness,
            conditions = conditions,
            playStatuses = playStatuses,
            genres = genres,
            publisher = publisher.trim().ifEmpty { null },
            developer = developer.trim().ifEmpty { null },
            storageLocation = storageLocation.trim().ifEmpty { null },
            favoritesOnly = favoritesOnly,
            cover = cover,
            releaseYearFrom = releaseYearFrom.trim().toIntOrNull(),
            releaseYearTo = releaseYearTo.trim().toIntOrNull(),
            purchasePriceMin = parseDecimalInput(purchasePriceMin),
            purchasePriceMax = parseDecimalInput(purchasePriceMax),
            estimatedValueMin = parseDecimalInput(estimatedValueMin),
            estimatedValueMax = parseDecimalInput(estimatedValueMax),
            purchaseDateFrom = purchaseDateFrom,
            purchaseDateTo = purchaseDateTo,
            ratingMin = ratingMin,
        )
    }

    companion object {
        const val MIN_YEAR = 1950
        const val MAX_YEAR = 2100

        fun from(filter: GameFilter) = FilterDraft(
            platforms = filter.platforms,
            statuses = filter.statuses,
            formats = filter.formats,
            regions = filter.regions,
            completeness = filter.completeness,
            conditions = filter.conditions,
            playStatuses = filter.playStatuses,
            genres = filter.genres,
            publisher = filter.publisher.orEmpty(),
            developer = filter.developer.orEmpty(),
            storageLocation = filter.storageLocation.orEmpty(),
            favoritesOnly = filter.favoritesOnly,
            cover = filter.cover,
            releaseYearFrom = filter.releaseYearFrom?.toString().orEmpty(),
            releaseYearTo = filter.releaseYearTo?.toString().orEmpty(),
            purchasePriceMin = filter.purchasePriceMin?.let(Formatters::decimalInput).orEmpty(),
            purchasePriceMax = filter.purchasePriceMax?.let(Formatters::decimalInput).orEmpty(),
            estimatedValueMin = filter.estimatedValueMin?.let(Formatters::decimalInput).orEmpty(),
            estimatedValueMax = filter.estimatedValueMax?.let(Formatters::decimalInput).orEmpty(),
            purchaseDateFrom = filter.purchaseDateFrom,
            purchaseDateTo = filter.purchaseDateTo,
            ratingMin = filter.ratingMin,
        )

        private fun yearRangeError(from: String, to: String): UiText? {
            val fromYear = parseYear(from)
            val toYear = parseYear(to)
            return when {
                fromYear == INVALID || toYear == INVALID -> UiText(R.string.validation_year)
                fromYear != null && toYear != null && fromYear > toYear -> UiText(R.string.validation_range)
                else -> null
            }
        }

        private fun amountRangeError(min: String, max: String): UiText? {
            val minAmount = parseAmount(min)
            val maxAmount = parseAmount(max)
            return when {
                minAmount == INVALID_AMOUNT || maxAmount == INVALID_AMOUNT -> UiText(R.string.validation_price)
                minAmount != null && maxAmount != null && minAmount > maxAmount -> UiText(R.string.validation_range)
                else -> null
            }
        }

        private const val INVALID = -1
        private val INVALID_AMOUNT = BigDecimal(-1)

        /** `null` = not set, [INVALID] = not a year in range. */
        private fun parseYear(input: String): Int? {
            if (input.isBlank()) return null
            val year = input.trim().toIntOrNull() ?: return INVALID
            return if (year in MIN_YEAR..MAX_YEAR) year else INVALID
        }

        private fun parseAmount(input: String): BigDecimal? {
            if (input.isBlank()) return null
            val amount = parseDecimalInput(input) ?: return INVALID_AMOUNT
            return if (amount.hasAtMostTwoDecimals()) amount else INVALID_AMOUNT
        }
    }
}

data class FilterDraftErrors(
    val releaseYear: UiText? = null,
    val purchasePrice: UiText? = null,
    val estimatedValue: UiText? = null,
    val purchaseDate: UiText? = null,
) {
    val hasAny: Boolean
        get() = releaseYear != null || purchasePrice != null || estimatedValue != null || purchaseDate != null
}
