package cz.gameshelf.app.data.games

import cz.gameshelf.app.data.api.GamesApi
import cz.gameshelf.app.data.api.apiCall
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.GameFacets
import cz.gameshelf.app.domain.model.GamePage
import cz.gameshelf.app.domain.model.GameQuery
import cz.gameshelf.app.domain.model.SaveGameRequest
import cz.gameshelf.app.domain.model.onSuccess
import cz.gameshelf.app.domain.model.toSaveRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A successful mutation, broadcast so that every screen showing the game can update itself. */
sealed interface GameChange {
    data class Created(val game: Game) : GameChange
    data class Updated(val game: Game) : GameChange
    data class Deleted(val id: String) : GameChange
}

interface GamesRepository {
    val changes: Flow<GameChange>

    suspend fun listGames(query: GameQuery, page: Int, pageSize: Int = GameListQuery.DEFAULT_PAGE_SIZE): ApiResult<GamePage>
    suspend fun facets(): ApiResult<GameFacets>
    suspend fun game(id: String): ApiResult<Game>
    suspend fun createGame(request: SaveGameRequest): ApiResult<Game>
    suspend fun updateGame(id: String, request: SaveGameRequest): ApiResult<Game>

    /** `PUT` of the full object with only `favorite` changed. */
    suspend fun setFavorite(game: Game, favorite: Boolean): ApiResult<Game> =
        updateGame(game.id, game.toSaveRequest().copy(favorite = favorite))

    suspend fun deleteGame(id: String): ApiResult<Unit>
}

class NetworkGamesRepository(private val api: GamesApi) : GamesRepository {

    private val _changes = MutableSharedFlow<GameChange>(extraBufferCapacity = 16)
    override val changes: Flow<GameChange> = _changes.asSharedFlow()

    override suspend fun listGames(query: GameQuery, page: Int, pageSize: Int): ApiResult<GamePage> {
        val q = GameListQuery.from(query, page, pageSize)
        return apiCall {
            api.listGames(
                q = q.q,
                platform = q.platform,
                status = q.status,
                format = q.format,
                region = q.region,
                completeness = q.completeness,
                condition = q.condition,
                playStatus = q.playStatus,
                genre = q.genre,
                publisher = q.publisher,
                developer = q.developer,
                storageLocation = q.storageLocation,
                favorite = q.favorite,
                hasCover = q.hasCover,
                releaseYearFrom = q.releaseYearFrom,
                releaseYearTo = q.releaseYearTo,
                purchaseDateFrom = q.purchaseDateFrom,
                purchaseDateTo = q.purchaseDateTo,
                purchasePriceMin = q.purchasePriceMin,
                purchasePriceMax = q.purchasePriceMax,
                estimatedValueMin = q.estimatedValueMin,
                estimatedValueMax = q.estimatedValueMax,
                ratingMin = q.ratingMin,
                ratingMax = q.ratingMax,
                sort = q.sort,
                order = q.order,
                page = q.page,
                pageSize = q.pageSize,
            )
        }
    }

    override suspend fun facets(): ApiResult<GameFacets> = apiCall { api.facets() }

    override suspend fun game(id: String): ApiResult<Game> = apiCall { api.game(id) }

    override suspend fun createGame(request: SaveGameRequest): ApiResult<Game> =
        apiCall { api.createGame(request) }.onSuccess { _changes.emit(GameChange.Created(it)) }

    override suspend fun updateGame(id: String, request: SaveGameRequest): ApiResult<Game> =
        apiCall { api.updateGame(id, request) }.onSuccess { _changes.emit(GameChange.Updated(it)) }

    override suspend fun deleteGame(id: String): ApiResult<Unit> =
        apiCall { api.deleteGame(id) }.onSuccess { _changes.emit(GameChange.Deleted(id)) }
}
