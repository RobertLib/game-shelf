package cz.gameshelf.app.ui.games.edit

import cz.gameshelf.app.R
import cz.gameshelf.app.data.sync.GameFields
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Completeness
import cz.gameshelf.app.domain.model.GameFormat
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.PlayStatus
import cz.gameshelf.app.ui.common.Formatters
import cz.gameshelf.app.ui.common.MAX_AMOUNT
import cz.gameshelf.app.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class GameFormValidatorTest {

    private val validForm = GameForm(title = "  Doom ", platform = Platform.PC)

    private fun errorsOf(form: GameForm, initial: GameForm? = null): Map<GameField, UiText> =
        (GameFormValidator.validate(form, initial) as? GameFormValidation.Invalid)?.errors.orEmpty()

    private fun requestOf(form: GameForm, initial: GameForm? = null) =
        (GameFormValidator.validate(form, initial) as GameFormValidation.Valid).request

    private fun tooLong(max: Int) = UiText.plural(R.plurals.validation_too_long, max)

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
    fun `every text field has the limit of the API`() {
        val limits = listOf<Triple<GameField, Int, (String) -> GameForm>>(
            Triple(GameField.TITLE, 200) { validForm.copy(title = it) },
            Triple(GameField.EDITION, 100) { validForm.copy(edition = it) },
            Triple(GameField.GENRE, 100) { validForm.copy(genre = it) },
            Triple(GameField.DEVELOPER, 100) { validForm.copy(developer = it) },
            Triple(GameField.PUBLISHER, 100) { validForm.copy(publisher = it) },
            Triple(GameField.PURCHASE_PLACE, 100) { validForm.copy(purchasePlace = it) },
            Triple(GameField.STORAGE_LOCATION, 100) { validForm.copy(storageLocation = it) },
            Triple(GameField.PRODUCT_CODE, 50) { validForm.copy(productCode = it) },
            Triple(GameField.NOTES, 5000) { validForm.copy(notes = it) },
        )

        limits.forEach { (field, max, form) ->
            assertTrue("$field", errorsOf(form(" " + "x".repeat(max) + " ")).isEmpty())
            assertEquals("$field", tooLong(max), errorsOf(form("x".repeat(max + 1)))[field])
        }
    }

    @Test
    fun `lengths are counted in code points`() {
        // 200 emoji are 400 UTF-16 chars, but 200 code points.
        assertEquals("😀".repeat(200), requestOf(validForm.copy(title = "😀".repeat(200))).title)
        assertEquals(tooLong(200), errorsOf(validForm.copy(title = "😀".repeat(201)))[GameField.TITLE])
        // A flag is two code points, a decomposed "é" too.
        assertTrue(errorsOf(validForm.copy(productCode = "🇨🇿".repeat(25))).isEmpty())
        assertEquals(tooLong(50), errorsOf(validForm.copy(productCode = "🇨🇿".repeat(25) + "x"))[GameField.PRODUCT_CODE])
        assertTrue(errorsOf(validForm.copy(edition = "é".repeat(50))).isEmpty())
        assertEquals(tooLong(100), errorsOf(validForm.copy(edition = "é".repeat(50) + "x"))[GameField.EDITION])
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
    fun `prices accept a decimal point or comma and grouping with at most two decimals`() {
        val request = requestOf(validForm.copy(purchasePrice = "1299.90", estimatedValue = "2 500,5"))
        assertEquals(BigDecimal("1299.9"), request.purchasePrice)
        assertEquals(BigDecimal("2500.5"), request.estimatedValue)

        assertEquals(BigDecimal("1000"), requestOf(validForm.copy(purchasePrice = "1,000")).purchasePrice)
        assertEquals(BigDecimal("12345"), requestOf(validForm.copy(purchasePrice = "12,345")).purchasePrice)
        assertEquals(BigDecimal("1234567.89"), requestOf(validForm.copy(purchasePrice = "1.234.567,89")).purchasePrice)
        assertEquals(BigDecimal("0"), requestOf(validForm.copy(purchasePrice = "0")).purchasePrice)
        listOf("1,5000", "1.23,45", "-5", "abc", "1e3").forEach {
            val errors = errorsOf(validForm.copy(purchasePrice = it))
            assertEquals(it, UiText(R.string.validation_price), errors[GameField.PURCHASE_PRICE])
        }
    }

    @Test
    fun `prices are at most 9 999 999 999,99`() {
        assertEquals(MAX_AMOUNT, requestOf(validForm.copy(estimatedValue = "9 999 999 999.99")).estimatedValue)

        val errors = errorsOf(validForm.copy(purchasePrice = "10 000 000 000", estimatedValue = "10000000000,5"))

        val tooLarge = UiText(R.string.validation_price_too_large, Formatters.number(MAX_AMOUNT))
        assertEquals(tooLarge, errors[GameField.PURCHASE_PRICE])
        assertEquals(tooLarge, errors[GameField.ESTIMATED_VALUE])
    }

    @Test
    fun `barcode must be 8 to 14 digits`() {
        assertEquals("12345678", requestOf(validForm.copy(barcode = "12345678")).barcode)
        assertEquals("12345678901234", requestOf(validForm.copy(barcode = "12345678901234")).barcode)
        listOf("1234567", "123456789012345", "1234abcd", "１２３４５６７８").forEach {
            assertEquals(it, UiText(R.string.validation_barcode), errorsOf(validForm.copy(barcode = it))[GameField.BARCODE])
        }
    }

    @Test
    fun `cover must match the cover URL pattern`() {
        val valid = listOf(
            "https://example.com/a.png",
            "HTTPS://Example.COM/A.png",
            "http://localhost/x.png",
            "https://nas/cover.jpg",
            "https://example.com:8443/a?b=c#d",
            "https://example.com?x=1",
            "https://img.example.co.uk/a_b/%C3%A9.jpg",
        )
        val invalid = listOf(
            "ftp://example.com/a.png",
            "example.com/a.png",
            "https://",
            "https://-example.com/a.png",
            "https://example.com./a.png",
            "https://exa mple.com/a.png",
            "https://example.com/a b.png",
            "https://example.com/é.png",
            "https://user:pw@example.com/a.png",
            "https://my_host/a.png",
            "https://[::1]/a.png",
            "https://example.com:123456/a.png",
        )

        valid.forEach { assertEquals(it, it, requestOf(validForm.copy(coverImageUrl = " $it ")).coverImageUrl) }
        invalid.forEach {
            assertEquals(it, UiText(R.string.validation_url), errorsOf(validForm.copy(coverImageUrl = it))[GameField.COVER_URL])
        }
    }

    @Test
    fun `cover is at most 2048 characters`() {
        val url = "https://example.com/" + "a".repeat(2028)
        assertEquals(url, requestOf(validForm.copy(coverImageUrl = url)).coverImageUrl)
        assertEquals(tooLong(2048), errorsOf(validForm.copy(coverImageUrl = url + "a"))[GameField.COVER_URL])
    }

    @Test
    fun `currency is any three letters, sent upper-case`() {
        assertEquals("EUR", requestOf(validForm.copy(currency = "eur")).currency)
        assertEquals("DEM", requestOf(validForm.copy(currency = " DEM ")).currency)
        assertEquals("SKK", requestOf(validForm.copy(currency = "sKk")).currency)
        listOf("", "EU", "EURO", "E1R", "ČZK").forEach {
            assertEquals(it, UiText(R.string.validation_currency), errorsOf(validForm.copy(currency = it))[GameField.CURRENCY])
        }
    }

    @Test
    fun `a new game is validated whole, including values unknown to this version`() {
        val errors = errorsOf(validForm.copy(status = CollectionStatus.UNKNOWN, playStatus = PlayStatus.UNKNOWN))

        assertEquals(UiText(R.string.validation_required), errors[GameField.STATUS])
        assertEquals(UiText(R.string.validation_required), errors[GameField.PLAY_STATUS])
    }

    @Test
    fun `values the user did not change are not validated`() {
        // As stored: values this version doesn't know and values the API's rules no longer allow.
        val initial = GameForm(
            title = "x".repeat(250),
            platform = Platform.N64,
            status = CollectionStatus.UNKNOWN,
            format = GameFormat.UNKNOWN,
            completeness = Completeness.UNKNOWN,
            coverImageUrl = "https://example.com/é.png",
            currency = "€",
            releaseYear = "1940",
        )
        val edited = initial.copy(notes = "Signed by the developers")

        val request = requestOf(edited, initial)

        assertEquals(CollectionStatus.UNKNOWN, request.status)
        // Built the same way as the request the edit started from, so only the edited field differs.
        assertEquals(setOf("notes"), GameFields.diff(requestOf(initial, initial), request))
    }

    @Test
    fun `changed values are validated`() {
        val initial = GameForm(title = "Doom", platform = Platform.PC, status = CollectionStatus.UNKNOWN)

        val errors = errorsOf(initial.copy(title = "", coverImageUrl = "nas/cover.jpg", currency = "EU"), initial)

        assertEquals(setOf(GameField.TITLE, GameField.COVER_URL, GameField.CURRENCY), errors.keys)
    }
}
