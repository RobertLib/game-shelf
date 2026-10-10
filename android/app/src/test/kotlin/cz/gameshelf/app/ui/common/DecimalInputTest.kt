package cz.gameshelf.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** The vectors of mobile-spec.md, "Decimal input". */
class DecimalInputTest {

    @Test
    fun `reads a decimal point or comma with grouping, keeping the decimals as typed`() {
        val expected = mapOf(
            "1299.90" to "1299.90",
            "1299,9" to "1299.9",
            "1 299,90" to "1299.90",
            "1,299.90" to "1299.90",
            "1.299,90" to "1299.90",
            "1,5" to "1.5",
            "1,50" to "1.50",
            "1,000" to "1000",
            "2.500" to "2500",
            "1.234.567,89" to "1234567.89",
            "12,345,678" to "12345678",
            ",5" to "0.5",
            "5," to "5",
            "1,5000" to "1.5000",
        )

        expected.forEach { (input, value) ->
            val parsed = parseDecimalInput(input)
            // Equal including the scale: the decimal places stay as typed.
            assertEquals(input, BigDecimal(value), parsed)
        }
    }

    @Test
    fun `rejects input that cannot be read unambiguously`() {
        listOf("1.23,45", "1.2.3", "1,2.3", "1,2,3", ".", ",", "12a", "", "   ", "-5", "1e3", ",500", "1000,000", "1,23,456", "0,500", "0.001", "012,345")
            .forEach { assertNull(it, parseDecimalInput(it)) }
    }

    @Test
    fun `ignores every kind of space`() {
        listOf("1 299,90", "1 299,90", "1 299,90", "1 299,90", " 1299,90\t")
            .forEach { assertEquals(it, BigDecimal("1299.90"), parseDecimalInput(it)) }
    }

    @Test
    fun `counts the decimal places as typed`() {
        assertTrue(parseDecimalInput("1,50")!!.hasAtMostTwoDecimals())
        assertTrue(parseDecimalInput("1,500")!!.hasAtMostTwoDecimals())
        assertFalse(parseDecimalInput("1,5000")!!.hasAtMostTwoDecimals())
        assertFalse(parseDecimalInput("1.0000")!!.hasAtMostTwoDecimals())
    }

    @Test
    fun `amounts are 0 to 9 999 999 999,99 with at most two decimals`() {
        assertTrue(parseDecimalInput("0")!!.isValidAmount())
        assertTrue(parseDecimalInput("9 999 999 999,99")!!.isValidAmount())
        assertFalse(parseDecimalInput("10 000 000 000")!!.isValidAmount())
        assertFalse(parseDecimalInput("10000000000,5")!!.isValidAmount())
        assertFalse(parseDecimalInput("1,5000")!!.isValidAmount())
    }
}
