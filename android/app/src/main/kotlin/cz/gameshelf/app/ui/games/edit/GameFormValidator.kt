package cz.gameshelf.app.ui.games.edit

import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Completeness
import cz.gameshelf.app.domain.model.Condition
import cz.gameshelf.app.domain.model.GameFormat
import cz.gameshelf.app.domain.model.PlayStatus
import cz.gameshelf.app.domain.model.SaveGameRequest
import cz.gameshelf.app.ui.common.Formatters
import cz.gameshelf.app.ui.common.MAX_AMOUNT
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.codePointLength
import cz.gameshelf.app.ui.common.hasAtMostTwoDecimals
import cz.gameshelf.app.ui.common.parseDecimalInput
import java.math.BigDecimal

sealed interface GameFormValidation {
    data class Valid(val request: SaveGameRequest) : GameFormValidation
    data class Invalid(val errors: Map<GameField, UiText>) : GameFormValidation
}

/**
 * Exactly the rules of the API's `SaveGameRequest` (mobile-spec.md, "Validation"), so that nothing the form
 * lets through is rejected – and undone – by the sync later. Produces the request when the form is valid.
 */
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

    /** The cover URL pattern of mobile-spec.md, identical on all platforms. */
    private val CoverUrlPattern =
        Regex("""^[Hh][Tt][Tt][Pp][Ss]?://[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?(:[0-9]{1,5})?([/?#][!-~]*)?$""")

    /**
     * [initial] is the form an edit started from. Values the user did not change are not validated: only the
     * changed fields are saved and sent, so a value that came from the server – e.g. one this app version
     * doesn't know – never blocks saving an edit of another field. A new game (no [initial]) is sent whole,
     * so every field is validated.
     */
    fun validate(form: GameForm, initial: GameForm? = null): GameFormValidation {
        val errors = mutableMapOf<GameField, UiText>()
        fun error(field: GameField, text: UiText) = errors.putIfAbsent(field, text)

        /** Reports what [problem] finds wrong with [field] unless its [value] is still the initial one. */
        fun check(field: GameField, value: (GameForm) -> Any?, problem: () -> UiText?) {
            if (initial != null && value(form) == value(initial)) return
            problem()?.let { error(field, it) }
        }

        fun optionalText(field: GameField, value: (GameForm) -> String, maxLength: Int): String? {
            val trimmed = value(form).trim().ifEmpty { null }
            check(field, value) { tooLong(maxLength).takeIf { trimmed != null && trimmed.codePointLength() > maxLength } }
            return trimmed
        }

        fun amount(field: GameField, value: (GameForm) -> String): BigDecimal? {
            val input = value(form)
            val amount = if (input.isBlank()) null else parseDecimalInput(input)
            check(field, value) {
                when {
                    input.isBlank() -> null
                    amount == null || !amount.hasAtMostTwoDecimals() -> UiText(R.string.validation_price)
                    amount > MAX_AMOUNT -> UiText(R.string.validation_price_too_large, Formatters.number(MAX_AMOUNT))
                    else -> null
                }
            }
            return amount?.stripTrailingZeros()?.let { if (it.scale() < 0) it.setScale(0) else it }
        }

        val title = form.title.trim()
        check(GameField.TITLE, GameForm::title) {
            when {
                title.isEmpty() -> UiText(R.string.validation_required)
                title.codePointLength() > TITLE_MAX -> tooLong(TITLE_MAX)
                else -> null
            }
        }
        // Without it there is no request at all, changed or not.
        if (form.platform == null) error(GameField.PLATFORM, UiText(R.string.validation_platform_required))

        val edition = optionalText(GameField.EDITION, GameForm::edition, TEXT_MAX)
        val genre = optionalText(GameField.GENRE, GameForm::genre, TEXT_MAX)
        val developer = optionalText(GameField.DEVELOPER, GameForm::developer, TEXT_MAX)
        val publisher = optionalText(GameField.PUBLISHER, GameForm::publisher, TEXT_MAX)
        val productCode = optionalText(GameField.PRODUCT_CODE, GameForm::productCode, PRODUCT_CODE_MAX)
        val storageLocation = optionalText(GameField.STORAGE_LOCATION, GameForm::storageLocation, TEXT_MAX)
        val purchasePlace = optionalText(GameField.PURCHASE_PLACE, GameForm::purchasePlace, TEXT_MAX)
        val notes = optionalText(GameField.NOTES, GameForm::notes, NOTES_MAX)

        val releaseYear = form.releaseYear.trim().toIntOrNull()
        check(GameField.RELEASE_YEAR, GameForm::releaseYear) {
            val invalid = form.releaseYear.isNotBlank() && (releaseYear == null || releaseYear !in YEARS)
            UiText(R.string.validation_year).takeIf { invalid }
        }

        val coverImageUrl = form.coverImageUrl.trim().ifEmpty { null }
        check(GameField.COVER_URL, GameForm::coverImageUrl) {
            when {
                coverImageUrl == null -> null
                coverImageUrl.codePointLength() > URL_MAX -> tooLong(URL_MAX)
                !CoverUrlPattern.matches(coverImageUrl) -> UiText(R.string.validation_url)
                else -> null
            }
        }

        val barcode = form.barcode.trim().ifEmpty { null }
        check(GameField.BARCODE, GameForm::barcode) {
            UiText(R.string.validation_barcode).takeIf { barcode != null && !BarcodePattern.matches(barcode) }
        }

        val quantity = form.quantity.trim().toIntOrNull()
        check(GameField.QUANTITY, GameForm::quantity) {
            UiText(R.string.validation_quantity).takeIf { quantity == null || quantity !in QUANTITIES }
        }
        // Without it there is no request at all, changed or not.
        if (quantity == null) error(GameField.QUANTITY, UiText(R.string.validation_quantity))

        val purchasePrice = amount(GameField.PURCHASE_PRICE, GameForm::purchasePrice)
        val estimatedValue = amount(GameField.ESTIMATED_VALUE, GameForm::estimatedValue)

        val currency = form.currency.trim()
        check(GameField.CURRENCY, GameForm::currency) {
            UiText(R.string.validation_currency).takeIf { !CurrencyPattern.matches(currency) }
        }

        check(GameField.RATING, GameForm::rating) {
            UiText(R.string.validation_required).takeIf { form.rating != null && form.rating !in RATINGS }
        }

        // Values from a newer API that this version cannot send back; the pickers never offer them.
        val unknownValue = UiText(R.string.validation_required)
        check(GameField.STATUS, GameForm::status) { unknownValue.takeIf { form.status == CollectionStatus.UNKNOWN } }
        check(GameField.FORMAT, GameForm::format) { unknownValue.takeIf { form.format == GameFormat.UNKNOWN } }
        check(GameField.COMPLETENESS, GameForm::completeness) {
            unknownValue.takeIf { form.completeness == Completeness.UNKNOWN }
        }
        check(GameField.CONDITION, GameForm::condition) { unknownValue.takeIf { form.condition == Condition.UNKNOWN } }
        check(GameField.PLAY_STATUS, GameForm::playStatus) {
            unknownValue.takeIf { form.playStatus == PlayStatus.UNKNOWN }
        }

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

    private fun tooLong(max: Int) = UiText.plural(R.plurals.validation_too_long, max)
}
