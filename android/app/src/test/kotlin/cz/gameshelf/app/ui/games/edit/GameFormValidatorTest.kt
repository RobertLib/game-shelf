package cz.gameshelf.app.ui.games.edit

import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.GameFormat
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class GameFormValidatorTest {

    private val validForm = GameForm(title = "  Doom ", platform = Platform.PC)

    private fun errorsOf(form: GameForm): Map<GameField, UiText> =
        (GameFormValidator.validate(form) as? GameFormValidation.Invalid)?.errors.orEmpty()

    private fun requestOf(form: GameForm) = (GameFormValidator.validate(form) as GameFormValidation.Valid).request

    @Test
    fun `title and platform are required`() {
        val errors = errorsOf(GameForm())

        assertEquals(UiText(R.string.validation_required), errors[GameField.TITLE])
        assertEquals(UiText(R.string.validation_platform_required), errors[GameField.PLATFORM])
        assertEquals(setOf(GameField.TITLE, GameField.PLATFORM), errors.keys)
    }

    @Test
    fun `minimal valid form produces a request with API defaults`() {
        val request = requestOf(validForm)

        assertEquals("Doom", request.title)
        assertEquals(Platform.PC, request.platform)
        assertEquals(CollectionStatus.OWNED, request.status)
        assertEquals(GameFormat.PHYSICAL, request.format)
        assertEquals(1, request.quantity)
        assertEquals("CZK", request.currency)
        assertNull(request.edition)
        assertNull(request.purchasePrice)
        assertNull(request.releaseYear)
    }

    @Test
    fun `text length limits`() {
        fun tooLong(max: Int) = UiText.plural(R.plurals.validation_too_long, max)

        assertEquals(tooLong(200), errorsOf(validForm.copy(title = "x".repeat(201)))[GameField.TITLE])
        assertEquals(tooLong(100), errorsOf(validForm.copy(edition = "x".repeat(101)))[GameField.EDITION])
        assertEquals(tooLong(50), errorsOf(validForm.copy(productCode = "x".repeat(51)))[GameField.PRODUCT_CODE])
        assertTrue(errorsOf(validForm.copy(title = "x".repeat(200), notes = "y".repeat(5000))).isEmpty())
    }

    @Test
    fun `release year must be between 1950 and 2100`() {
        assertEquals(1950, requestOf(validForm.copy(releaseYear = "1950")).releaseYear)
        assertEquals(2100, requestOf(validForm.copy(releaseYear = "2100")).releaseYear)
        listOf("1949", "2101", "19x8").forEach {
            assertEquals(it, UiText(R.string.validation_year), errorsOf(validForm.copy(releaseYear = it))[GameField.RELEASE_YEAR])
        }
    }

    @Test
    fun `quantity must be 1 to 999`() {
        assertEquals(999, requestOf(validForm.copy(quantity = "999")).quantity)
        listOf("0", "1000", "", "1.5").forEach {
            assertEquals(it, UiText(R.string.validation_quantity), errorsOf(validForm.copy(quantity = it))[GameField.QUANTITY])
        }
    }

    @Test
    fun `prices accept a decimal point or comma with at most two decimals`() {
        val request = requestOf(validForm.copy(purchasePrice = "1299.90", estimatedValue = "2 500,5"))

        assertEquals(BigDecimal("1299.9"), request.purchasePrice)
        assertEquals(BigDecimal("2500.5"), request.estimatedValue)
        listOf("12,345", "-5", "abc", "1e3").forEach {
            val errors = errorsOf(validForm.copy(purchasePrice = it))
            assertEquals(it, UiText(R.string.validation_price), errors[GameField.PURCHASE_PRICE])
        }
    }

    @Test
    fun `barcode must be 8 to 14 digits`() {
        assertEquals("12345678", requestOf(validForm.copy(barcode = "12345678")).barcode)
        assertEquals("12345678901234", requestOf(validForm.copy(barcode = "12345678901234")).barcode)
        listOf("1234567", "123456789012345", "1234abcd").forEach {
            assertEquals(it, UiText(R.string.validation_barcode), errorsOf(validForm.copy(barcode = it))[GameField.BARCODE])
        }
    }

    @Test
    fun `cover must be an http or https url`() {
        val request = requestOf(validForm.copy(coverImageUrl = " https://example.com/a.jpg "))
        assertEquals("https://example.com/a.jpg", request.coverImageUrl)
        listOf("ftp://example.com/a.jpg", "example.com/a.jpg", "https://").forEach {
            assertEquals(it, UiText(R.string.validation_url), errorsOf(validForm.copy(coverImageUrl = it))[GameField.COVER_URL])
        }
    }

    @Test
    fun `currency is three letters and sent upper-case`() {
        assertEquals("EUR", requestOf(validForm.copy(currency = "eur")).currency)
        listOf("", "EU", "E1R").forEach {
            assertEquals(it, UiText(R.string.validation_currency), errorsOf(validForm.copy(currency = it))[GameField.CURRENCY])
        }
    }

    @Test
    fun `values unknown to this app version cannot be saved`() {
        val errors = errorsOf(validForm.copy(status = CollectionStatus.UNKNOWN))

        assertEquals(UiText(R.string.validation_required), errors[GameField.STATUS])
    }
}
