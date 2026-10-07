package cz.gameshelf.app.data.games

import cz.gameshelf.app.data.local.LocalGameStore
import cz.gameshelf.app.data.sync.GameFields
import cz.gameshelf.app.data.sync.PendingChange
import cz.gameshelf.app.data.sync.afterDelete
import cz.gameshelf.app.data.sync.afterEdit
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.SaveGameRequest
import cz.gameshelf.app.domain.model.toNewGame
import cz.gameshelf.app.domain.model.toSaveRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID

/** A change the user made on this device, broadcast for confirmation messages. */
sealed interface GameChange {
    data class Created(val game: Game) : GameChange
    data class Updated(val game: Game) : GameChange
    data class Deleted(val id: String) : GameChange
}

/**
 * The collection stored on the device. Reads follow the local database, so they update by themselves
 * when a sync brings changes. Writes are local and instant and never fail because of the network:
 * each one records a pending change in the same transaction and asks for a sync.
 */
interface GamesRepository {
    /** Changes made by the user on this device; changes brought by a sync are not reported. */
    val changes: Flow<GameChange>

    /** Every stored game, in no particular order. */
    val games: Flow<List<Game>>

    /** The stored game; `null` once it is gone (deleted here or on another device). */
    fun observeGame(id: String): Flow<Game?>

    suspend fun game(id: String): Game?

    suspend fun createGame(request: SaveGameRequest): Game

    /**
     * Saves the fields of [request] that differ from [base] – the request the edit started from – or,
     * without it, from the stored game. Other fields keep their stored values, so changes a sync
     * brought in while the form was open survive. `null` when the game no longer exists.
     */
    suspend fun updateGame(id: String, request: SaveGameRequest, base: SaveGameRequest? = null): Game?

    suspend fun setFavorite(id: String, favorite: Boolean): Game?

    suspend fun deleteGame(id: String)
}

class OfflineGamesRepository(
    private val store: LocalGameStore,
    private val requestSync: () -> Unit,
    scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : GamesRepository {

    private val _changes = MutableSharedFlow<GameChange>(extraBufferCapacity = 16)
    override val changes: Flow<GameChange> = _changes.asSharedFlow()

    // One query and decoding pass shared by all screens; the cached list is dropped with the last
    // subscriber, so a later subscriber never sees a stale (e.g. previous user's) collection.
    override val games: Flow<List<Game>> = store.observeGames()
        .flowOn(decodeDispatcher)
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0), replay = 1)

    override fun observeGame(id: String): Flow<Game?> = store.observeGame(id).flowOn(decodeDispatcher)

    override suspend fun game(id: String): Game? = withContext(decodeDispatcher) { store.game(id) }

    override suspend fun createGame(request: SaveGameRequest): Game {
        val now = clock.instant()
        val game = request.toNewGame(newId(), now)
        store.transaction {
            store.putGame(game)
            store.putPendingChange(PendingChange.create(game.id, now.toEpochMilli()))
        }
        onLocalChange(GameChange.Created(game))
        return game
    }

    override suspend fun updateGame(id: String, request: SaveGameRequest, base: SaveGameRequest?): Game? =
        edit(id, base) { request }

    override suspend fun setFavorite(id: String, favorite: Boolean): Game? =
        edit(id, base = null) { it.copy(favorite = favorite) }

    override suspend fun deleteGame(id: String) {
        val now = clock.millis()
        val deleted = store.transaction {
            if (store.game(id) == null) return@transaction false
            store.removeGame(id)
            val pending = store.pendingChange(id).afterDelete(id, now)
            if (pending == null) store.removePendingChange(id) else store.putPendingChange(pending)
            true
        }
        if (deleted) onLocalChange(GameChange.Deleted(id))
    }

    /**
     * Applies the fields of `transform(stored)` that differ from [base] (default: the stored game).
     * An edit that changes nothing records no pending change.
     */
    private suspend fun edit(
        id: String,
        base: SaveGameRequest?,
        transform: (SaveGameRequest) -> SaveGameRequest,
    ): Game? {
        val now = clock.instant()
        var changed = false
        val game = store.transaction {
            val stored = store.game(id) ?: return@transaction null
            val request = transform(stored.toSaveRequest())
            val fields = GameFields.diff(base ?: stored.toSaveRequest(), request)
            if (fields.isEmpty()) return@transaction stored
            val updated = GameFields.apply(stored, request, fields).copy(updatedAt = now)
            store.putGame(updated)
            store.putPendingChange(store.pendingChange(id).afterEdit(id, fields, now.toEpochMilli()))
            changed = true
            updated
        }
        if (changed && game != null) onLocalChange(GameChange.Updated(game))
        return game
    }

    private suspend fun onLocalChange(change: GameChange) {
        _changes.emit(change)
        requestSync()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
