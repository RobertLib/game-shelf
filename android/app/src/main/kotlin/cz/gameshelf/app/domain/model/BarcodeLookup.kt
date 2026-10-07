package cz.gameshelf.app.domain.model

import kotlinx.serialization.Serializable

/** Details of the game with a barcode (OpenAPI `BarcodeLookup`), used to prefill the form. */
@Serializable
data class BarcodeLookup(
    val barcode: String,
    val title: String,
    val platform: Platform?,
    val region: Region?,
    val edition: String?,
    val genre: String?,
    val developer: String?,
    val publisher: String?,
    val releaseYear: Int?,
    val coverImageUrl: String?,
    /** Databases the details come from, shown as attribution. */
    val sources: List<String> = emptyList(),
)

/** EAN / UPC codes printed on game boxes. */
object Barcodes {
    private val VALID = Regex("""\d{8,14}""")

    fun isValid(code: String): Boolean = VALID.matches(code)

    /**
     * Drops the zeros that pad a UPC-A (12 digits) to EAN-13 or GTIN-14, so that every form of one code
     * is equal – iOS scans UPC-A as EAN-13 with a leading zero. Same rule as the API.
     */
    fun normalize(code: String): String {
        var normalized = code.trim()
        while (normalized.length > 12 && normalized.startsWith('0')) normalized = normalized.drop(1)
        return normalized
    }

    fun sameProduct(a: String, b: String): Boolean = normalize(a) == normalize(b)
}
