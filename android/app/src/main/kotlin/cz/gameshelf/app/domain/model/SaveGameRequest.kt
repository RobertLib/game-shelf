@file:UseSerializers(BigDecimalSerializer::class, LocalDateSerializer::class)

package cz.gameshelf.app.domain.model

import cz.gameshelf.app.domain.serialization.BigDecimalSerializer
import cz.gameshelf.app.domain.serialization.LocalDateSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Body of `POST games` and `PUT games/{id}` (OpenAPI `SaveGameRequest`). PUT is a full replacement,
 * so the API JSON is configured to always emit every property, including defaults and nulls.
 */
@Serializable
data class SaveGameRequest(
    val title: String,
    val platform: Platform,
    val status: CollectionStatus = CollectionStatus.OWNED,
    val format: GameFormat = GameFormat.PHYSICAL,
    val region: Region? = null,
    val edition: String? = null,
    val completeness: Completeness? = null,
    val condition: Condition? = null,
    val playStatus: PlayStatus? = null,
    val genre: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val releaseYear: Int? = null,
    val barcode: String? = null,
    val productCode: String? = null,
    val quantity: Int = 1,
    val purchasePrice: BigDecimal? = null,
    val purchaseDate: LocalDate? = null,
    val purchasePlace: String? = null,
    val estimatedValue: BigDecimal? = null,
    val currency: String = DEFAULT_CURRENCY,
    val storageLocation: String? = null,
    val rating: Int? = null,
    val favorite: Boolean = false,
    val coverImageUrl: String? = null,
    val notes: String? = null,
) {
    companion object {
        const val DEFAULT_CURRENCY = "CZK"
    }
}

fun Game.toSaveRequest(): SaveGameRequest = SaveGameRequest(
    title = title,
    platform = platform,
    status = status,
    format = format,
    region = region,
    edition = edition,
    completeness = completeness,
    condition = condition,
    playStatus = playStatus,
    genre = genre,
    developer = developer,
    publisher = publisher,
    releaseYear = releaseYear,
    barcode = barcode,
    productCode = productCode,
    quantity = quantity,
    purchasePrice = purchasePrice,
    purchaseDate = purchaseDate,
    purchasePlace = purchasePlace,
    estimatedValue = estimatedValue,
    currency = currency,
    storageLocation = storageLocation,
    rating = rating,
    favorite = favorite,
    coverImageUrl = coverImageUrl,
    notes = notes,
)
