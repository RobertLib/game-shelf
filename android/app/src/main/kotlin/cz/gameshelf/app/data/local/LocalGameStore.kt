package cz.gameshelf.app.data.local

import android.util.Log
import androidx.room.withTransaction
import cz.gameshelf.app.data.api.ApiJson
import cz.gameshelf.app.data.sync.PendingChange
import cz.gameshelf.app.domain.model.Game
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant

/** Sync bookkeeping; see [SyncStateEntity]. */
data class SyncState(
    val ownerUserId: String? = null,
    val cursor: String? = null,
    val lastSyncedAt: Instant? = null,
)

/**
 * Typed access to [GameShelfDatabase]: games decoded from their JSON payload, pending changes and the
 * sync state. Every multi-step change runs inside [transaction].
 */
class LocalGameStore(
    private val database: GameShelfDatabase,
    private val json: Json = ApiJson,
) {
    private val dao = database.dao()

    suspend fun <R> transaction(block: suspend () -> R): R = database.withTransaction(block)

    /** Every stored game. Rows whose payload did not change since the previous emission are not decoded again. */
    fun observeGames(): Flow<List<Game>> = flow {
        var decoded = emptyMap<String, DecodedGame>()
        dao.observeGames().collect { rows ->
            val next = HashMap<String, DecodedGame>(rows.size)
            val games = ArrayList<Game>(rows.size)
            for (row in rows) {
                val cached = decoded[row.id]
                val game = if (cached != null && cached.payload == row.payload) cached.game else decode(row) ?: continue
                next[row.id] = DecodedGame(row.payload, game)
                games += game
            }
            decoded = next
            emit(games)
        }
    }

    fun observeGame(id: String): Flow<Game?> = dao.observeGame(id).map { it?.let(::decode) }.distinctUntilChanged()

    suspend fun game(id: String): Game? = dao.game(id)?.let(::decode)

    suspend fun putGame(game: Game) = dao.upsertGame(GameEntity(game.id, json.encodeToString(Game.serializer(), game)))

    suspend fun removeGame(id: String) = dao.deleteGame(id)

    suspend fun removeGamesWithoutPendingChange() = dao.deleteGamesWithoutPendingChange()

    /** Oldest first. */
    suspend fun pendingChanges(): List<PendingChange> = dao.pendingChanges().map { it.toModel() }

    suspend fun pendingChange(gameId: String): PendingChange? = dao.pendingChange(gameId)?.toModel()

    suspend fun putPendingChange(change: PendingChange) = dao.upsertPendingChange(change.toEntity())

    suspend fun removePendingChange(gameId: String) = dao.deletePendingChange(gameId)

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun syncState(): SyncState = dao.syncState()?.toModel() ?: SyncState()

    fun observeSyncState(): Flow<SyncState> = dao.observeSyncState().map { it?.toModel() ?: SyncState() }

    suspend fun putSyncState(state: SyncState) = dao.upsertSyncState(state.toEntity())

    /** Deletes all games, pending changes and sync progress and hands the store over to [owner]. */
    suspend fun wipe(owner: String?) = transaction {
        dao.deleteAllGames()
        dao.deleteAllPendingChanges()
        dao.upsertSyncState(SyncState(ownerUserId = owner).toEntity())
    }

    /** `null` for a payload this app version cannot read (logged), so one bad row does not hide the rest. */
    private fun decode(row: GameEntity): Game? = try {
        json.decodeFromString(Game.serializer(), row.payload)
    } catch (e: RuntimeException) {
        Log.e(TAG, "Unreadable stored game ${row.id}", e)
        null
    }

    private class DecodedGame(val payload: String, val game: Game)

    private companion object {
        const val TAG = "LocalGameStore"
    }
}

private fun PendingChangeEntity.toModel() = PendingChange(gameId, kind, fields, revision, attempted, queuedAt)

private fun PendingChange.toEntity() = PendingChangeEntity(gameId, kind, fields, revision, attempted, queuedAt)

private fun SyncStateEntity.toModel() = SyncState(ownerUserId, cursor, lastSyncedAt?.let(Instant::ofEpochMilli))

private fun SyncState.toEntity() = SyncStateEntity(
    ownerUserId = ownerUserId,
    cursor = cursor,
    lastSyncedAt = lastSyncedAt?.toEpochMilli(),
)
