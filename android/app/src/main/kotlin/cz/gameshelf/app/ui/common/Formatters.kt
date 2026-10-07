package cz.gameshelf.app.ui.common

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** Formatting of dates, numbers and prices with the device locale. */
object Formatters {

    fun date(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

    fun dateTime(
        instant: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .format(instant.atZone(zone))

    /**
     * The amount in the game's own currency, laid out by the device locale (`CZK 1,499`, `24,99 €`).
     * Whole amounts are shown without decimals.
     */
    fun money(amount: BigDecimal, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val currency = runCatching { Currency.getInstance(currencyCode.uppercase()) }.getOrNull()
            ?: return "${number(amount, locale)} $currencyCode"
        val fractionDigits = if (amount.stripTrailingZeros().scale() <= 0) 0 else 2
        val formatted = NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }.format(amount)
        return formatted.withCurrencySpacing(currency.getSymbol(locale))
    }

    /**
     * CLDR "currency spacing", which `java.text` does not apply: a symbol made of letters (an ISO code
     * such as `CZK`) is separated from the digits by a no-break space – `CZK 1,500`, not `CZK1,500`.
     */
    private fun String.withCurrencySpacing(symbol: String): String {
        if (symbol.isEmpty() || !symbol.first().isLetter() && !symbol.last().isLetter()) return this
        val escaped = Regex.escape(symbol)
        return replace(Regex("($escaped)(?=\\d)"), "$1\u00A0").replace(Regex("(?<=\\d)($escaped)"), "\u00A0$1")
    }

    fun number(amount: BigDecimal, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(amount)

    /** Pre-fills an amount into a text field without grouping, using the locale's decimal separator. */
    fun decimalInput(amount: BigDecimal, locale: Locale = Locale.getDefault()): String {
        val separator = if (DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',') ',' else '.'
        return amount.stripTrailingZeros().toPlainString().replace('.', separator)
    }
}
