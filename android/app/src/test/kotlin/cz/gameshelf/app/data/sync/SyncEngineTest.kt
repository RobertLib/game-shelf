package cz.gameshelf.app.data.sync

import android.app.Application
import androidx.room.Room
import cz.gameshelf.app.data.api.ApiJson
import cz.gameshelf.app.data.games.OfflineGamesRepository
import cz.gameshelf.app.data.local.GameEntity
import cz.gameshelf.app.data.local.GameShelfDatabase
import cz.gameshelf.app.data.local.LocalGameStore
import cz.gameshelf.app.data.local.SyncState
import cz.gameshelf.app.data.sync.PendingChange.Kind
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.ErrorCode
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.SaveGameRequest
import cz.gameshelf.app.domain.model.toSaveRequest
import cz.gameshelf.app.testing.FULL_GAME_JSON
import cz.gameshelf.app.testing.FakeGamesApi
import cz.gameshelf.app.testing.FakeGamesApi.Call
import cz.gameshelf.app.testing.httpError
import cz.gameshelf.app.testing.httpErrorWithBody
import cz.gameshelf.app.testing.testGame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The sync rules of offline-sync.md against an in-memory Room database and a fake API. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SyncEngineTest {

    private lateinit var database: GameShelfDatabase
    private lateinit var store: LocalGameStore
    private val api = FakeGamesApi(Clock.fixed(SERVER_TIME, ZoneOffset.UTC))
    private val userId = MutableStateFlow<String?>(USER)
    private val connected = MutableStateFlow(true)
    private val foreground = MutableStateFlow(false)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GameShelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = LocalGameStore(database)
    }

    @After
    fun tearDown() = database.close()

    private data class Harness(val engine: SyncEngine, val repository: OfflineGamesRepository)

    /**
     * A signed-in user whose (empty) local data is already adopted. Local changes do not trigger runs
     * here and the engine is not started, so every test decides when a run happens.
     */
    private suspend fun TestScope.signedIn(): Harness {
        store.wipe(owner = USER)
        val engine = engine()
        val repository = OfflineGamesRepository(store, requestSync = {}, scope = backgroundScope, clock = LOCAL_CLOCK)
        return Harness(engine, repository)
    }

    private fun TestScope.engine(appVersion: Int = APP_VERSION) =
        SyncEngine(api, store, userId, connected, foreground, backgroundScope, appVersion, LOCAL_CLOCK)

    @Test
    fun `a created game is pushed and its pending change removed`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertEquals(Call("POST", game.id, fields = GameFields.ALL), api.calls.first())
        assertNull(store.pendingChange(game.id))
        assertEquals(api.serverGame(game.id), store.game(game.id))
        assertEquals(SERVER_TIME, store.game(game.id)?.createdAt)
        assertEquals(LOCAL_TIME, store.syncState().lastSyncedAt)
    }

    @Test
    fun `a replayed create pushes the fields edited after the lost first attempt`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        api.afterCall = { if (it.name == "POST") throw IOException("response lost") }
        assertEquals(ApiResult.Failure(AppError.Network), engine.syncNow())
        assertEquals(true, store.pendingChange(game.id)?.attempted)

        api.afterCall = {}
        repository.updateGame(game.id, REQUEST.copy(title = "Edited", rating = 7))
        assertEquals(setOf("title", "rating"), store.pendingChange(game.id)?.fields)
        api.calls.clear()

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertEquals(listOf("POST", "PATCH", "changes"), api.calls.map { it.name })
        assertEquals(setOf("title", "rating"), api.calls[1].fields)
        assertEquals("Edited", api.serverGame(game.id)?.title)
        assertEquals(7, api.serverGame(game.id)?.rating)
        assertNull(store.pendingChange(game.id))
        assertEquals(api.serverGame(game.id), store.game(game.id))
    }

    @Test
    fun `a replayed create edited while in flight pushes only the remembered fields, in the same run`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        api.afterCall = { if (it.name == "POST") throw IOException("response lost") }
        engine.syncNow()
        api.afterCall = {}
        // Changed on another device since the first attempt; that change must survive.
        api.putOnServer(api.serverGame(game.id)!!.copy(notes = "Edited elsewhere"))
        repository.updateGame(game.id, REQUEST.copy(title = "Edited"))
        api.calls.clear()

        val gate = api.hold("POST")
        val sync = async { engine.syncNow() }
        gate.reached.await()
        repository.updateGame(game.id, REQUEST.copy(title = "Edited", rating = 7))
        gate.release.complete(Unit)

        assertEquals(ApiResult.Success(Unit), sync.await())
        assertEquals(listOf("POST", "PATCH", "changes"), api.calls.map { it.name })
        assertEquals(setOf("title", "rating"), api.calls[1].fields)
        val server = api.serverGame(game.id)!!
        assertEquals("Edited", server.title)
        assertEquals(7, server.rating)
        assertEquals("Edited elsewhere", server.notes)
        assertNull(store.pendingChange(game.id))
        assertEquals(server, store.game(game.id))
    }

    @Test
    fun `a replayed create of a game deleted on another device meanwhile removes it`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        api.afterCall = { if (it.name == "POST") throw IOException("response lost") }
        engine.syncNow()
        api.afterCall = {}
        api.deleteOnServer(game.id)

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertNull(store.game(game.id))
        assertNull(store.pendingChange(game.id))
    }

    @Test
    fun `edits of a synced game are coalesced into one PATCH of the changed fields`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()

        repository.updateGame(game.id, REQUEST.copy(title = "Edited"))
        repository.setFavorite(game.id, true)
        val pending = store.pendingChange(game.id)
        assertEquals(Kind.UPDATE, pending?.kind)
        assertEquals(setOf("title", "favorite"), pending?.fields)
        assertEquals(2L, pending?.revision)
        api.calls.clear()

        engine.syncNow()

        assertEquals(Call("PATCH", game.id, fields = setOf("title", "favorite")), api.calls.first())
        assertNull(store.pendingChange(game.id))
        assertEquals("Edited", api.serverGame(game.id)?.title)
        assertEquals(true, api.serverGame(game.id)?.favorite)
    }

    @Test
    fun `an edit of a game with a value unknown to this version sends only the edited field`() = runTest {
        val (engine, repository) = signedIn()
        val game = testGame("10000000-0000-4000-8000-000000000004")
        api.putOnServer(game)
        engine.syncNow()
        // The server has a status this version doesn't know.
        store.putGame(game.copy(status = CollectionStatus.UNKNOWN))
        val opened = store.game(game.id)!!.toSaveRequest()
        repository.updateGame(game.id, opened.copy(title = "Renamed"), base = opened)
        api.calls.clear()

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertEquals(Call("PATCH", game.id, fields = setOf("title")), api.calls.first())
        assertEquals(CollectionStatus.OWNED, api.serverGame(game.id)?.status)
    }

    @Test
    fun `deleting a game that was never pushed leaves nothing to push`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)

        repository.deleteGame(game.id)
        engine.syncNow()

        assertNull(store.game(game.id))
        assertNull(store.pendingChange(game.id))
        assertEquals(listOf("changes"), api.calls.map { it.name })
    }

    @Test
    fun `a deleted game is deleted on the server, and an already missing one counts as deleted`() = runTest {
        val (engine, repository) = signedIn()
        val first = repository.createGame(REQUEST)
        val second = repository.createGame(REQUEST.copy(title = "Second"))
        engine.syncNow()
        api.forgetOnServer(second.id)

        repository.deleteGame(first.id)
        repository.deleteGame(second.id)
        assertEquals(Kind.DELETE, store.pendingChange(first.id)?.kind)

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertNull(api.serverGame(first.id))
        assertEquals(emptyList<PendingChange>(), store.pendingChanges())
    }

    @Test
    fun `pull keeps the local values of fields still waiting to be pushed`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()
        api.putOnServer(api.serverGame(game.id)!!.copy(title = "Server title", notes = "Edited elsewhere"))

        val gate = api.hold("changes")
        val sync = async { engine.syncNow() }
        gate.reached.await()
        repository.updateGame(game.id, REQUEST.copy(title = "Local title"))
        gate.release.complete(Unit)

        assertEquals(ApiResult.Success(Unit), sync.await())
        val stored = store.game(game.id)
        assertEquals("Local title", stored?.title)
        assertEquals("Edited elsewhere", stored?.notes)
        assertEquals(setOf("title"), store.pendingChange(game.id)?.fields)
    }

    @Test
    fun `a pulled game doesn't replace a pending update of a stored game this version can't read`() = runTest {
        val (engine, _) = signedIn()
        val id = "10000000-0000-4000-8000-000000000006"
        val unreadable = GameEntity(id, """{"id":"$id","title":"Edited offline by a newer version"}""")
        database.dao().upsertGame(unreadable)
        store.putPendingChange(PendingChange(id, Kind.UPDATE, setOf("title"), queuedAt = LOCAL_TIME.toEpochMilli()))
        api.putOnServer(testGame(id, title = "Server title"))

        // The push of the unreadable game fails, the pull goes on.
        assertEquals(ApiResult.Failure(AppError.Unexpected), engine.syncNow())

        assertEquals(unreadable, database.dao().game(id))
        assertEquals(Kind.UPDATE, store.pendingChange(id)?.kind)
        assertNotNull(store.syncState().lastSyncedAt)
    }

    @Test
    fun `a game this version can't decode is skipped, and the rest of the page and its cursor applied`() = runTest {
        val (engine, _) = signedIn()
        val readable = testGame("10000000-0000-4000-8000-000000000001")
        val broken = testGame("10000000-0000-4000-8000-000000000002", title = "Stored")
        api.putOnServer(readable)
        api.putOnServer(broken)
        engine.syncNow()
        api.putOnServer(readable.copy(title = "Changed elsewhere"))
        api.putOnServer(broken.copy(title = "Changed by a newer version"))
        api.undecodable += broken.id

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertEquals("Changed elsewhere", store.game(readable.id)?.title)
        // Its local copy stays as it is.
        assertEquals("Stored", store.game(broken.id)?.title)
        assertEquals("4", store.syncState().cursor)

        // A full pull (e.g. after an app update) doesn't push it back: the server has it.
        val updated = engine(appVersion = APP_VERSION + 1)
        assertEquals(ApiResult.Success(Unit), updated.syncNow())
        advanceUntilIdle()
        assertEquals(emptyList<String>(), store.unconfirmedGameIds())
        assertEquals(emptyList<PendingChange>(), store.pendingChanges())
        assertTrue(api.calls.none { it.name == "POST" })
    }

    @Test
    fun `values this version doesn't know are kept, never patched, and pushed back verbatim`() = runTest {
        val (engine, repository) = signedIn()
        val game = ApiJson.decodeFromString<Game>(
            FULL_GAME_JSON
                .replace("\"platform\": \"N64\"", "\"platform\": \"PLAYSTATION_6\"")
                .replace("\"status\": \"OWNED\"", "\"status\": \"BORROWED\""),
        )
        api.putOnServer(game)
        engine.syncNow()
        val unknown = mapOf("platform" to "PLAYSTATION_6", "status" to "BORROWED")
        assertEquals(unknown, store.game(game.id)?.unknownEnumValues)
        assertEquals(Platform.OTHER, store.game(game.id)?.platform)
        assertEquals(CollectionStatus.UNKNOWN, store.game(game.id)?.status)

        // Edited in the form, which shows them as "Other" and "Unknown".
        val opened = store.game(game.id)!!.toSaveRequest()
        repository.updateGame(game.id, opened.copy(title = "Renamed"), base = opened)
        api.calls.clear()
        assertEquals(ApiResult.Success(Unit), engine.syncNow())
        assertEquals(setOf("title"), api.bodies.last().keys)
        assertEquals(unknown, api.serverGame(game.id)?.unknownEnumValues)

        // The server lost it (restored from a backup): pushed back whole, with the values as the API sent them.
        api.forgetOnServer(game.id)
        val renamed = opened.copy(title = "Renamed")
        repository.updateGame(game.id, renamed.copy(rating = 3), base = renamed)
        assertEquals(ApiResult.Success(Unit), engine.syncNow())
        val created = api.bodies.last()
        assertEquals(JsonPrimitive("PLAYSTATION_6"), created["platform"])
        assertEquals(JsonPrimitive("BORROWED"), created["status"])
        assertEquals("Renamed", api.serverGame(game.id)?.title)
        assertEquals(unknown, api.serverGame(game.id)?.unknownEnumValues)
        assertEquals(unknown, store.game(game.id)?.unknownEnumValues)
    }

    @Test
    fun `a deletion pulled from the server wins over a pending update`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()
        api.deleteOnServer(game.id)

        val gate = api.hold("changes")
        val sync = async { engine.syncNow() }
        gate.reached.await()
        repository.updateGame(game.id, REQUEST.copy(title = "Local title"))
        gate.release.complete(Unit)

        assertEquals(ApiResult.Success(Unit), sync.await())
        assertNull(store.game(game.id))
        assertNull(store.pendingChange(game.id))
    }

    @Test
    fun `an update of a game deleted on another device removes it`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()
        api.deleteOnServer(game.id)
        repository.updateGame(game.id, REQUEST.copy(title = "Edited"))

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertTrue(Call("PATCH", game.id, fields = setOf("title")) in api.calls)
        assertNull(store.game(game.id))
        assertNull(store.pendingChange(game.id))
    }

    @Test
    fun `a rejected update is undone with the server version and reported until shown`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()
        repository.updateGame(game.id, REQUEST.copy(title = "Rejected"))
        api.beforeCall = { if (it.name == "PATCH") throw httpError(400, "VALIDATION_FAILED") }

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertTrue(Call("GET", game.id) in api.calls)
        assertEquals(REQUEST.title, store.game(game.id)?.title)
        assertEquals(api.serverGame(game.id), store.game(game.id))
        assertNull(store.pendingChange(game.id))
        assertEquals(SyncEvent.ChangesRejected, engine.events.first())

        engine.onChangesRejectedShown()
        store.observeSyncState().first { !it.reportRejectedChanges }
    }

    @Test
    fun `a rejected update of a game the server has no record of is pushed as a new game`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()
        // Restored from a backup older than the game; the server validates the body before it looks the game up.
        api.forgetOnServer(game.id)
        repository.updateGame(game.id, REQUEST.copy(title = "Edited"))
        api.beforeCall = { if (it.name == "PATCH") throw httpError(400, "VALIDATION_FAILED") }
        api.calls.clear()

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertEquals(listOf("PATCH", "GET", "POST", "changes"), api.calls.map { it.name })
        assertEquals(GameFields.ALL, api.calls[2].fields)
        assertEquals("Edited", api.serverGame(game.id)?.title)
        assertEquals(api.serverGame(game.id), store.game(game.id))
        assertNull(store.pendingChange(game.id))
        // Nothing was undone.
        assertFalse(store.syncState().reportRejectedChanges)
    }

    @Test
    fun `a rejected delete of a game the server has no record of is dropped`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()
        api.forgetOnServer(game.id)
        repository.deleteGame(game.id)
        api.beforeCall = { if (it.name == "DELETE") throw httpError(409, "CONFLICT") }
        api.calls.clear()

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertEquals(listOf("DELETE", "GET", "changes"), api.calls.map { it.name })
        assertNull(store.game(game.id))
        assertNull(store.pendingChange(game.id))
        assertFalse(store.syncState().reportRejectedChanges)
    }

    @Test
    fun `a rejected update edited meanwhile is not undone but sent again, and undone if rejected again`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()
        repository.updateGame(game.id, REQUEST.copy(title = "Rejected"))
        val rejection = rejectNext("PATCH", httpError(400, "VALIDATION_FAILED"))
        api.calls.clear()

        val sync = async { engine.syncNow() }
        rejection.reached.await()
        // The newer edit may fix the rejected value; undoing would lose it.
        repository.updateGame(game.id, REQUEST.copy(title = "Rejected", rating = 7))
        rejection.release.complete(Unit)

        assertEquals(ApiResult.Failure(AppError.Api(400, ErrorCode.VALIDATION_FAILED)), sync.await())
        assertTrue(api.calls.none { it.name == "GET" })
        assertEquals("Rejected", store.game(game.id)?.title)
        assertEquals(7, store.game(game.id)?.rating)
        assertEquals(setOf("title", "rating"), store.pendingChange(game.id)?.fields)
        assertFalse(store.syncState().reportRejectedChanges)

        // Sent again in the next run; rejected again unchanged, it is undone.
        api.calls.clear()
        api.beforeCall = { if (it.name == "PATCH") throw httpError(400, "VALIDATION_FAILED") }
        assertEquals(ApiResult.Success(Unit), engine.syncNow())
        assertEquals(Call("PATCH", game.id, fields = setOf("title", "rating")), api.calls.first())
        assertEquals(REQUEST.title, store.game(game.id)?.title)
        assertNull(store.game(game.id)?.rating)
        assertNull(store.pendingChange(game.id))
        assertTrue(store.syncState().reportRejectedChanges)
    }

    @Test
    fun `a rejected change is reported also after a restart`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()
        repository.updateGame(game.id, REQUEST.copy(title = "Rejected"))
        api.beforeCall = { if (it.name == "PATCH") throw httpError(409, "CONFLICT") }
        engine.syncNow()

        // The process died before any screen showed it.
        val restarted = engine()

        assertEquals(SyncEvent.ChangesRejected, restarted.events.first())
        restarted.onChangesRejectedShown()
        store.observeSyncState().first { !it.reportRejectedChanges }
    }

    @Test
    fun `an update the server doesn't know the game of is pushed as a new game in the same run`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()
        // The server was restored from a backup older than the game.
        api.forgetOnServer(game.id)
        repository.updateGame(game.id, REQUEST.copy(title = "Edited"))
        api.calls.clear()

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertEquals(listOf("PATCH", "POST", "changes"), api.calls.map { it.name })
        assertEquals(GameFields.ALL, api.calls[1].fields)
        assertEquals("Edited", api.serverGame(game.id)?.title)
        assertNull(store.pendingChange(game.id))
        assertEquals(api.serverGame(game.id), store.game(game.id))
    }

    @Test
    fun `a game deleted while its update learns the server doesn't know it is deleted, not created`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()
        api.forgetOnServer(game.id)
        repository.updateGame(game.id, REQUEST.copy(title = "Edited"))
        api.calls.clear()

        val gate = api.hold("PATCH")
        val sync = async { engine.syncNow() }
        gate.reached.await()
        repository.deleteGame(game.id)
        gate.release.complete(Unit)

        assertEquals(ApiResult.Success(Unit), sync.await())
        assertEquals(listOf("PATCH", "DELETE", "changes"), api.calls.map { it.name })
        assertNull(api.serverGame(game.id))
        assertNull(store.pendingChange(game.id))
    }

    @Test
    fun `a delete made while an update is rejected stays queued and is pushed`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()
        repository.updateGame(game.id, REQUEST.copy(title = "Rejected"))
        val rejection = rejectNext("PATCH", httpError(400, "VALIDATION_FAILED"))

        val sync = async { engine.syncNow() }
        rejection.reached.await()
        repository.deleteGame(game.id)
        rejection.release.complete(Unit)

        assertEquals(ApiResult.Success(Unit), sync.await())
        assertNull(store.game(game.id))
        assertEquals(Kind.DELETE, store.pendingChange(game.id)?.kind)
        // Nothing the user sees was undone: they deleted the game themselves.
        assertFalse(store.syncState().reportRejectedChanges)

        assertEquals(ApiResult.Success(Unit), engine.syncNow())
        assertNull(api.serverGame(game.id))
        assertNull(store.pendingChange(game.id))
    }

    @Test
    fun `a delete made while a create is rejected stays queued and is pushed`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        val rejection = rejectNext("POST", httpError(409, "CONFLICT"))

        val sync = async { engine.syncNow() }
        rejection.reached.await()
        repository.deleteGame(game.id)
        rejection.release.complete(Unit)

        // The rejected CREATE failed; the DELETE made meanwhile is pushed next time.
        assertEquals(ApiResult.Failure(AppError.Api(409, ErrorCode.CONFLICT)), sync.await())
        assertNull(store.game(game.id))
        assertEquals(Kind.DELETE, store.pendingChange(game.id)?.kind)
        assertFalse(store.syncState().reportRejectedChanges)

        api.calls.clear()
        assertEquals(ApiResult.Success(Unit), engine.syncNow())
        // The server never had the game: it answers 204 and keeps a tombstone.
        assertEquals(Call("DELETE", game.id), api.calls.first())
        assertNull(store.pendingChange(game.id))
    }

    @Test
    fun `a rejected create keeps the game and is sent again in later runs`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        api.beforeCall = { if (it.name == "POST") throw httpError(400, "VALIDATION_FAILED") }

        assertEquals(ApiResult.Failure(AppError.Api(400, ErrorCode.VALIDATION_FAILED)), engine.syncNow())

        assertEquals(game, store.game(game.id))
        assertEquals(Kind.CREATE, store.pendingChange(game.id)?.kind)
        assertFalse(store.syncState().reportRejectedChanges)

        // E.g. the server was updated meanwhile.
        api.beforeCall = {}
        assertEquals(ApiResult.Success(Unit), engine.syncNow())
        assertEquals(api.serverGame(game.id), store.game(game.id))
        assertNull(store.pendingChange(game.id))
    }

    @Test
    fun `a change that fails doesn't hold up the others or the pull`() = runTest {
        val (engine, repository) = signedIn()
        val failing = repository.createGame(REQUEST.copy(title = "Failing"))
        val edited = repository.createGame(REQUEST)
        engine.syncNow()
        val elsewhere = testGame("10000000-0000-4000-8000-000000000007")

        listOf(
            httpError(500, "INTERNAL_ERROR") to AppError.Api(500, ErrorCode.INTERNAL_ERROR),
            httpError(422, "SOMETHING_NEW") to AppError.Api(422, ErrorCode.UNKNOWN),
            httpError(404, "NOT_FOUND") to AppError.Api(404, ErrorCode.NOT_FOUND),
        ).forEachIndexed { i, (failure, error) ->
            repository.updateGame(failing.id, REQUEST.copy(title = "Failing", rating = i + 1))
            repository.updateGame(edited.id, REQUEST.copy(rating = i + 1))
            api.putOnServer(elsewhere.copy(rating = i + 1))
            api.beforeCall = { if (it.name == "PATCH" && it.id == failing.id) throw failure }

            // The run counts as failed (and is retried), but went on.
            assertEquals(ApiResult.Failure(error), engine.syncNow())

            assertEquals(Kind.UPDATE, store.pendingChange(failing.id)?.kind)
            assertEquals(i + 1, store.game(failing.id)?.rating)
            assertNull(store.pendingChange(edited.id))
            assertEquals(i + 1, api.serverGame(edited.id)?.rating)
            assertEquals(i + 1, store.game(elsewhere.id)?.rating)
        }
        assertTrue(api.calls.none { it.name == "GET" })
    }

    @Test
    fun `when the server can't be used the run ends at once, without a pull`() = runTest {
        val (engine, repository) = signedIn()
        val first = repository.createGame(REQUEST.copy(title = "First"))
        val second = repository.createGame(REQUEST.copy(title = "Second"))
        engine.syncNow()
        repository.updateGame(first.id, REQUEST.copy(title = "First", rating = 1))
        repository.updateGame(second.id, REQUEST.copy(title = "Second", rating = 2))

        listOf(
            httpError(429, "TOO_MANY_REQUESTS"),
            httpError(408, "REQUEST_TIMEOUT"),
            httpError(401, "UNAUTHORIZED"),
            httpErrorWithBody(502, "<html>Bad gateway</html>"),
            IOException("timeout"),
        ).forEach { failure ->
            api.calls.clear()
            api.beforeCall = { if (it.name == "PATCH") throw failure }

            assertTrue(engine.syncNow() is ApiResult.Failure)

            assertEquals(listOf("PATCH"), api.calls.map { it.name })
            assertEquals(2, store.pendingChanges().size)
        }
    }

    @Test
    fun `responses without an API error body, and 408, are temporary and undo nothing`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()
        repository.updateGame(game.id, REQUEST.copy(title = "Edited"))
        api.calls.clear()

        listOf(
            httpErrorWithBody(403, "<html><body>Blocked by the firewall</body></html>"),
            httpErrorWithBody(404, "Not Found", "text/plain"),
            httpErrorWithBody(400, """{"error":"bad request"}""", "application/json"),
            httpError(408, "REQUEST_TIMEOUT"),
        ).forEach { failure ->
            api.beforeCall = { if (it.name == "PATCH") throw failure }

            val result = engine.syncNow()

            assertTrue(result is ApiResult.Failure)
            assertEquals("Edited", store.game(game.id)?.title)
            assertEquals(Kind.UPDATE, store.pendingChange(game.id)?.kind)
        }
        assertTrue(api.calls.none { it.name == "GET" })

        api.beforeCall = {}
        assertEquals(ApiResult.Success(Unit), engine.syncNow())
        assertEquals("Edited", api.serverGame(game.id)?.title)
    }

    @Test
    fun `a 404 counts as deleted only with GAME_NOT_FOUND`() = runTest {
        val (engine, repository) = signedIn()
        val edited = repository.createGame(REQUEST)
        val deleted = repository.createGame(REQUEST.copy(title = "Second"))
        engine.syncNow()

        // A misrouted request: says nothing about the game.
        repository.deleteGame(deleted.id)
        api.beforeCall = { if (it.name == "DELETE") throw httpErrorWithBody(404, "<html>Not found</html>") }
        assertTrue(engine.syncNow() is ApiResult.Failure)
        assertEquals(Kind.DELETE, store.pendingChange(deleted.id)?.kind)

        // Another code from the API fails just this change, which stays queued.
        api.beforeCall = { if (it.name == "DELETE") throw httpError(404, "NOT_FOUND") }
        assertEquals(ApiResult.Failure(AppError.Api(404, ErrorCode.NOT_FOUND)), engine.syncNow())
        assertEquals(Kind.DELETE, store.pendingChange(deleted.id)?.kind)
        assertNull(store.game(deleted.id))

        // The same for an update: the game is not removed.
        repository.updateGame(edited.id, REQUEST.copy(title = "Edited"))
        api.beforeCall = { if (it.name == "PATCH") throw httpErrorWithBody(404, "Not Found", "text/plain") }
        assertTrue(engine.syncNow() is ApiResult.Failure)
        assertEquals("Edited", store.game(edited.id)?.title)
        assertEquals(Kind.UPDATE, store.pendingChange(edited.id)?.kind)
    }

    @Test
    fun `a pending change of a game missing locally fails, but not the rest of the run`() = runTest {
        val (engine, _) = signedIn()
        val created = "10000000-0000-4000-8000-000000000005"
        store.putPendingChange(PendingChange.create(created, LOCAL_TIME.toEpochMilli()))

        assertEquals(ApiResult.Failure(AppError.Unexpected), engine.syncNow())
        assertEquals(PendingChange.create(created, LOCAL_TIME.toEpochMilli()), store.pendingChange(created))

        // A stored game this version cannot read is missing as well.
        store.removePendingChange(created)
        val edited = "10000000-0000-4000-8000-000000000006"
        database.dao().upsertGame(GameEntity(edited, """{"id":"$edited","title":"Unreadable"}"""))
        store.putPendingChange(PendingChange(edited, Kind.UPDATE, setOf("title"), queuedAt = LOCAL_TIME.toEpochMilli()))

        assertEquals(ApiResult.Failure(AppError.Unexpected), engine.syncNow())
        assertEquals(Kind.UPDATE, store.pendingChange(edited)?.kind)
        // Nothing was sent for them; both runs still pulled.
        assertEquals(listOf("changes", "changes"), api.calls.map { it.name })
    }

    @Test
    fun `a network failure keeps the change and shows the app offline`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        api.beforeCall = { throw IOException("no route to host") }

        assertEquals(ApiResult.Failure(AppError.Network), engine.syncNow())

        assertNotNull(store.game(game.id))
        assertEquals(true, store.pendingChange(game.id)?.attempted)
        val offline = engine.status.first { it.isOffline }
        assertEquals(1, offline.pendingCount)
        assertEquals(AppError.Network, offline.lastError)

        api.beforeCall = {}
        assertEquals(ApiResult.Success(Unit), engine.syncNow())
        engine.status.first { !it.isOffline && it.pendingCount == 0 && it.hasCompletedInitialSync }
    }

    @Test
    fun `server errors are temporary and keep the change`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        api.beforeCall = { throw httpError(503, "INTERNAL_ERROR") }

        val result = engine.syncNow()

        assertEquals(503, ((result as ApiResult.Failure).error as AppError.Api).statusCode)
        assertEquals(Kind.CREATE, store.pendingChange(game.id)?.kind)
        assertFalse(engine.status.first { it.lastError != null }.isOffline)
    }

    @Test
    fun `a sync reset keeps every game and pushes back those the server doesn't have`() = runTest {
        val (engine, repository) = signedIn()
        val kept = testGame("10000000-0000-4000-8000-000000000001", platform = Platform.PS5)
        val vanished = testGame("10000000-0000-4000-8000-000000000002", platform = Platform.PS4)
        api.putOnServer(kept)
        api.putOnServer(vanished)
        engine.syncNow()
        // The server was restored from a backup that does not have the second game.
        api.forgetOnServer(vanished.id)
        api.calls.clear()
        val reset = resetNextPull()
        val push = api.hold("POST")

        val sync = async { engine.syncNow() }
        reset.reached.await()
        val created = repository.createGame(REQUEST)
        reset.release.complete(Unit)

        assertEquals(ApiResult.Success(Unit), sync.await())
        assertEquals(listOf("2", null), api.calls.filter { it.name == "changes" }.map { it.cursor })
        assertEquals(kept, store.game(kept.id))
        assertEquals(vanished, store.game(vanished.id))
        assertNotNull(store.game(created.id))
        assertEquals(emptyList<String>(), store.unconfirmedGameIds())

        // Another run follows at once and pushes it back as a new game.
        push.reached.await()
        assertEquals(Kind.CREATE, store.pendingChange(vanished.id)?.kind)
        assertNull(store.pendingChange(kept.id))
        push.release.complete(Unit)
        assertEquals(ApiResult.Success(Unit), engine.syncNow())
        assertEquals(vanished.title, api.serverGame(vanished.id)?.title)
        assertNotNull(api.serverGame(created.id))
        assertEquals(emptyList<PendingChange>(), store.pendingChanges())
    }

    @Test
    fun `a reset interrupted by a failure or a restart continues in the next run`() = runTest {
        val (engine, _) = signedIn()
        val kept = testGame("10000000-0000-4000-8000-000000000001")
        val vanished = testGame("10000000-0000-4000-8000-000000000002")
        api.putOnServer(kept)
        api.putOnServer(vanished)
        engine.syncNow()
        api.forgetOnServer(vanished.id)
        api.beforeCall = { call ->
            when {
                call.name == "changes" && call.cursor != null -> throw httpError(410, "SYNC_RESET_REQUIRED")
                call.name == "changes" -> throw IOException("offline")
            }
        }

        assertEquals(ApiResult.Failure(AppError.Network), engine.syncNow())
        // Nothing was removed; the list stays as it is.
        assertEquals(setOf(kept, vanished), store.observeGames().first().toSet())
        assertEquals(setOf(kept.id, vanished.id), store.unconfirmedGameIds().toSet())
        assertNull(store.syncState().cursor)

        api.beforeCall = {}
        val push = api.hold("POST")
        val restarted = engine()
        assertEquals(ApiResult.Success(Unit), restarted.syncNow())

        assertEquals(emptyList<String>(), store.unconfirmedGameIds())
        assertNull(store.pendingChange(kept.id))
        push.reached.await()
        assertEquals(Kind.CREATE, store.pendingChange(vanished.id)?.kind)
        push.release.complete(Unit)
        assertEquals(ApiResult.Success(Unit), restarted.syncNow())
        assertNotNull(api.serverGame(vanished.id))
    }

    @Test
    fun `games a reset brings or deletes are confirmed, and signing out forgets the reset`() = runTest {
        val (engine, _) = signedIn()
        val changed = testGame("10000000-0000-4000-8000-000000000001")
        val deleted = testGame("10000000-0000-4000-8000-000000000002")
        api.putOnServer(changed)
        api.putOnServer(deleted)
        engine.syncNow()
        api.putOnServer(changed.copy(title = "Changed elsewhere"))
        api.deleteOnServer(deleted.id)
        resetNextPull().release.complete(Unit)

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertEquals("Changed elsewhere", store.game(changed.id)?.title)
        assertNull(store.game(deleted.id))
        assertEquals(emptyList<PendingChange>(), store.pendingChanges())

        store.markGamesWithoutPendingChangeUnconfirmed()
        engine.clearUserData()
        assertEquals(emptyList<String>(), store.unconfirmedGameIds())
    }

    @Test
    fun `a malformed cursor is treated like a reset`() = runTest {
        val (engine, _) = signedIn()
        val kept = testGame("10000000-0000-4000-8000-000000000001")
        api.putOnServer(kept)
        engine.syncNow()
        api.calls.clear()
        api.beforeCall = { if (it.name == "changes" && it.cursor != null) throw httpError(400, "VALIDATION_FAILED") }

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertEquals(listOf("1", null), api.calls.filter { it.name == "changes" }.map { it.cursor })
        assertEquals(kept, store.game(kept.id))
        assertEquals("1", store.syncState().cursor)
    }

    @Test
    fun `a change feed error without an API error body never resets`() = runTest {
        val (engine, _) = signedIn()
        val kept = testGame("10000000-0000-4000-8000-000000000001")
        api.putOnServer(kept)
        engine.syncNow()
        api.forgetOnServer(kept.id)
        api.beforeCall = { if (it.name == "changes") throw httpErrorWithBody(410, "<html>Gone</html>") }

        assertEquals(ApiResult.Failure(AppError.Http(410)), engine.syncNow())

        assertEquals(kept, store.game(kept.id))
        assertEquals("1", store.syncState().cursor)
    }

    @Test
    fun `after an app update the next run pulls everything again and pushes back what the server lacks`() = runTest {
        val (oldVersion, repository) = signedIn()
        val synced = testGame("10000000-0000-4000-8000-000000000001", title = "Synced")
        val edited = testGame("10000000-0000-4000-8000-000000000002", title = "Edited elsewhere")
        api.putOnServer(synced)
        api.putOnServer(edited)
        oldVersion.syncNow()
        assertEquals(APP_VERSION, store.syncState().appVersion)
        // The old version stored a status it didn't know as its fallback.
        store.putGame(synced.copy(status = CollectionStatus.UNKNOWN))
        val localOnly = testGame("10000000-0000-4000-8000-000000000003")
        store.putGame(localOnly)
        repository.updateGame(edited.id, edited.toSaveRequest().copy(rating = 8))
        api.calls.clear()

        val updated = engine(appVersion = APP_VERSION + 1)
        val pushBack = api.hold("POST")
        assertEquals(ApiResult.Success(Unit), updated.syncNow())

        assertEquals(listOf("PATCH", "changes"), api.calls.map { it.name })
        assertEquals(listOf<String?>(null), api.calls.filter { it.name == "changes" }.map { it.cursor })
        assertEquals(CollectionStatus.OWNED, store.game(synced.id)?.status)
        assertEquals(8, store.game(edited.id)?.rating)
        assertEquals(localOnly, store.game(localOnly.id))
        assertEquals(APP_VERSION + 1, store.syncState().appVersion)
        // Like after a reset: no 410 said so, but the server doesn't have it (e.g. restored from a backup).
        assertEquals(emptyList<String>(), store.unconfirmedGameIds())
        assertEquals(Kind.CREATE, store.pendingChange(localOnly.id)?.kind)
        assertNull(store.pendingChange(synced.id))
        assertNull(store.pendingChange(edited.id))
        pushBack.reached.await()
        pushBack.release.complete(Unit)
        assertEquals(ApiResult.Success(Unit), updated.syncNow())
        assertEquals(localOnly.title, api.serverGame(localOnly.id)?.title)
        assertNull(store.pendingChange(localOnly.id))

        api.calls.clear()
        updated.syncNow()
        assertNotNull(api.calls.single { it.name == "changes" }.cursor)
    }

    @Test
    fun `a pull that starts without a stored cursor pushes back the games the server lacks`() = runTest {
        val (engine, _) = signedIn()
        val kept = testGame("10000000-0000-4000-8000-000000000001")
        val vanished = testGame("10000000-0000-4000-8000-000000000002")
        api.putOnServer(kept)
        api.putOnServer(vanished)
        engine.syncNow()
        api.forgetOnServer(vanished.id)
        // The app dropped its cursor itself.
        store.putSyncState(store.syncState().copy(cursor = null))
        val push = api.hold("POST")

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        push.reached.await()
        assertEquals(Kind.CREATE, store.pendingChange(vanished.id)?.kind)
        assertNull(store.pendingChange(kept.id))
        push.release.complete(Unit)
        assertEquals(ApiResult.Success(Unit), engine.syncNow())
        assertNotNull(api.serverGame(vanished.id))
        assertEquals(emptyList<PendingChange>(), store.pendingChanges())
    }

    @Test
    fun `a full pull interrupted half-way continues from its cursor and pushes back what the server lacks`() = runTest {
        val (engine, _) = signedIn()
        val first = testGame("10000000-0000-4000-8000-000000000001")
        val second = testGame("10000000-0000-4000-8000-000000000002")
        val vanished = testGame("10000000-0000-4000-8000-000000000003")
        listOf(first, second, vanished).forEach(api::putOnServer)
        engine.syncNow()
        api.forgetOnServer(vanished.id)
        api.maxPageSize = 1
        api.calls.clear()
        api.beforeCall = { if (it.name == "changes" && it.cursor != null) throw IOException("offline") }

        val updated = engine(appVersion = APP_VERSION + 1)
        assertEquals(ApiResult.Failure(AppError.Network), updated.syncNow())

        // The first page is applied with its cursor; the marks of the rest are stored.
        assertEquals(listOf(null, "1"), api.calls.filter { it.name == "changes" }.map { it.cursor })
        assertEquals("1", store.syncState().cursor)
        assertEquals(setOf(second.id, vanished.id), store.unconfirmedGameIds().toSet())

        api.beforeCall = {}
        api.calls.clear()
        val push = api.hold("POST")
        val restarted = engine(appVersion = APP_VERSION + 1)
        assertEquals(ApiResult.Success(Unit), restarted.syncNow())

        assertEquals(listOf("1"), api.calls.filter { it.name == "changes" }.map { it.cursor })
        assertEquals(emptyList<String>(), store.unconfirmedGameIds())
        push.reached.await()
        assertEquals(Kind.CREATE, store.pendingChange(vanished.id)?.kind)
        assertNull(store.pendingChange(first.id))
        assertNull(store.pendingChange(second.id))
        push.release.complete(Unit)
        assertEquals(ApiResult.Success(Unit), restarted.syncNow())
        assertNotNull(api.serverGame(vanished.id))
    }

    @Test
    fun `a run resets at most once`() = runTest {
        val (engine, _) = signedIn()
        api.putOnServer(testGame("10000000-0000-4000-8000-000000000001"))
        engine.syncNow()
        api.calls.clear()
        api.beforeCall = { if (it.name == "changes") throw httpError(410, "SYNC_RESET_REQUIRED") }

        // The stored cursor is reset; the pull from the beginning is answered 410 again.
        assertEquals(ApiResult.Failure(AppError.Api(410, ErrorCode.SYNC_RESET_REQUIRED)), engine.syncNow())
        assertEquals(listOf("1", null), api.calls.filter { it.name == "changes" }.map { it.cursor })

        // Starting without a cursor counts as the run's reset already.
        api.calls.clear()
        assertEquals(ApiResult.Failure(AppError.Api(410, ErrorCode.SYNC_RESET_REQUIRED)), engine.syncNow())
        assertEquals(listOf<String?>(null), api.calls.filter { it.name == "changes" }.map { it.cursor })
        assertEquals(emptyList<PendingChange>(), store.pendingChanges())
    }

    @Test
    fun `the first sync of a fresh sign-in marks nothing and pushes nothing back`() = runTest {
        val (engine, _) = signedIn()
        api.putOnServer(testGame("10000000-0000-4000-8000-000000000001"))
        api.putOnServer(testGame("10000000-0000-4000-8000-000000000002"))

        assertEquals(ApiResult.Success(Unit), engine.syncNow())
        advanceUntilIdle()

        assertEquals(listOf("changes"), api.calls.map { it.name })
        assertEquals(2, store.observeGames().first().size)
        assertEquals(emptyList<String>(), store.unconfirmedGameIds())
        assertEquals(emptyList<PendingChange>(), store.pendingChanges())
    }

    @Test
    fun `a rejection waits for someone to report it to, and is reported once`() = runTest {
        val (engine, repository) = signedIn()
        val first = repository.createGame(REQUEST)
        val second = repository.createGame(REQUEST.copy(title = "Second"))
        engine.syncNow()
        api.beforeCall = { if (it.name == "PATCH") throw httpError(400, "VALIDATION_FAILED") }
        repository.updateGame(first.id, REQUEST.copy(rating = 1))
        engine.syncNow()
        repository.updateGame(second.id, REQUEST.copy(title = "Second", rating = 2))
        engine.syncNow()

        // Nobody listened during the runs (e.g. at app start); the list screen subscribes only now.
        val events = mutableListOf<SyncEvent>()
        val received = CompletableDeferred<Unit>()
        backgroundScope.launch {
            engine.events.collect {
                events += it
                received.complete(Unit)
            }
        }
        received.await()
        // Not reported again while it waits to be shown, also when a run changes the sync state meanwhile.
        api.beforeCall = {}
        engine.syncNow()
        engine.onChangesRejectedShown()
        store.observeSyncState().first { !it.reportRejectedChanges }

        assertEquals(listOf(SyncEvent.ChangesRejected), events)
    }

    @Test
    fun `an unreported rejection is dropped with the local data`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()
        api.beforeCall = { if (it.name == "PATCH") throw httpError(409, "CONFLICT") }
        repository.updateGame(game.id, REQUEST.copy(rating = 1))
        engine.syncNow()
        assertTrue(store.syncState().reportRejectedChanges)

        engine.clearUserData()

        assertFalse(store.syncState().reportRejectedChanges)
    }

    @Test
    fun `a local edit during a push keeps the pending change`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()
        repository.updateGame(game.id, REQUEST.copy(title = "First"))

        val gate = api.hold("PATCH")
        val sync = async { engine.syncNow() }
        gate.reached.await()
        repository.updateGame(game.id, REQUEST.copy(title = "First", rating = 8))
        gate.release.complete(Unit)

        assertEquals(ApiResult.Success(Unit), sync.await())
        assertEquals("First", api.serverGame(game.id)?.title)
        assertNull(api.serverGame(game.id)?.rating)
        assertEquals(setOf("title", "rating"), store.pendingChange(game.id)?.fields)
        assertEquals(8, store.game(game.id)?.rating)

        api.calls.clear()
        engine.syncNow()

        assertEquals(Call("PATCH", game.id, fields = setOf("title", "rating")), api.calls.first())
        assertEquals(8, api.serverGame(game.id)?.rating)
        assertNull(store.pendingChange(game.id))
    }

    @Test
    fun `an edit during the first push of a created game turns it into an update of the edited fields`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)

        val gate = api.hold("POST")
        val sync = async { engine.syncNow() }
        gate.reached.await()
        repository.updateGame(game.id, REQUEST.copy(title = "Renamed"))
        gate.release.complete(Unit)

        assertEquals(ApiResult.Success(Unit), sync.await())
        // All fields would overwrite what other devices changed meanwhile.
        assertEquals(Kind.UPDATE, store.pendingChange(game.id)?.kind)
        assertEquals(setOf("title"), store.pendingChange(game.id)?.fields)
        assertEquals("Renamed", store.game(game.id)?.title)
        assertEquals(SERVER_TIME, store.game(game.id)?.createdAt)

        api.putOnServer(api.serverGame(game.id)!!.copy(notes = "Edited elsewhere"))
        api.calls.clear()
        assertEquals(ApiResult.Success(Unit), engine.syncNow())
        assertEquals(Call("PATCH", game.id, fields = setOf("title")), api.calls.first())
        assertEquals("Edited elsewhere", api.serverGame(game.id)?.notes)
        assertEquals("Renamed", api.serverGame(game.id)?.title)
    }

    @Test
    fun `signing in as another user wipes the local data first`() = runTest {
        val (engine, repository) = signedIn()
        api.beforeCall = { throw IOException("offline") }
        engine.start()
        val game = repository.createGame(REQUEST)

        userId.value = OTHER_USER
        store.observeSyncState().first { it.ownerUserId == OTHER_USER }

        assertNull(store.game(game.id))
        assertEquals(emptyList<PendingChange>(), store.pendingChanges())
    }

    @Test
    fun `a run of a signed-in user who doesn't own the local data hands it over instead of skipping`() = runTest {
        val (engine, _) = signedIn()
        val game = testGame("10000000-0000-4000-8000-000000000001")
        api.putOnServer(game)
        // E.g. the app was killed between wiping the data and ending the session.
        store.wipe(owner = null)

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertEquals(USER, store.syncState().ownerUserId)
        assertEquals(game, store.game(game.id))
    }

    @Test
    fun `no run brings the data back between wiping it and ending the session`() = runTest {
        val (engine, _) = signedIn()
        api.putOnServer(testGame("10000000-0000-4000-8000-000000000001"))

        engine.clearUserData()
        engine.syncNow()

        assertTrue(api.calls.isEmpty())
        assertEquals(SyncState(), store.syncState())
    }

    @Test
    fun `an expired session keeps the local data`() = runTest {
        val (engine, repository) = signedIn()
        api.beforeCall = { throw IOException("offline") }
        engine.start()
        val game = repository.createGame(REQUEST)

        userId.value = null
        advanceUntilIdle()

        assertNotNull(store.game(game.id))
        assertEquals(Kind.CREATE, store.pendingChange(game.id)?.kind)
        assertEquals(USER, store.syncState().ownerUserId)
    }

    @Test
    fun `signing out cancels the running sync and deletes the local data`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        api.putOnServer(testGame("10000000-0000-4000-8000-000000000003"))

        val gate = api.hold("changes")
        val sync = async { runCatching { engine.syncNow() } }
        gate.reached.await()
        engine.clearUserData()
        gate.release.complete(Unit)

        assertTrue(sync.await().exceptionOrNull() is CancellationException)
        assertNull(store.game(game.id))
        assertEquals(emptyList<PendingChange>(), store.pendingChanges())
        assertEquals(SyncState(), store.syncState())
    }

    @Test
    fun `a temporary failure is retried after a backoff while in the foreground`() = runTest {
        foreground.value = true
        val (engine, repository) = signedIn()
        repository.createGame(REQUEST)
        api.beforeCall = { if (it.name == "POST") throw IOException("offline") }
        assertEquals(ApiResult.Failure(AppError.Network), engine.syncNow())

        api.beforeCall = {}
        val retry = api.hold("POST")
        advanceTimeBy(1_999)
        runCurrent()
        assertFalse(retry.reached.isCompleted)
        advanceTimeBy(2)

        retry.reached.await()
        retry.release.complete(Unit)
    }

    private class Rejection {
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    /** Holds the next pull that continues a cursor until released, then answers `410` once. */
    private fun resetNextPull(): Rejection {
        val reset = Rejection()
        api.beforeCall = { call ->
            if (call.name == "changes" && call.cursor != null && !reset.reached.isCompleted) {
                reset.reached.complete(Unit)
                reset.release.await()
                throw httpError(410, "SYNC_RESET_REQUIRED")
            }
        }
        return reset
    }

    /** Holds the next call named [name] until released, then fails it with [failure]. */
    private fun rejectNext(name: String, failure: Exception): Rejection {
        val rejection = Rejection()
        api.beforeCall = { call ->
            if (call.name == name && !rejection.reached.isCompleted) {
                rejection.reached.complete(Unit)
                rejection.release.await()
                throw failure
            }
        }
        return rejection
    }

    @Test
    fun `backoff doubles from 2 seconds up to 5 minutes`() {
        assertEquals(
            listOf(2_000L, 4_000L, 8_000L, 16_000L, 256_000L, 300_000L, 300_000L),
            listOf(1, 2, 3, 4, 8, 9, 100).map { SyncEngine.backoffMillis(it) },
        )
    }

    private companion object {
        const val APP_VERSION = 7
        const val USER = "user-1"
        const val OTHER_USER = "user-2"
        val SERVER_TIME: Instant = Instant.parse("2026-10-07T10:00:00Z")
        val LOCAL_TIME: Instant = Instant.parse("2026-10-07T12:00:00Z")
        val LOCAL_CLOCK: Clock = Clock.fixed(LOCAL_TIME, ZoneOffset.UTC)
        val REQUEST = SaveGameRequest(title = "Banjo-Kazooie", platform = Platform.N64)
    }
}
