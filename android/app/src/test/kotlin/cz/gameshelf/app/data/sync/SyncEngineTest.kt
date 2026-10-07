package cz.gameshelf.app.data.sync

import android.app.Application
import androidx.room.Room
import cz.gameshelf.app.data.games.OfflineGamesRepository
import cz.gameshelf.app.data.local.GameShelfDatabase
import cz.gameshelf.app.data.local.LocalGameStore
import cz.gameshelf.app.data.local.SyncState
import cz.gameshelf.app.data.sync.PendingChange.Kind
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.SaveGameRequest
import cz.gameshelf.app.testing.FakeGamesApi
import cz.gameshelf.app.testing.FakeGamesApi.Call
import cz.gameshelf.app.testing.httpError
import cz.gameshelf.app.testing.testGame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
        val engine = SyncEngine(api, store, userId, connected, foreground, backgroundScope, LOCAL_CLOCK)
        val repository = OfflineGamesRepository(store, requestSync = {}, scope = backgroundScope, clock = LOCAL_CLOCK)
        return Harness(engine, repository)
    }

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
    fun `a rejected update is undone with the server version and reported`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        engine.syncNow()
        val events = mutableListOf<SyncEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { engine.events.toList(events) }
        repository.updateGame(game.id, REQUEST.copy(title = "Rejected"))
        api.beforeCall = { if (it.name == "PATCH") throw httpError(400, "VALIDATION_FAILED") }

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertTrue(Call("GET", game.id) in api.calls)
        assertEquals(REQUEST.title, store.game(game.id)?.title)
        assertEquals(api.serverGame(game.id), store.game(game.id))
        assertNull(store.pendingChange(game.id))
        assertEquals(listOf(SyncEvent.ChangesRejected), events)
    }

    @Test
    fun `a rejected create removes the game`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)
        api.beforeCall = { if (it.name == "POST") throw httpError(409, "CONFLICT") }

        assertEquals(ApiResult.Success(Unit), engine.syncNow())

        assertNull(store.game(game.id))
        assertNull(store.pendingChange(game.id))
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
    fun `a sync reset keeps games with pending changes and pulls everything again`() = runTest {
        val (engine, repository) = signedIn()
        val kept = testGame("10000000-0000-4000-8000-000000000001", platform = Platform.PS5)
        val vanished = testGame("10000000-0000-4000-8000-000000000002", platform = Platform.PS4)
        api.putOnServer(kept)
        api.putOnServer(vanished)
        engine.syncNow()
        assertNotNull(store.game(vanished.id))
        // The server was restored from a backup that does not have the second game.
        api.forgetOnServer(vanished.id)
        api.calls.clear()
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        api.beforeCall = { call ->
            if (call.name == "changes" && call.cursor != null) {
                reached.complete(Unit)
                release.await()
                throw httpError(410, "SYNC_RESET_REQUIRED")
            }
        }

        val sync = async { engine.syncNow() }
        reached.await()
        val created = repository.createGame(REQUEST)
        release.complete(Unit)

        assertEquals(ApiResult.Success(Unit), sync.await())
        assertEquals(listOf("2", null), api.calls.filter { it.name == "changes" }.map { it.cursor })
        assertNotNull(store.game(kept.id))
        assertNull(store.game(vanished.id))
        assertNotNull(store.game(created.id))
        assertEquals(Kind.CREATE, store.pendingChange(created.id)?.kind)
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
    fun `an edit during the first push of a created game turns it into an update of all fields`() = runTest {
        val (engine, repository) = signedIn()
        val game = repository.createGame(REQUEST)

        val gate = api.hold("POST")
        val sync = async { engine.syncNow() }
        gate.reached.await()
        repository.updateGame(game.id, REQUEST.copy(title = "Renamed"))
        gate.release.complete(Unit)

        assertEquals(ApiResult.Success(Unit), sync.await())
        assertEquals(Kind.UPDATE, store.pendingChange(game.id)?.kind)
        assertEquals(GameFields.ALL, store.pendingChange(game.id)?.fields)
        assertEquals("Renamed", store.game(game.id)?.title)
        assertEquals(SERVER_TIME, store.game(game.id)?.createdAt)
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

    @Test
    fun `backoff doubles from 2 seconds up to 5 minutes`() {
        assertEquals(
            listOf(2_000L, 4_000L, 8_000L, 16_000L, 256_000L, 300_000L, 300_000L),
            listOf(1, 2, 3, 4, 8, 9, 100).map { SyncEngine.backoffMillis(it) },
        )
    }

    private companion object {
        const val USER = "user-1"
        const val OTHER_USER = "user-2"
        val SERVER_TIME: Instant = Instant.parse("2026-10-07T10:00:00Z")
        val LOCAL_TIME: Instant = Instant.parse("2026-10-07T12:00:00Z")
        val LOCAL_CLOCK: Clock = Clock.fixed(LOCAL_TIME, ZoneOffset.UTC)
        val REQUEST = SaveGameRequest(title = "Banjo-Kazooie", platform = Platform.N64)
    }
}
