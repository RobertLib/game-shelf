package cz.gameshelf.app.data.api

import cz.gameshelf.app.data.api.dto.ErrorResponse
import cz.gameshelf.app.data.api.dto.GameChanges
import cz.gameshelf.app.domain.model.BarcodeLookup
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Completeness
import cz.gameshelf.app.domain.model.Condition
import cz.gameshelf.app.domain.model.ErrorCode
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.GameFormat
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.PlayStatus
import cz.gameshelf.app.domain.model.Region
import cz.gameshelf.app.domain.model.SaveGameRequest
import cz.gameshelf.app.domain.model.toSaveRequest
import cz.gameshelf.app.testing.FULL_GAME_JSON
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class ApiJsonTest {

    @Test
    fun `decodes a barcode lookup`() {
        val lookup = ApiJson.decodeFromString<BarcodeLookup>(
            """
            {"barcode": "045496420055", "title": "Mario Kart 8 Deluxe", "platform": "SWITCH", "region": null,
             "edition": null, "genre": "Racing", "developer": "Nintendo EPD", "publisher": "Nintendo",
             "releaseYear": 2017, "coverImageUrl": null, "sources": ["UPCitemdb", "IGDB"]}
            """,
        )

        assertEquals("Mario Kart 8 Deluxe", lookup.title)
        assertEquals(Platform.SWITCH, lookup.platform)
        assertNull(lookup.region)
        assertEquals(2017, lookup.releaseYear)
        assertEquals(listOf("UPCitemdb", "IGDB"), lookup.sources)
    }

    @Test
    fun `decodes a full game`() {
        val game = ApiJson.decodeFromString<Game>(FULL_GAME_JSON)

        assertEquals("Banjo-Kazooie", game.title)
        assertEquals(Platform.N64, game.platform)
        assertEquals(CollectionStatus.OWNED, game.status)
        assertEquals(GameFormat.PHYSICAL, game.format)
        assertEquals(Region.PAL, game.region)
        assertEquals(Completeness.CIB, game.completeness)
        assertEquals(Condition.NEAR_MINT, game.condition)
        assertEquals(PlayStatus.COMPLETED, game.playStatus)
        assertEquals(1998, game.releaseYear)
        assertEquals(2, game.quantity)
        assertEquals(0, BigDecimal("1299.9").compareTo(game.purchasePrice))
        assertEquals(0, BigDecimal("1500").compareTo(game.estimatedValue))
        assertEquals(LocalDate.of(2024, 5, 17), game.purchaseDate)
        assertEquals(Instant.parse("2026-10-07T12:01:54.435Z"), game.createdAt)
        assertEquals("Shelf A", game.storageLocation)
        assertTrue(game.favorite)
    }

    @Test
    fun `decodes nulls of optional fields`() {
        val json = FULL_GAME_JSON
            .replace("\"region\": \"PAL\"", "\"region\": null")
            .replace("\"purchasePrice\": 1299.90", "\"purchasePrice\": null")
            .replace("\"purchaseDate\": \"2024-05-17\"", "\"purchaseDate\": null")
            .replace("\"rating\": 9", "\"rating\": null")

        val game = ApiJson.decodeFromString<Game>(json)

        assertNull(game.region)
        assertNull(game.purchasePrice)
        assertNull(game.purchaseDate)
        assertNull(game.rating)
    }

    @Test
    fun `unknown enum values fall back instead of failing`() {
        val json = FULL_GAME_JSON
            .replace("\"platform\": \"N64\"", "\"platform\": \"PLAYSTATION_6\"")
            .replace("\"status\": \"OWNED\"", "\"status\": \"BORROWED\"")
            .replace("\"format\": \"PHYSICAL\"", "\"format\": \"CLOUD\"")
            .replace("\"region\": \"PAL\"", "\"region\": \"NTSC_C\"")
            .replace("\"completeness\": \"CIB\"", "\"completeness\": \"CART_ONLY\"")
            .replace("\"condition\": \"NEAR_MINT\"", "\"condition\": \"GRADED\"")
            .replace("\"playStatus\": \"COMPLETED\"", "\"playStatus\": \"PLATINUM\"")

        val game = ApiJson.decodeFromString<Game>(json)

        assertEquals(Platform.OTHER, game.platform)
        assertEquals(CollectionStatus.UNKNOWN, game.status)
        assertEquals(GameFormat.UNKNOWN, game.format)
        assertEquals(Region.OTHER, game.region)
        assertEquals(Completeness.UNKNOWN, game.completeness)
        assertEquals(Condition.UNKNOWN, game.condition)
        assertEquals(PlayStatus.UNKNOWN, game.playStatus)
    }

    @Test
    fun `ignores unknown properties`() {
        val json = FULL_GAME_JSON.replaceFirst("{", "{ \"loanedTo\": { \"name\": \"Petr\" }, ")

        val game = ApiJson.decodeFromString<Game>(json)

        assertEquals("Banjo-Kazooie", game.title)
    }

    @Test
    fun `decodes a change feed page`() {
        val json = """{"games":[$FULL_GAME_JSON],"deletedIds":["0b9e3f4a-1c2d-4e5f-8a9b-0c1d2e3f4a5b"],
            "cursor":"1234","hasMore":true}"""

        val page = ApiJson.decodeFromString<GameChanges>(json)

        assertEquals(listOf("Banjo-Kazooie"), page.games.map { it.title })
        assertEquals(listOf("0b9e3f4a-1c2d-4e5f-8a9b-0c1d2e3f4a5b"), page.deletedIds)
        assertEquals("1234", page.cursor)
        assertTrue(page.hasMore)
    }

    @Test
    fun `save request sends every property including explicit nulls and defaults`() {
        val request = SaveGameRequest(
            title = "Doom",
            platform = Platform.PC,
            purchasePrice = BigDecimal("1299.90"),
            purchaseDate = LocalDate.of(2024, 5, 17),
        )

        val json = ApiJson.encodeToJsonElement(SaveGameRequest.serializer(), request).jsonObject

        assertEquals(26, json.size)
        assertEquals(JsonNull, json["region"])
        assertEquals(JsonNull, json["rating"])
        assertEquals(JsonNull, json["coverImageUrl"])
        assertEquals(JsonPrimitive("OWNED"), json["status"])
        assertEquals(JsonPrimitive("PHYSICAL"), json["format"])
        assertEquals(JsonPrimitive("CZK"), json["currency"])
        assertEquals(JsonPrimitive(1), json["quantity"])
        assertEquals(JsonPrimitive(false), json["favorite"])
        assertEquals(JsonPrimitive("2024-05-17"), json["purchaseDate"])
        // A JSON number, not a string.
        val price = json["purchasePrice"] as JsonPrimitive
        assertFalse(price.isString)
        assertEquals(0, BigDecimal("1299.9").compareTo(BigDecimal(price.content)))
    }

    @Test
    fun `round-trips a game through a save request`() {
        val game = ApiJson.decodeFromString<Game>(FULL_GAME_JSON)
        val json = ApiJson.encodeToJsonElement(
            SaveGameRequest.serializer(),
            game.toSaveRequest(),
        ) as JsonObject

        assertEquals(JsonPrimitive("N64"), json["platform"])
        assertEquals(JsonPrimitive("NEAR_MINT"), json["condition"])
        assertEquals(JsonPrimitive(true), json["favorite"])
    }

    @Test
    fun `decodes error responses with unknown codes`() {
        val reset = ApiJson.decodeFromString<ErrorResponse>(
            """{"statusCode":410,"code":"SYNC_RESET_REQUIRED","message":"Sync reset required"}""",
        )
        val known = ApiJson.decodeFromString<ErrorResponse>(
            """{"statusCode":400,"code":"VALIDATION_FAILED","message":"Validation failed",
               "details":["title should not be empty"]}""",
        )
        val unknown = ApiJson.decodeFromString<ErrorResponse>(
            """{"statusCode":402,"code":"PAYMENT_REQUIRED","message":"Pay"}""",
        )

        assertEquals(ErrorCode.SYNC_RESET_REQUIRED, reset.code)
        assertEquals(ErrorCode.VALIDATION_FAILED, known.code)
        assertEquals(listOf("title should not be empty"), known.details)
        assertEquals(ErrorCode.UNKNOWN, unknown.code)
        assertNull(unknown.details)
    }
}
