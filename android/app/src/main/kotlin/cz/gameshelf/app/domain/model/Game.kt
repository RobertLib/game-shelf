@file:UseSerializers(BigDecimalSerializer::class, LocalDateSerializer::class, InstantSerializer::class)

package cz.gameshelf.app.domain.model

import cz.gameshelf.app.domain.serialization.BigDecimalSerializer
import cz.gameshelf.app.domain.serialization.InstantSerializer
import cz.gameshelf.app.domain.serialization.LenientEnumSerializer
import cz.gameshelf.app.domain.serialization.LocalDateSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * A game in the collection (OpenAPI `Game`). Every property of the API is required; optional ones are nullable.
 * Its JSON shape (API responses, the games stored on the device) is handled by [GameSerializer].
 */
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = GameSerializer::class)
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
    /**
     * The wire values of enum properties this app version doesn't know, by property name (e.g. `platform` → a
     * platform added to a newer API); such a property holds its fallback (`UNKNOWN`, or `OTHER` for platform and
     * region). Not part of the API's shape: [GameSerializer] writes each value back in place of the fallback, so
     * the stored game keeps what the API sent (offline-sync.md, "Local data"). A value whose property no longer
     * holds the fallback (the user picked another one) is dropped.
     */
    val unknownEnumValues: Map<String, String> = emptyMap(),
)

/**
 * [Game] in its JSON shape, keeping enum values this app version doesn't know verbatim (offline-sync.md, "Local
 * data"): decoding records each one in [Game.unknownEnumValues] while its property gets the fallback; encoding puts
 * it back in place of the fallback. So it survives storing the game on the device, an edit of other fields and a
 * `CREATE` that pushes the whole game again – unless the user replaced it with a value of their own.
 */
@OptIn(ExperimentalSerializationApi::class)
object GameSerializer : JsonTransformingSerializer<Game>(Game.generatedSerializer()) {

    private val UNKNOWN_ENUM_VALUES = Game::unknownEnumValues.name

    private val enumProperties: Map<String, LenientEnumSerializer<*>> = mapOf(
        Game::platform.name to PlatformSerializer,
        Game::status.name to CollectionStatusSerializer,
        Game::format.name to GameFormatSerializer,
        Game::region.name to RegionSerializer,
        Game::completeness.name to CompletenessSerializer,
        Game::condition.name to ConditionSerializer,
        Game::playStatus.name to PlayStatusSerializer,
    )

    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        val unknown = enumProperties.mapNotNull { (name, serializer) ->
            val value = element[name] as? JsonPrimitive
            if (value == null || !value.isString || serializer.isKnown(value.content)) null else name to value
        }
        // Replaces whatever the input had under that name: only this serializer writes it.
        return JsonObject(element + (UNKNOWN_ENUM_VALUES to JsonObject(unknown.toMap())))
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        val unknown = (element[UNKNOWN_ENUM_VALUES] as? JsonObject).orEmpty()
        val restored = unknown.filter { (name, _) ->
            val serializer = enumProperties[name] ?: return@filter false
            element[name] == JsonPrimitive(serializer.fallbackValue)
        }
        return JsonObject(element - UNKNOWN_ENUM_VALUES + restored)
    }
}
