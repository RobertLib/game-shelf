package cz.gameshelf.app.data.api

import kotlinx.serialization.json.Json

/**
 * JSON configuration for the Game Shelf API: tolerant to new response fields, and emitting every
 * request property (defaults and explicit `null`s) because `PUT games/{id}` is a full replacement.
 */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = true
}
