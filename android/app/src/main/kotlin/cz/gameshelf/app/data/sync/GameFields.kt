package cz.gameshelf.app.data.sync

import cz.gameshelf.app.data.api.ApiJson
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.SaveGameRequest
import cz.gameshelf.app.domain.model.toSaveRequest
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Field-level operations on games in their JSON shape (the property names of `SaveGameRequest`),
 * so that every field is handled the same way – including enum values unknown to this app version.
 */
object GameFields {

    /** Every editable field, i.e. every `SaveGameRequest` property. */
    val ALL: Set<String> = SaveGameRequest.serializer().descriptor.elementNames.toSet()

    /** Fields whose value differs between the two requests; amounts compare by value (`1.50` = `1.5`). */
    fun diff(before: SaveGameRequest, after: SaveGameRequest): Set<String> {
        val old = before.toJson()
        val new = after.toJson()
        return ALL.filterTo(linkedSetOf()) { !sameValue(old[it], new[it]) }
    }

    /** [game] with [fields] taken from [request]; everything else stays as stored. */
    fun apply(game: Game, request: SaveGameRequest, fields: Set<String>): Game =
        game.withFields(request.toJson(), fields)

    /** The [server] version with the local values of [fields] that are still waiting to be pushed. */
    fun merge(server: Game, local: Game, fields: Set<String>): Game {
        val merged = server.withFields(local.toJson(), fields)
        return if (local.updatedAt > merged.updatedAt) merged.copy(updatedAt = local.updatedAt) else merged
    }

    /** `PATCH games/{id}` body: [fields] of [game], cleared optional fields as explicit `null`. */
    fun patchBody(game: Game, fields: Set<String>): JsonObject {
        val request = game.toSaveRequest().toJson()
        return JsonObject(request.filterKeys { it in fields })
    }

    /** `POST games` body: every field of [game] plus its client-generated id. */
    fun createBody(game: Game): JsonObject =
        JsonObject(mapOf("id" to JsonPrimitive(game.id)) + game.toSaveRequest().toJson())

    private fun Game.withFields(source: JsonObject, fields: Set<String>): Game {
        val json = toJson()
        val replaced = fields.filter { it in ALL && it in source }.associateWith { source.getValue(it) }
        return ApiJson.decodeFromJsonElement(Game.serializer(), JsonObject(json + replaced))
    }

    private fun SaveGameRequest.toJson(): JsonObject =
        ApiJson.encodeToJsonElement(SaveGameRequest.serializer(), this).jsonObject

    private fun Game.toJson(): JsonObject = ApiJson.encodeToJsonElement(Game.serializer(), this).jsonObject

    private fun sameValue(a: JsonElement?, b: JsonElement?): Boolean {
        if (a == b) return true
        if (a !is JsonPrimitive || b !is JsonPrimitive || a.isString || b.isString) return false
        val x = a.content.toBigDecimalOrNull() ?: return false
        val y = b.content.toBigDecimalOrNull() ?: return false
        return x.compareTo(y) == 0
    }
}
