package cz.gameshelf.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

class FormattersTest {

    /** Number formatting may use (narrow) no-break spaces depending on the locale data. */
    private fun String.normalizeSpaces() = replace(' ', ' ').replace(' ', ' ')

    @Test
    fun `prices use the game's currency and the device locale layout`() {
        assertEquals("€24.99", Formatters.money(BigDecimal("24.99"), "EUR", Locale.US))
        assertEquals("1.499 €", Formatters.money(BigDecimal("1499"), "EUR", Locale.GERMANY).normalizeSpaces())

        // A currency without a local symbol is shown by its code, spaced from the digits.
        assertEquals("CZK 1,499", Formatters.money(BigDecimal("1499.00"), "CZK", Locale.US).normalizeSpaces())
    }

    @Test
    fun `whole amounts drop the decimals, fractional ones keep two`() {
        assertEquals("$1,500", Formatters.money(BigDecimal("1500.00"), "USD", Locale.US))
        assertEquals("$1,299.90", Formatters.money(BigDecimal("1299.9"), "USD", Locale.US))
    }

    @Test
    fun `unknown currency codes fall back to number and code`() {
        assertEquals("1,299.9 QQQ", Formatters.money(BigDecimal("1299.9"), "QQQ", Locale.US))
    }

    @Test
    fun `dates follow the device locale`() {
        assertEquals("Oct 5, 2026", Formatters.date(LocalDate.of(2026, 10, 5), Locale.US))
        assertEquals("05.10.2026", Formatters.date(LocalDate.of(2026, 10, 5), Locale.GERMANY))
    }

    @Test
    fun `decimal input uses the locale's decimal separator and round-trips`() {
        assertEquals("1299.9", Formatters.decimalInput(BigDecimal("1299.90"), Locale.US))
        assertEquals("1299,9", Formatters.decimalInput(BigDecimal("1299.90"), Locale.GERMANY))
        val prefilled = Formatters.decimalInput(BigDecimal("1299.90"), Locale.GERMANY)
        assertEquals(BigDecimal("1299.9"), parseDecimalInput(prefilled))
    }
}
