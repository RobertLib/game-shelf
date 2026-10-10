package cz.gameshelf.app.data.api.dto

import android.util.Log
import cz.gameshelf.app.domain.model.Game
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One page of `GET games/changes` (OpenAPI `GameChanges`). */
@Serializable(with = GameChangesSerializer::class)
data class GameChanges(
    /** Games created or changed since the cursor. */
    val games: List<Game>,
    val deletedIds: List<String>,
    /** Opaque; pass it to the next call. */
    val cursor: String,
    val hasMore: Boolean,
    /**
     * Ids of the games of this page that this app version can't decode; they are not in [games]. Not part of the
     * API's shape (see [GameChangesSerializer]).
     */
    val undecodableGameIds: List<String> = emptyList(),
)

/** The page as the API sends it, its games not decoded yet. */
@Serializable
private class GameChangesPayload(
    val games: List<JsonElement>,
    val deletedIds: List<String>,
    val cursor: String,
    val hasMore: Boolean,
)

/**
 * Decodes each game of a [GameChanges] page on its own (offline-sync.md, "Pull"): a game this app version can't
 * decode is skipped and logged – its id, if it has one, goes to [GameChanges.undecodableGameIds] – so that one game
 * never fails the whole page and stops the device from receiving changes. A page that is malformed itself (no cursor,
 * …) still fails.
 */
object GameChangesSerializer : KSerializer<GameChanges> {
    private const val TAG = "GameChanges"

    override val descriptor: SerialDescriptor = GameChangesPayload.serializer().descriptor

    override fun deserialize(decoder: Decoder): GameChanges {
        val json = (decoder as? JsonDecoder)?.json
            ?: throw SerializationException("GameChanges can only be read from JSON")
        val payload = decoder.decodeSerializableValue(GameChangesPayload.serializer())
        val games = ArrayList<Game>(payload.games.size)
        val undecodable = mutableListOf<String>()
        for (element in payload.games) {
            try {
                games += json.decodeFromJsonElement(Game.serializer(), element)
            } catch (e: RuntimeException) {
                // SerializationException, or an invalid date or amount.
                val id = ((element as? JsonObject)?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content
                Log.e(TAG, "Skipping game $id of the change feed: this version can't read it", e)
                if (id != null) undecodable += id
            }
        }
        return GameChanges(games, payload.deletedIds, payload.cursor, payload.hasMore, undecodable)
    }

    override fun serialize(encoder: Encoder, value: GameChanges) {
        val json = (encoder as? JsonEncoder)?.json
            ?: throw SerializationException("GameChanges can only be written as JSON")
        val payload = GameChangesPayload(
            games = value.games.map { json.encodeToJsonElement(Game.serializer(), it) },
            deletedIds = value.deletedIds,
            cursor = value.cursor,
            hasMore = value.hasMore,
        )
        encoder.encodeSerializableValue(GameChangesPayload.serializer(), payload)
    }
}
