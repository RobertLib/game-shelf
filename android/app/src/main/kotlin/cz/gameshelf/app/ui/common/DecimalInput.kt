package cz.gameshelf.app.ui.common

import java.math.BigDecimal

/** Largest purchase price / estimated value the API accepts. */
val MAX_AMOUNT = BigDecimal("9999999999.99")

private const val GROUP_SIZE = 3

/**
 * Parses a non-negative decimal typed by the user, the same way in every locale (mobile-spec.md,
 * "Decimal input"): a decimal point or a decimal comma, grouping with the other one (`1.234.567,89`,
 * `1,299.90`) and any whitespace (`1 299,90`) are accepted. A single separator followed by exactly
 * three digits is grouping (`1,500` = 1500), as prices never have three decimals.
 *
 * The result keeps the decimal places as typed (`1,50` = 1.50, `1,5000` = 1.5000), so that
 * [hasAtMostTwoDecimals] can check them; `null` when the input is invalid.
 */
fun parseDecimalInput(input: String): BigDecimal? {
    // isWhitespace() covers the no-break spaces of number formats (U+00A0, U+202F) and thin spaces (U+2009).
    val compact = input.filterNot { it.isWhitespace() }
    if (compact.any { it !in '0'..'9' && it != '.' && it != ',' } || compact.none { it in '0'..'9' }) return null

    val separatorIndex = decimalSeparatorIndex(compact) ?: return null
    val whole = if (separatorIndex < 0) compact else compact.substring(0, separatorIndex)
    val decimals = if (separatorIndex < 0) "" else compact.substring(separatorIndex + 1)
    val wholeDigits = ungroup(whole) ?: return null
    if (wholeDigits.isEmpty() && decimals.isEmpty()) return null
    val number = wholeDigits.ifEmpty { "0" }
    return BigDecimal(if (decimals.isEmpty()) number else "$number.$decimals")
}

/** "At most 2 decimal places" as typed: `1,50` is fine, `1,500` was read as 1500, `1,5000` has 4. */
fun BigDecimal.hasAtMostTwoDecimals(): Boolean = scale() <= 2

/** A purchase price or estimated value the API accepts: 0 – 9 999 999 999.99 with at most 2 decimals as typed. */
fun BigDecimal.isValidAmount(): Boolean = hasAtMostTwoDecimals() && this <= MAX_AMOUNT

/**
 * Index of the decimal separator in [compact] (digits, `.` and `,` only); `-1` when every separator in it
 * is grouping, `null` when it cannot be told.
 */
private fun decimalSeparatorIndex(compact: String): Int? {
    val lastDot = compact.lastIndexOf('.')
    val lastComma = compact.lastIndexOf(',')
    val last = maxOf(lastDot, lastComma)
    if (last < 0) return -1
    val occurrences = compact.count { it == compact[last] }
    return when {
        // Both occur: the one that occurs last separates the decimals (exactly once), the other one is grouping.
        lastDot >= 0 && lastComma >= 0 -> last.takeIf { occurrences == 1 }
        // Only one of them, more than once: grouping.
        occurrences > 1 -> -1
        // Only one of them, once: the decimals, unless exactly 3 digits follow it.
        compact.length - last - 1 == GROUP_SIZE -> -1
        else -> last
    }
}

/**
 * The digits of the whole part: 1–3 digits not starting with `0`, then groups of exactly 3 (`12,345,678`);
 * `null` when grouped wrongly (`0,500` is not 500).
 */
private fun ungroup(whole: String): String? {
    val separator = whole.firstOrNull { it == '.' || it == ',' } ?: return whole
    val groups = whole.split(separator)
    val first = groups.first()
    val grouped = first.length in 1..GROUP_SIZE && !first.startsWith('0') && groups.drop(1).all { it.length == GROUP_SIZE }
    return if (grouped) groups.joinToString("") else null
}
