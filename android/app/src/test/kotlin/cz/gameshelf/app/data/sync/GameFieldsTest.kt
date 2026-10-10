package cz.gameshelf.app.data.sync

import cz.gameshelf.app.data.api.ApiJson
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Condition
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.toSaveRequest
import cz.gameshelf.app.testing.FULL_GAME_JSON
import cz.gameshelf.app.testing.GAME_WITH_UNKNOWN_VALUES_JSON
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class GameFieldsTest {

    // As stored: the JSON encoder writes amounts without trailing zeros.
    private val game = ApiJson.decodeFromString<Game>(FULL_GAME_JSON).copy(purchasePrice = BigDecimal("1299.9"))

    @Test
    fun `lists every SaveGameRequest field`() {
        assertEquals(26, GameFields.ALL.size)
        assertTrue("favorite" in GameFields.ALL && "coverImageUrl" in GameFields.ALL)
    }

    @Test
    fun `an unchanged request has no changed fields`() {
        assertEquals(emptySet<String>(), GameFields.diff(game.toSaveRequest(), game.toSaveRequest()))
    }

    @Test
    fun `reports changed and cleared fields`() {
        val edited = game.toSaveRequest().copy(title = "Banjo-Tooie", rating = null, favorite = false)

        assertEquals(setOf("title", "rating", "favorite"), GameFields.diff(game.toSaveRequest(), edited))
    }

    @Test
    fun `amounts compare by value`() {
        val stored = game.copy(purchasePrice = BigDecimal("1299.90"))
        val retyped = stored.toSaveRequest().copy(purchasePrice = BigDecimal("1299.9"))

        assertEquals(emptySet<String>(), GameFields.diff(stored.toSaveRequest(), retyped))
    }

    @Test
    fun `applying changed fields keeps the rest, including values unknown to this version`() {
        val stored = game.copy(status = CollectionStatus.UNKNOWN)
        val request = stored.toSaveRequest().copy(title = "Banjo-Tooie", status = CollectionStatus.OWNED)

        val updated = GameFields.apply(stored, request, setOf("title"))

        assertEquals(stored.copy(title = "Banjo-Tooie"), updated)
    }

    @Test
    fun `merge takes the server version with the local values of pending fields`() {
        val server = game.copy(title = "Server", notes = "New notes", updatedAt = Instant.parse("2026-10-07T13:00:00Z"))
        val local = game.copy(title = "Local", notes = "Old notes", updatedAt = Instant.parse("2026-10-07T14:00:00Z"))

        val merged = GameFields.merge(server, local, setOf("title"))

        assertEquals(server.copy(title = "Local", updatedAt = local.updatedAt), merged)
    }

    @Test
    fun `patch body holds only the changed fields, cleared ones as explicit null`() {
        val body = GameFields.patchBody(game.copy(rating = null, title = "Banjo"), setOf("rating", "title"))

        assertEquals(setOf("rating", "title"), body.keys)
        assertEquals(JsonNull, body["rating"])
        assertEquals(JsonPrimitive("Banjo"), body["title"])
    }

    @Test
    fun `fallbacks without the value they stand for (stored by an older version) are never sent`() {
        val stored = game.copy(status = CollectionStatus.UNKNOWN, condition = Condition.UNKNOWN, title = "Banjo")

        val patch = GameFields.patchBody(stored, setOf("title", "status", "condition"))
        val create = GameFields.createBody(stored)

        assertEquals(setOf("title"), patch.keys)
        assertEquals(GameFields.ALL + "id" - "status" - "condition", create.keys)
        assertFalse(JsonPrimitive("UNKNOWN") in create.values)
    }

    /** As decoded from the API. */
    private val withUnknownValues = ApiJson.decodeFromString<Game>(GAME_WITH_UNKNOWN_VALUES_JSON)

    @Test
    fun `create body sends values unknown to this version as the API sent them`() {
        val body = GameFields.createBody(withUnknownValues)

        assertEquals(GameFields.ALL + "id", body.keys)
        assertEquals(JsonPrimitive("PLAYSTATION_6"), body["platform"])
        assertEquals(JsonPrimitive("BORROWED"), body["status"])
    }

    @Test
    fun `an edit of other fields never patches them, and keeps them for a create`() {
        // The form's initial and saved requests, built the same way.
        val opened = withUnknownValues.toSaveRequest()
        val saved = opened.copy(title = "Banjo-Tooie")
        val fields = GameFields.diff(opened, saved)

        val updated = GameFields.apply(withUnknownValues, saved, fields)

        assertEquals(setOf("title"), fields)
        assertEquals(setOf("title"), GameFields.patchBody(updated, fields).keys)
        assertEquals(withUnknownValues.unknownEnumValues, updated.unknownEnumValues)
        assertEquals(JsonPrimitive("PLAYSTATION_6"), GameFields.createBody(updated)["platform"])
    }

    @Test
    fun `a value the user picks replaces the one this version doesn't know`() {
        val opened = withUnknownValues.toSaveRequest()
        val saved = opened.copy(platform = Platform.PS5, status = CollectionStatus.WISHLIST)
        val fields = GameFields.diff(opened, saved)

        val updated = GameFields.apply(withUnknownValues, saved, fields)

        assertEquals(setOf("platform", "status"), fields)
        assertEquals(emptyMap<String, String>(), updated.unknownEnumValues)
        val patch = GameFields.patchBody(updated, fields)
        assertEquals(JsonPrimitive("PS5"), patch["platform"])
        assertEquals(JsonPrimitive("WISHLIST"), patch["status"])
        assertEquals(JsonPrimitive("WISHLIST"), GameFields.createBody(updated)["status"])
    }

    @Test
    fun `merge keeps the server's unknown values outside the pending fields`() {
        val picked = withUnknownValues.toSaveRequest().copy(platform = Platform.PS5)
        val local = GameFields.apply(withUnknownValues, picked, setOf("platform"))

        val merged = GameFields.merge(withUnknownValues, local, setOf("platform"))

        assertEquals(Platform.PS5, merged.platform)
        assertEquals(mapOf("status" to "BORROWED"), merged.unknownEnumValues)
    }

    @Test
    fun `create body holds the id and every field`() {
        val body = GameFields.createBody(game)

        assertEquals(GameFields.ALL + "id", body.keys)
        assertEquals(JsonPrimitive(game.id), body["id"])
    }
}
