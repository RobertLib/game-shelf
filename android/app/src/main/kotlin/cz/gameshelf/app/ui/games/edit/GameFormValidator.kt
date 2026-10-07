package cz.gameshelf.app.ui.games.edit

import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Completeness
import cz.gameshelf.app.domain.model.Condition
import cz.gameshelf.app.domain.model.GameFormat
import cz.gameshelf.app.domain.model.PlayStatus
import cz.gameshelf.app.domain.model.SaveGameRequest
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.hasAtMostTwoDecimals
import cz.gameshelf.app.ui.common.parseDecimalInput
import java.math.BigDecimal
import java.net.URI

sealed interface GameFormValidation {
    data class Valid(val request: SaveGameRequest) : GameFormValidation
    data class Invalid(val errors: Map<GameField, UiText>) : GameFormValidation
}

/** Same rules as the API's `SaveGameRequest`; produces the request when the form is valid. */
object GameFormValidator {
    private const val TITLE_MAX = 200
    private const val TEXT_MAX = 100
    private const val PRODUCT_CODE_MAX = 50
    private const val NOTES_MAX = 5000
    private const val URL_MAX = 2048
    private val YEARS = 1950..2100
    private val QUANTITIES = 1..999
    private val RATINGS = 1..10
    private val BarcodePattern = Regex("^[0-9]{8,14}$")
    private val CurrencyPattern = Regex("^[A-Za-z]{3}$")

    fun validate(form: GameForm): GameFormValidation {
        val errors = mutableMapOf<GameField, UiText>()
        fun error(field: GameField, text: UiText) = errors.putIfAbsent(field, text)

        val title = form.title.trim()
        if (title.isEmpty()) error(GameField.TITLE, UiText(R.string.validation_required))
        if (title.length > TITLE_MAX) error(GameField.TITLE, tooLong(TITLE_MAX))
        if (form.platform == null) error(GameField.PLATFORM, UiText(R.string.validation_platform_required))

        val edition = optionalText(form.edition, TEXT_MAX, GameField.EDITION, errors)
        val genre = optionalText(form.genre, TEXT_MAX, GameField.GENRE, errors)
        val developer = optionalText(form.developer, TEXT_MAX, GameField.DEVELOPER, errors)
        val publisher = optionalText(form.publisher, TEXT_MAX, GameField.PUBLISHER, errors)
        val productCode = optionalText(form.productCode, PRODUCT_CODE_MAX, GameField.PRODUCT_CODE, errors)
        val storageLocation = optionalText(form.storageLocation, TEXT_MAX, GameField.STORAGE_LOCATION, errors)
        val purchasePlace = optionalText(form.purchasePlace, TEXT_MAX, GameField.PURCHASE_PLACE, errors)
        val notes = optionalText(form.notes, NOTES_MAX, GameField.NOTES, errors)

        val releaseYear = form.releaseYear.trim().ifEmpty { null }?.let { raw ->
            raw.toIntOrNull()?.takeIf { it in YEARS }
                ?: run { error(GameField.RELEASE_YEAR, UiText(R.string.validation_year)); null }
        }

        val coverImageUrl = form.coverImageUrl.trim().ifEmpty { null }?.also { url ->
            when {
                url.length > URL_MAX -> error(GameField.COVER_URL, tooLong(URL_MAX))
                !isHttpUrl(url) -> error(GameField.COVER_URL, UiText(R.string.validation_url))
            }
        }

        val barcode = form.barcode.trim().ifEmpty { null }?.also {
            if (!BarcodePattern.matches(it)) error(GameField.BARCODE, UiText(R.string.validation_barcode))
        }

        val quantity = form.quantity.trim().toIntOrNull()?.takeIf { it in QUANTITIES }
        if (quantity == null) error(GameField.QUANTITY, UiText(R.string.validation_quantity))

        val purchasePrice = amount(form.purchasePrice, GameField.PURCHASE_PRICE, errors)
        val estimatedValue = amount(form.estimatedValue, GameField.ESTIMATED_VALUE, errors)

        val currency = form.currency.trim()
        if (!CurrencyPattern.matches(currency)) error(GameField.CURRENCY, UiText(R.string.validation_currency))

        if (form.rating != null && form.rating !in RATINGS) error(GameField.RATING, UiText(R.string.validation_required))

        // Values from a newer API that this version cannot send back.
        if (form.status == CollectionStatus.UNKNOWN) error(GameField.STATUS, UiText(R.string.validation_required))
        if (form.format == GameFormat.UNKNOWN) error(GameField.FORMAT, UiText(R.string.validation_required))
        if (form.completeness == Completeness.UNKNOWN) error(GameField.COMPLETENESS, UiText(R.string.validation_required))
        if (form.condition == Condition.UNKNOWN) error(GameField.CONDITION, UiText(R.string.validation_required))
        if (form.playStatus == PlayStatus.UNKNOWN) error(GameField.PLAY_STATUS, UiText(R.string.validation_required))

        if (errors.isNotEmpty()) return GameFormValidation.Invalid(errors)

        return GameFormValidation.Valid(
            SaveGameRequest(
                title = title,
                platform = checkNotNull(form.platform),
                status = form.status,
                format = form.format,
                region = form.region,
                edition = edition,
                completeness = form.completeness,
                condition = form.condition,
                playStatus = form.playStatus,
                genre = genre,
                developer = developer,
                publisher = publisher,
                releaseYear = releaseYear,
                barcode = barcode,
                productCode = productCode,
                quantity = checkNotNull(quantity),
                purchasePrice = purchasePrice,
                purchaseDate = form.purchaseDate,
                purchasePlace = purchasePlace,
                estimatedValue = estimatedValue,
                currency = currency.uppercase(),
                storageLocation = storageLocation,
                rating = form.rating,
                favorite = form.favorite,
                coverImageUrl = coverImageUrl,
                notes = notes,
            ),
        )
    }

    private fun optionalText(
        value: String,
        maxLength: Int,
        field: GameField,
        errors: MutableMap<GameField, UiText>,
    ): String? {
        val trimmed = value.trim().ifEmpty { return null }
        if (trimmed.length > maxLength) errors.putIfAbsent(field, tooLong(maxLength))
        return trimmed
    }

    private fun amount(value: String, field: GameField, errors: MutableMap<GameField, UiText>): BigDecimal? {
        if (value.isBlank()) return null
        val amount = parseDecimalInput(value)?.takeIf { it.hasAtMostTwoDecimals() }
        if (amount == null) errors.putIfAbsent(field, UiText(R.string.validation_price))
        return amount?.stripTrailingZeros()?.let { if (it.scale() < 0) it.setScale(0) else it }
    }

    private fun isHttpUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun tooLong(max: Int) = UiText.plural(R.plurals.validation_too_long, max)
}
