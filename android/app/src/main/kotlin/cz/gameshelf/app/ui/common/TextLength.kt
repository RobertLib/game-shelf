package cz.gameshelf.app.ui.common

/**
 * Length in Unicode code points, as the API counts it (mobile-spec.md, "Validation"): `"😀"` is 1,
 * `"🇨🇿"` is 2 and a decomposed `"é"` (`e` + U+0301) is 2.
 */
fun String.codePointLength(): Int = codePointCount(0, length)

/** The first [maxLength] code points; a surrogate pair is never cut in half. */
fun String.takeCodePoints(maxLength: Int): String =
    if (codePointLength() <= maxLength) this else substring(0, offsetByCodePoints(0, maxLength))

/**
 * The text as the API stores it (mobile-spec.md, "Validation"): without U+0000, then trimmed of white space
 * and U+FEFF, which JavaScript's `trim()` removes too but Kotlin's `isWhitespace()` doesn't.
 */
fun String.normalizedText(): String = replace("\u0000", "").trim { it.isWhitespace() || it == '\uFEFF' }
