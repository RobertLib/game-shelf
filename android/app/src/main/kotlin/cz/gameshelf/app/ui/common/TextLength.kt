package cz.gameshelf.app.ui.common

/**
 * Length in Unicode code points, as the API counts it (mobile-spec.md, "Validation"): `"😀"` is 1,
 * `"🇨🇿"` is 2 and a decomposed `"é"` (`e` + U+0301) is 2.
 */
fun String.codePointLength(): Int = codePointCount(0, length)
