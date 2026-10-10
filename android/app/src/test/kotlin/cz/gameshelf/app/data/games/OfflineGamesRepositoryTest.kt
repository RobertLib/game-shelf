package cz.gameshelf.app.data.games

import android.app.Application
import androidx.room.Room
import app.cash.turbine.test
import cz.gameshelf.app.data.api.ApiJson
import cz.gameshelf.app.data.local.GameShelfDatabase
import cz.gameshelf.app.data.local.LocalGameStore
import cz.gameshelf.app.data.sync.PendingChange
import cz.gameshelf.app.data.sync.PendingChange.Kind
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.SaveGameRequest
import cz.gameshelf.app.domain.model.toSaveRequest
import cz.gameshelf.app.testing.GAME_WITH_UNKNOWN_VALUES_JSON
import cz.gameshelf.app.testing.testGame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OfflineGamesRepositoryTest {

    private lateinit var database: GameShelfDatabase
    private lateinit var store: LocalGameStore
    private var syncRequests = 0

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GameShelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = LocalGameStore(database)
    }

    @After
    fun tearDown() = database.close()

    private fun TestScope.repository() = OfflineGamesRepository(
        store = store,
        requestSync = { syncRequests++ },
        scope = backgroundScope,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
        newId = { NEW_ID },
        decodeDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    @Test
    fun `creating stores the game with a pending CREATE and asks for a sync`() = runTest {
        val repository = repository()

        val game = repository.createGame(REQUEST)

        assertEquals(NEW_ID, game.id)
        assertEquals(NOW, game.createdAt)
        assertEquals(NOW, game.updatedAt)
        assertEquals(game, store.game(NEW_ID))
        assertEquals(PendingChange(NEW_ID, Kind.CREATE, queuedAt = NOW.toEpochMilli()), store.pendingChange(NEW_ID))
        assertEquals(1, syncRequests)
    }

    @Test
    fun `an edit that changes nothing records nothing`() = runTest {
        val repository = repository()
        val stored = testGame("g1")
        store.putGame(stored)

        val result = repository.updateGame("g1", stored.toSaveRequest())

        assertEquals(stored, result)
        assertNull(store.pendingChange("g1"))
        assertEquals(0, syncRequests)
    }

    @Test
    fun `an edit saves only the changed fields and keeps values unknown to this version`() = runTest {
        val repository = repository()
        val stored = testGame("g1").copy(status = CollectionStatus.UNKNOWN)
        store.putGame(stored)

        repository.updateGame("g1", stored.toSaveRequest().copy(title = "Renamed"))

        assertEquals(stored.copy(title = "Renamed", updatedAt = NOW), store.game("g1"))
        assertEquals(setOf("title"), store.pendingChange("g1")?.fields)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `values unknown to this version survive the stored payload and an edit of other fields`() = runTest {
        val repository = repository()
        val game = ApiJson.decodeFromString<Game>(GAME_WITH_UNKNOWN_VALUES_JSON)
        store.putGame(game)

        // Stored as the API sent them.
        val payload = checkNotNull(database.dao().game(game.id)).payload
        assertTrue(payload, "\"platform\":\"PLAYSTATION_6\"" in payload && "\"status\":\"BORROWED\"" in payload)
        assertFalse(payload, "unknownEnumValues" in payload)
        assertEquals(game.unknownEnumValues, store.game(game.id)?.unknownEnumValues)

        // The form starts from the stored game and saves only what the user changed.
        val opened = checkNotNull(store.game(game.id)).toSaveRequest()
        repository.updateGame(game.id, opened.copy(title = "Renamed"), opened)

        val updated = checkNotNull(store.game(game.id))
        assertEquals("Renamed", updated.title)
        assertEquals(Platform.OTHER, updated.platform)
        assertEquals(game.unknownEnumValues, updated.unknownEnumValues)
        assertEquals(setOf("title"), store.pendingChange(game.id)?.fields)
    }

    @Test
    fun `a value the user picks replaces the stored one this version doesn't know`() = runTest {
        val repository = repository()
        val game = ApiJson.decodeFromString<Game>(GAME_WITH_UNKNOWN_VALUES_JSON)
        store.putGame(game)
        val opened = checkNotNull(store.game(game.id)).toSaveRequest()

        repository.updateGame(game.id, opened.copy(status = CollectionStatus.FOR_SALE), opened)

        val updated = checkNotNull(store.game(game.id))
        assertEquals(CollectionStatus.FOR_SALE, updated.status)
        assertEquals(mapOf("platform" to "PLAYSTATION_6"), updated.unknownEnumValues)
        assertEquals(setOf("status"), store.pendingChange(game.id)?.fields)
        assertTrue("\"status\":\"FOR_SALE\"" in checkNotNull(database.dao().game(game.id)).payload)
    }

    @Test
    fun `an edit keeps values a sync brought in while the form was open`() = runTest {
        val repository = repository()
        val opened = testGame("g1").copy(title = "Doom", rating = 5)
        val synced = opened.copy(rating = 9)
        store.putGame(synced)

        val base = opened.toSaveRequest()
        repository.updateGame("g1", base.copy(title = "Doom II"), base)

        assertEquals(synced.copy(title = "Doom II", updatedAt = NOW), store.game("g1"))
        assertEquals(setOf("title"), store.pendingChange("g1")?.fields)
    }

    @Test
    fun `deleting a synced game removes it at once and queues a DELETE`() = runTest {
        val repository = repository()
        store.putGame(testGame("g1"))

        repository.deleteGame("g1")

        assertNull(store.game("g1"))
        assertEquals(Kind.DELETE, store.pendingChange("g1")?.kind)
    }

    @Test
    fun `games follow the database and changes report local actions`() = runTest {
        val repository = repository()

        repository.changes.test {
            val game = repository.createGame(REQUEST)
            assertEquals(GameChange.Created(game), awaitItem())
            assertEquals(listOf(game), repository.games.first { it.isNotEmpty() })

            val favorite = repository.setFavorite(game.id, true)
            assertEquals(GameChange.Updated(favorite!!), awaitItem())

            repository.deleteGame(game.id)
            assertEquals(GameChange.Deleted(game.id), awaitItem())
            assertEquals(emptyList<Any>(), repository.games.first { it.isEmpty() })
        }
    }

    @Test
    fun `editing or deleting a missing game does nothing`() = runTest {
        val repository = repository()

        assertNull(repository.updateGame("missing", REQUEST))
        repository.deleteGame("missing")

        assertEquals(emptyList<PendingChange>(), store.pendingChanges())
        assertEquals(0, syncRequests)
    }

    private companion object {
        const val NEW_ID = "4f9c2b1e-8d7a-4c3b-9e2f-1a0b9c8d7e6f"
        val NOW: Instant = Instant.parse("2026-10-07T12:00:00Z")
        val REQUEST = SaveGameRequest(title = "Doom", platform = Platform.PC)
    }
}
