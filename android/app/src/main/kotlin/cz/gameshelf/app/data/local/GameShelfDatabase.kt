package cz.gameshelf.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cz.gameshelf.app.data.sync.PendingChange
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * A game of the signed-in user. [payload] is the `Game` encoded with `ApiJson`: every query runs in
 * memory, so the columns do not need to be split up.
 */
@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val payload: String,
)

/** See [PendingChange]; [fields] is stored as a JSON array of field names. */
@Entity(tableName = "pending_changes")
data class PendingChangeEntity(
    @PrimaryKey val gameId: String,
    val kind: PendingChange.Kind,
    val fields: Set<String>,
    val revision: Long,
    val attempted: Boolean,
    val queuedAt: Long,
)

/**
 * Single row: whose data this is, where the change feed continues, when it last completed and which app
 * version (`versionCode`) stored the cursor – `null` for version 1, which did not record it.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val ownerUserId: String?,
    val cursor: String?,
    val lastSyncedAt: Long?,
    val appVersion: Int?,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}

@Dao
interface GameShelfDao {
    @Query("SELECT * FROM games")
    fun observeGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun observeGame(id: String): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun game(id: String): GameEntity?

    @Upsert
    suspend fun upsertGame(game: GameEntity)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteGame(id: String)

    @Query("DELETE FROM games WHERE id NOT IN (SELECT gameId FROM pending_changes)")
    suspend fun deleteGamesWithoutPendingChange()

    @Query("DELETE FROM games")
    suspend fun deleteAllGames()

    @Query("SELECT * FROM pending_changes ORDER BY queuedAt, gameId")
    suspend fun pendingChanges(): List<PendingChangeEntity>

    @Query("SELECT * FROM pending_changes WHERE gameId = :gameId")
    suspend fun pendingChange(gameId: String): PendingChangeEntity?

    @Query("SELECT COUNT(*) FROM pending_changes")
    fun observePendingCount(): Flow<Int>

    @Upsert
    suspend fun upsertPendingChange(change: PendingChangeEntity)

    @Query("DELETE FROM pending_changes WHERE gameId = :gameId")
    suspend fun deletePendingChange(gameId: String)

    @Query("DELETE FROM pending_changes")
    suspend fun deleteAllPendingChanges()

    @Query("SELECT * FROM sync_state WHERE id = ${SyncStateEntity.SINGLE_ROW_ID}")
    suspend fun syncState(): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE id = ${SyncStateEntity.SINGLE_ROW_ID}")
    fun observeSyncState(): Flow<SyncStateEntity?>

    @Upsert
    suspend fun upsertSyncState(state: SyncStateEntity)
}

internal class FieldSetConverter {
    private val serializer = ListSerializer(String.serializer())

    @TypeConverter
    fun toJson(fields: Set<String>): String = Json.encodeToString(serializer, fields.sorted())

    @TypeConverter
    fun fromJson(json: String): Set<String> = Json.decodeFromString(serializer, json).toSet()
}

/**
 * The offline copy of the collection plus the sync bookkeeping (offline-sync.md, "Local data").
 * Every schema change comes with a [Migration] in [MIGRATIONS]: the local data holds unsynced changes,
 * so it is never dropped.
 */
@Database(
    entities = [GameEntity::class, PendingChangeEntity::class, SyncStateEntity::class],
    version = 2,
)
@TypeConverters(FieldSetConverter::class)
abstract class GameShelfDatabase : RoomDatabase() {
    abstract fun dao(): GameShelfDao

    companion object {
        const val FILE_NAME = "game_shelf.db"

        /** 1 → 2: `sync_state.appVersion`. Left `NULL`, so the first sync after the update pulls everything. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_state ADD COLUMN appVersion INTEGER")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun create(context: Context, name: String = FILE_NAME): GameShelfDatabase =
            Room.databaseBuilder(context, GameShelfDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
