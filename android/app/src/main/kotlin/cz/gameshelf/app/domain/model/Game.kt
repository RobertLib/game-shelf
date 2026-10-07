@file:UseSerializers(BigDecimalSerializer::class, LocalDateSerializer::class, InstantSerializer::class)

package cz.gameshelf.app.domain.model

import cz.gameshelf.app.domain.serialization.BigDecimalSerializer
import cz.gameshelf.app.domain.serialization.InstantSerializer
import cz.gameshelf.app.domain.serialization.LocalDateSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** A game in the collection (OpenAPI `Game`). Every property is required; optional ones are nullable. */
@Serializable
data class Game(
    val id: String,
    val title: String,
    val platform: Platform,
    val status: CollectionStatus,
    val format: GameFormat,
    val region: Region?,
    val edition: String?,
    val completeness: Completeness?,
    val condition: Condition?,
    val playStatus: PlayStatus?,
    val genre: String?,
    val developer: String?,
    val publisher: String?,
    val releaseYear: Int?,
    val barcode: String?,
    val productCode: String?,
    val quantity: Int,
    val purchasePrice: BigDecimal?,
    val purchaseDate: LocalDate?,
    val purchasePlace: String?,
    val estimatedValue: BigDecimal?,
    val currency: String,
    val storageLocation: String?,
    val rating: Int?,
    val favorite: Boolean,
    val coverImageUrl: String?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
