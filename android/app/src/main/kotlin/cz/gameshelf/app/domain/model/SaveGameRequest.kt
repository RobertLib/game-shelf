@file:UseSerializers(BigDecimalSerializer::class, LocalDateSerializer::class)

package cz.gameshelf.app.domain.model

import cz.gameshelf.app.domain.serialization.BigDecimalSerializer
import cz.gameshelf.app.domain.serialization.LocalDateSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * The editable fields of a game (OpenAPI `SaveGameRequest`). The form produces it; the sync sends it
 * whole in `POST games` and field by field in `PATCH games/{id}`.
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

/** A game created on the device; the server's timestamps replace [now] after sync. */
fun SaveGameRequest.toNewGame(id: String, now: Instant): Game = Game(
    id = id,
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
    createdAt = now,
    updatedAt = now,
)
