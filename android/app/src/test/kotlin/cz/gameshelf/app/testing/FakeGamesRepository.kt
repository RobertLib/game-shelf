package cz.gameshelf.app.testing

import cz.gameshelf.app.data.games.GameChange
import cz.gameshelf.app.data.games.GamesRepository
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.SaveGameRequest
import cz.gameshelf.app.domain.model.toNewGame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** In-memory local collection; [stored] is `null` until "the database has been read". */
class FakeGamesRepository(initial: List<Game>? = emptyList()) : GamesRepository {

    val stored = MutableStateFlow(initial)

    override val changes = MutableSharedFlow<GameChange>(extraBufferCapacity = 8)

    override val games: Flow<List<Game>> = stored.filterNotNull()

    private var nextId = 1

    override fun observeGame(id: String): Flow<Game?> = stored.map { games -> games?.find { it.id == id } }

    override suspend fun game(id: String): Game? = stored.value?.find { it.id == id }

    override suspend fun createGame(request: SaveGameRequest): Game {
        val game = request.toNewGame("new-${nextId++}", Instant.EPOCH)
        stored.update { it.orEmpty() + game }
        changes.emit(GameChange.Created(game))
        return game
    }

    override suspend fun updateGame(id: String, request: SaveGameRequest, base: SaveGameRequest?): Game? {
        val game = game(id) ?: return null
        val updated = request.toNewGame(id, game.createdAt)
        stored.update { games -> games?.map { if (it.id == id) updated else it } }
        changes.emit(GameChange.Updated(updated))
        return updated
    }

    override suspend fun setFavorite(id: String, favorite: Boolean): Game? {
        val updated = game(id)?.copy(favorite = favorite) ?: return null
        stored.update { games -> games?.map { if (it.id == id) updated else it } }
        changes.emit(GameChange.Updated(updated))
        return updated
    }

    override suspend fun deleteGame(id: String) {
        stored.update { games -> games?.filterNot { it.id == id } }
        changes.emit(GameChange.Deleted(id))
    }
}
