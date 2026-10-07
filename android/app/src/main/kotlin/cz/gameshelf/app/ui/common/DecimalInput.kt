package cz.gameshelf.app.ui.common

import java.math.BigDecimal

private val DecimalPattern = Regex("""\d+([.,]\d*)?|[.,]\d+""")

/**
 * Parses a non-negative decimal typed by the user. Either a decimal point or a decimal comma is
 * accepted (`1299.9`, `1299,90`) and spaces are ignored; returns `null` when the input is invalid.
 */
fun parseDecimalInput(input: String): BigDecimal? {
    val compact = input.filterNot { it.isWhitespace() || it == ' ' }
    if (!DecimalPattern.matches(compact)) return null
    return compact.replace(',', '.').toBigDecimalOrNull()
}

fun BigDecimal.hasAtMostTwoDecimals(): Boolean = stripTrailingZeros().scale() <= 2
