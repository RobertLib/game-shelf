package cz.gameshelf.app.testing

import cz.gameshelf.app.data.games.GameChange
import cz.gameshelf.app.data.games.GamesRepository
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.GameFacets
import cz.gameshelf.app.domain.model.GamePage
import cz.gameshelf.app.domain.model.GameQuery
import cz.gameshelf.app.domain.model.SaveGameRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow

/** In-memory collection of [totalItems] games; [gate] lets a test hold list responses back. */
class FakeGamesRepository(private val totalItems: Int) : GamesRepository {

    data class ListCall(val query: GameQuery, val page: Int)

    val listCalls = mutableListOf<ListCall>()
    var gate: CompletableDeferred<Unit>? = null
    var failNextList = false

    override val changes = MutableSharedFlow<GameChange>(extraBufferCapacity = 8)

    private val games = (1..totalItems).map { testGame(id = it.toString()) }

    override suspend fun listGames(query: GameQuery, page: Int, pageSize: Int): ApiResult<GamePage> {
        listCalls += ListCall(query, page)
        gate?.await()
        if (failNextList) {
            failNextList = false
            return ApiResult.Failure(AppError.Network)
        }
        val items = games.drop((page - 1) * pageSize).take(pageSize)
        val totalPages = (totalItems + pageSize - 1) / pageSize
        return ApiResult.Success(GamePage(items, page, pageSize, totalItems, totalPages))
    }

    override suspend fun facets(): ApiResult<GameFacets> = ApiResult.Success(GameFacets.Empty)

    override suspend fun game(id: String): ApiResult<Game> = ApiResult.Success(games.first { it.id == id })

    override suspend fun createGame(request: SaveGameRequest): ApiResult<Game> = error("not used")

    override suspend fun updateGame(id: String, request: SaveGameRequest): ApiResult<Game> = error("not used")

    override suspend fun deleteGame(id: String): ApiResult<Unit> {
        changes.emit(GameChange.Deleted(id))
        return ApiResult.Success(Unit)
    }
}
