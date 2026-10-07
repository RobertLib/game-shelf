package cz.gameshelf.app.data.api

import kotlinx.serialization.json.Json

/**
 * JSON configuration for the Game Shelf API and the games stored on the device: tolerant to new
 * response fields, and emitting every property (defaults and explicit `null`s), so a `PATCH` body can
 * clear optional fields and a stored game keeps the complete `Game` shape.
 */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = true
}
