package cz.gameshelf.app.data.api.dto

import cz.gameshelf.app.domain.model.Game
import kotlinx.serialization.Serializable

/** One page of `GET games/changes` (OpenAPI `GameChanges`). */
@Serializable
data class GameChanges(
    /** Games created or changed since the cursor. */
    val games: List<Game>,
    val deletedIds: List<String>,
    /** Opaque; pass it to the next call. */
    val cursor: String,
    val hasMore: Boolean,
)
