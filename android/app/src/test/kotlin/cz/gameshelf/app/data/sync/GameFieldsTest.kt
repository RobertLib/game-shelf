package cz.gameshelf.app.data.sync

import cz.gameshelf.app.data.api.ApiJson
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.toSaveRequest
import cz.gameshelf.app.testing.FULL_GAME_JSON
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
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
    fun `create body holds the id and every field`() {
        val body = GameFields.createBody(game)

        assertEquals(GameFields.ALL + "id", body.keys)
        assertEquals(JsonPrimitive(game.id), body["id"])
    }
}
