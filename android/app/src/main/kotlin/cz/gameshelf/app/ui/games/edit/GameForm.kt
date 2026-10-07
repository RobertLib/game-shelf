package cz.gameshelf.app.ui.games.edit

import cz.gameshelf.app.domain.model.BarcodeLookup
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Completeness
import cz.gameshelf.app.domain.model.Condition
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.GameFormat
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.PlayStatus
import cz.gameshelf.app.domain.model.Region
import cz.gameshelf.app.domain.model.SaveGameRequest
import cz.gameshelf.app.ui.common.Formatters
import java.time.LocalDate

/** Raw form input; text fields hold exactly what the user typed. Defaults follow the API. */
data class GameForm(
    val title: String = "",
    val platform: Platform? = null,
    val edition: String = "",
    val genre: String = "",
    val developer: String = "",
    val publisher: String = "",
    val releaseYear: String = "",
    val coverImageUrl: String = "",
    val status: CollectionStatus = CollectionStatus.OWNED,
    val format: GameFormat = GameFormat.PHYSICAL,
    val region: Region? = null,
    val completeness: Completeness? = null,
    val condition: Condition? = null,
    val barcode: String = "",
    val productCode: String = "",
    val quantity: String = "1",
    val storageLocation: String = "",
    val purchasePrice: String = "",
    val estimatedValue: String = "",
    val currency: String = SaveGameRequest.DEFAULT_CURRENCY,
    val purchaseDate: LocalDate? = null,
    val purchasePlace: String = "",
    val playStatus: PlayStatus? = null,
    val rating: Int? = null,
    val favorite: Boolean = false,
    val notes: String = "",
)

fun Game.toForm() = GameForm(
    title = title,
    platform = platform,
    edition = edition.orEmpty(),
    genre = genre.orEmpty(),
    developer = developer.orEmpty(),
    publisher = publisher.orEmpty(),
    releaseYear = releaseYear?.toString().orEmpty(),
    coverImageUrl = coverImageUrl.orEmpty(),
    status = status,
    format = format,
    region = region,
    completeness = completeness,
    condition = condition,
    barcode = barcode.orEmpty(),
    productCode = productCode.orEmpty(),
    quantity = quantity.toString(),
    storageLocation = storageLocation.orEmpty(),
    purchasePrice = purchasePrice?.let(Formatters::decimalInput).orEmpty(),
    estimatedValue = estimatedValue?.let(Formatters::decimalInput).orEmpty(),
    currency = currency,
    purchaseDate = purchaseDate,
    purchasePlace = purchasePlace.orEmpty(),
    playStatus = playStatus,
    rating = rating,
    favorite = favorite,
    notes = notes.orEmpty(),
)

/** Fills the empty fields with what a barcode lookup found; whatever the user entered stays. */
fun GameForm.fillFrom(lookup: BarcodeLookup) = copy(
    title = title.ifBlank { lookup.title },
    platform = platform ?: lookup.platform,
    edition = edition.ifBlank { lookup.edition.orEmpty() },
    genre = genre.ifBlank { lookup.genre.orEmpty() },
    developer = developer.ifBlank { lookup.developer.orEmpty() },
    publisher = publisher.ifBlank { lookup.publisher.orEmpty() },
    releaseYear = releaseYear.ifBlank { lookup.releaseYear?.toString().orEmpty() },
    coverImageUrl = coverImageUrl.ifBlank { lookup.coverImageUrl.orEmpty() },
    region = region ?: lookup.region,
    barcode = barcode.ifBlank { lookup.barcode },
)

/** Fields that can carry a validation message. */
enum class GameField {
    TITLE, PLATFORM, EDITION, GENRE, DEVELOPER, PUBLISHER, RELEASE_YEAR, COVER_URL,
    STATUS, FORMAT, COMPLETENESS, CONDITION, BARCODE, PRODUCT_CODE, QUANTITY, STORAGE_LOCATION,
    PURCHASE_PRICE, ESTIMATED_VALUE, CURRENCY, PURCHASE_PLACE, PLAY_STATUS, RATING, NOTES,
}
