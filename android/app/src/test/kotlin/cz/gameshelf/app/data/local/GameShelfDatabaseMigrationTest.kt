package cz.gameshelf.app.data.local

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import cz.gameshelf.app.data.api.ApiJson
import cz.gameshelf.app.data.sync.PendingChange
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.testing.testGame
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/** Opens databases left by older app versions, built from the exported schemas in `app/schemas`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GameShelfDatabaseMigrationTest {

    private val context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        context.deleteDatabase(NAME)
    }

    @Test
    fun `version 1 keeps all data and its cursor is marked as stored by an older version`() = runTest {
        val game = testGame("10000000-0000-4000-8000-000000000001", title = "Banjo-Kazooie")
        createDatabase(version = 1) { db ->
            db.insert("games", null, ContentValues().apply {
                put("id", game.id)
                put("payload", ApiJson.encodeToString(Game.serializer(), game))
            })
            db.insert("pending_changes", null, ContentValues().apply {
                put("gameId", game.id)
                put("kind", "UPDATE")
                put("fields", """["rating","title"]""")
                put("revision", 3)
                put("attempted", 1)
                put("queuedAt", 1_000L)
            })
            db.insert("sync_state", null, ContentValues().apply {
                put("id", SyncStateEntity.SINGLE_ROW_ID)
                put("ownerUserId", "user-1")
                put("cursor", "42")
                put("lastSyncedAt", 2_000L)
            })
        }

        // The app's own builder: it must know the migration (Room checks the result against the current schema).
        val database = GameShelfDatabase.create(context, NAME)
        try {
            val store = LocalGameStore(database)
            assertEquals(game, store.game(game.id))
            assertEquals(
                PendingChange(game.id, PendingChange.Kind.UPDATE, setOf("title", "rating"), 3, true, 1_000L),
                store.pendingChange(game.id),
            )
            assertEquals(SyncState("user-1", "42", Instant.ofEpochMilli(2_000L), appVersion = null), store.syncState())
        } finally {
            database.close()
        }
    }

    /** A database as the app version with schema [version] left it. */
    private fun createDatabase(version: Int, fill: (SQLiteDatabase) -> Unit) {
        val schemaFile = File(SCHEMAS, "$version.json")
        assertTrue("missing exported schema ${schemaFile.absolutePath}", schemaFile.exists())
        val schema = Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
        val file = context.getDatabasePath(NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            for (entity in schema.getValue("entities").jsonArray.map { it.jsonObject }) {
                val table = entity.getValue("tableName").jsonPrimitive.content
                db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
            schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            fill(db)
            db.version = version
        }
    }

    private companion object {
        const val NAME = "migration-test.db"

        /** Unit tests run in the module directory. */
        val SCHEMAS = File("schemas/${GameShelfDatabase::class.java.name}")
    }
}
