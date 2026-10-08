package cz.gameshelf.app.data.lookup

import cz.gameshelf.app.data.api.LookupApi
import cz.gameshelf.app.data.api.apiCall
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.GameSearchResponse
import cz.gameshelf.app.domain.model.Platform

sealed interface GameSearchOutcome {
    /** The games found; an empty `items` means nothing matches. */
    data class Found(val response: GameSearchResponse) : GameSearchOutcome

    data class Failed(val error: AppError) : GameSearchOutcome
}

/** Finds games by title in the game database. Unlike the collection, it needs a connection. */
fun interface GameSearchRepository {
    /** [query] has 2–100 characters; games on [platform] (the one chosen in the form) come first. */
    suspend fun search(query: String, platform: Platform?): GameSearchOutcome
}

class RemoteGameSearchRepository(private val api: LookupApi) : GameSearchRepository {
    override suspend fun search(query: String, platform: Platform?): GameSearchOutcome =
        when (val result = apiCall { api.searchGames(query, platform?.apiValue) }) {
            is ApiResult.Success -> GameSearchOutcome.Found(result.value)
            is ApiResult.Failure -> GameSearchOutcome.Failed(result.error)
        }
}
