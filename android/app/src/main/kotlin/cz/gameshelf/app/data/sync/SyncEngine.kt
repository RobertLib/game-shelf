package cz.gameshelf.app.data.sync

import android.util.Log
import androidx.annotation.VisibleForTesting
import cz.gameshelf.app.data.api.GamesApi
import cz.gameshelf.app.data.api.apiCall
import cz.gameshelf.app.data.api.dto.GameChanges
import cz.gameshelf.app.data.local.LocalGameStore
import cz.gameshelf.app.data.sync.PendingChange.Kind
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.ErrorCode
import cz.gameshelf.app.domain.model.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Response
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.time.Clock

/**
 * Pushes pending local changes to the API and pulls the change feed (offline-sync.md, "Sync run").
 *
 * Runs are single-flight: a request while a run is active schedules exactly one more run after it.
 * Temporary failures are retried with exponential backoff while the app is in the foreground.
 * A run captures the signed-in user and re-checks it, together with the stored owner, at the start
 * of every write transaction, so it never writes data of a user who is no longer signed in.
 */
class SyncEngine(
    private val api: GamesApi,
    private val store: LocalGameStore,
    private val signedInUserId: StateFlow<String?>,
    private val isConnected: StateFlow<Boolean>,
    private val isForeground: StateFlow<Boolean>,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) : SyncController {

    private val runState = MutableStateFlow(RunState())

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 8)
    override val events: Flow<SyncEvent> = _events.asSharedFlow()

    override val status: StateFlow<SyncStatus> = combine(
        runState,
        store.observePendingCount(),
        store.observeSyncState(),
        isConnected,
    ) { run, pendingCount, state, connected ->
        SyncStatus(
            isSyncing = run.isSyncing,
            isOffline = run.isUnreachable || !connected,
            pendingCount = pendingCount,
            lastSyncedAt = state.lastSyncedAt,
            lastError = run.lastError,
        )
    }.stateIn(scope, SharingStarted.Eagerly, SyncStatus(isOffline = !isConnected.value))

    private val lock = Any()
    private var worker: Job? = null
    private var queuedRun: CompletableDeferred<ApiResult<Unit>>? = null
    private var retryJob: Job? = null
    private var failedRuns = 0

    /** Starts following the session, the app coming to the foreground and the network coming back. */
    fun start() {
        scope.launch {
            signedInUserId.collect { userId ->
                if (userId == null) {
                    // The session ended (or expired): stop, but keep the data for the next sign-in.
                    cancelRuns()
                } else {
                    // Sign-in already did this; a session restored at launch needs it here.
                    prepareUserData(userId)
                    requestSync()
                }
            }
        }
        scope.launch {
            isForeground.collect { foreground -> if (foreground) requestSync() else cancelRetry() }
        }
        scope.launch {
            isConnected.drop(1).filter { it }.collect { requestSync() }
        }
    }

    override fun requestSync() {
        if (signedInUserId.value != null) enqueueRun()
    }

    override suspend fun syncNow(): ApiResult<Unit> = enqueueRun().await()

    /** Sign-out or account deletion: stops syncing and deletes everything stored for the user. */
    suspend fun clearUserData() {
        cancelRuns()
        store.wipe(owner = null)
        runState.value = RunState()
    }

    /**
     * Makes the local data [userId]'s: another user's data is wiped. Sign-in calls it before switching
     * the session, so the new user's screens never show someone else's collection.
     */
    suspend fun prepareUserData(userId: String) {
        if (store.syncState().ownerUserId == userId) return
        cancelRuns()
        store.wipe(owner = userId)
        runState.value = RunState()
    }

    private suspend fun cancelRuns() {
        val running = synchronized(lock) {
            retryJob?.cancel()
            retryJob = null
            queuedRun?.cancel()
            queuedRun = null
            failedRuns = 0
            worker.also { worker = null }
        }
        running?.cancelAndJoin()
    }

    private fun cancelRetry() = synchronized(lock) {
        retryJob?.cancel()
        retryJob = null
    }

    private fun enqueueRun(): Deferred<ApiResult<Unit>> = synchronized(lock) {
        // Any trigger retries right away instead of waiting for the backoff.
        retryJob?.cancel()
        retryJob = null
        val run = queuedRun ?: CompletableDeferred<ApiResult<Unit>>().also { queuedRun = it }
        if (worker == null) {
            val job = scope.launch(start = CoroutineStart.LAZY) { processRuns() }
            worker = job
            job.start()
        }
        run
    }

    /** Executes the queued run, then the one queued meanwhile, until nothing is queued. */
    private suspend fun processRuns() {
        val self = currentCoroutineContext().job
        while (true) {
            val next = synchronized(lock) {
                if (worker !== self) return
                queuedRun?.also { queuedRun = null } ?: run {
                    worker = null
                    return
                }
            }
            val outcome = try {
                runOnce()
            } catch (e: CancellationException) {
                next.cancel()
                throw e
            }
            next.complete(
                when (outcome) {
                    RunOutcome.Success -> ApiResult.Success(Unit)
                    is RunOutcome.Failure -> ApiResult.Failure(outcome.error)
                    RunOutcome.Skipped -> ApiResult.Failure(AppError.Unexpected)
                },
            )
            scheduleRetry(outcome)
        }
    }

    private fun scheduleRetry(outcome: RunOutcome) = synchronized(lock) {
        when (outcome) {
            RunOutcome.Success -> failedRuns = 0
            RunOutcome.Skipped -> Unit
            is RunOutcome.Failure -> {
                failedRuns++
                // Otherwise another run follows anyway, or coming to the foreground / online retries.
                if (queuedRun == null && isForeground.value && isConnected.value) {
                    val delayMillis = backoffMillis(failedRuns)
                    val job = scope.launch(start = CoroutineStart.LAZY) {
                        delay(delayMillis)
                        val self = currentCoroutineContext().job
                        synchronized(lock) { if (retryJob === self) retryJob = null }
                        requestSync()
                    }
                    retryJob = job
                    job.start()
                }
            }
        }
    }

    private suspend fun runOnce(): RunOutcome {
        val owner = signedInUserId.value ?: return RunOutcome.Skipped
        if (store.syncState().ownerUserId != owner) return RunOutcome.Skipped
        runState.update { it.copy(isSyncing = true) }
        val run = Run(owner)
        var outcome: RunOutcome = RunOutcome.Skipped
        try {
            val error = push(run) ?: pull(run)
            outcome = if (error == null) RunOutcome.Success else RunOutcome.Failure(error)
        } catch (e: OwnerChangedException) {
            outcome = RunOutcome.Skipped
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // E.g. a database failure: report the run as failed and retry later instead of crashing.
            Log.e(TAG, "Sync run failed", e)
            outcome = RunOutcome.Failure(AppError.Unexpected)
        } finally {
            runState.update { it.finishedWith(outcome) }
            if (run.rejectedChanges > 0) _events.tryEmit(SyncEvent.ChangesRejected)
        }
        return outcome
    }

    /** Pushes every pending change, oldest first; returns the error that ended the push early. */
    private suspend fun push(run: Run): AppError? {
        for (gameId in store.pendingChanges().map { it.gameId }) {
            var outcome = pushChange(run, gameId)
            while (outcome == PushOutcome.Again) outcome = pushChange(run, gameId)
            if (outcome is PushOutcome.Failed) return outcome.error
        }
        return null
    }

    private suspend fun pushChange(run: Run, gameId: String): PushOutcome {
        val attempt = store.transaction {
            run.checkOwner()
            val change = store.pendingChange(gameId) ?: return@transaction null
            val game = store.game(gameId)
            val nothingToSend = when (change.kind) {
                Kind.CREATE -> game == null
                Kind.UPDATE -> game == null || change.fields.isEmpty()
                Kind.DELETE -> false
            }
            if (nothingToSend) {
                store.removePendingChange(gameId)
                return@transaction null
            }
            // From now on the server may have the change even if the response gets lost.
            if (!change.attempted) store.putPendingChange(change.copy(attempted = true))
            Attempt(change, game)
        } ?: return PushOutcome.Done

        val change = attempt.change
        return when (change.kind) {
            Kind.CREATE -> {
                val body = GameFields.createBody(attempt.requireGame())
                when (val result = apiCall { api.createGame(body).toUpsert() }) {
                    is ApiResult.Success -> onUpserted(run, change, result.value)
                    is ApiResult.Failure -> onPushFailed(run, change, result.error)
                }
            }
            Kind.UPDATE -> {
                val body = GameFields.patchBody(attempt.requireGame(), change.fields)
                when (val result = apiCall { api.updateGame(gameId, body) }) {
                    is ApiResult.Success -> onUpserted(run, change, Upsert(result.value, alreadyExisted = false))
                    is ApiResult.Failure -> onPushFailed(run, change, result.error)
                }
            }
            Kind.DELETE -> {
                val result = apiCall { api.deleteGame(gameId) }
                if (result is ApiResult.Failure && !result.error.isNotFound()) {
                    onPushFailed(run, change, result.error)
                } else {
                    store.transaction {
                        run.checkOwner()
                        if (store.pendingChange(gameId)?.revision == change.revision) store.removePendingChange(gameId)
                    }
                    PushOutcome.Done
                }
            }
        }
    }

    /** Stores the server's answer to a pushed CREATE / UPDATE ([sent]) in one transaction. */
    private suspend fun onUpserted(run: Run, sent: PendingChange, response: Upsert): PushOutcome = store.transaction {
        run.checkOwner()
        val id = sent.gameId
        val current = store.pendingChange(id) ?: return@transaction PushOutcome.Done
        val server = response.game
        val local = store.game(id) ?: server
        when {
            // Deleted locally meanwhile; the DELETE is pushed next time.
            current.kind == Kind.DELETE -> PushOutcome.Done
            current.revision == sent.revision && response.alreadyExisted && sent.fields.isNotEmpty() -> {
                // An earlier attempt created the game without us learning about it, so the fields
                // edited after that attempt are not on the server yet: push them right away.
                store.putPendingChange(current.copy(kind = Kind.UPDATE))
                store.putGame(GameFields.merge(server, local, sent.fields))
                PushOutcome.Again
            }
            current.revision == sent.revision -> {
                store.removePendingChange(id)
                store.putGame(server)
                PushOutcome.Done
            }
            else -> {
                // Edited locally while the request was in flight: keep the change.
                val pending = if (current.kind == Kind.CREATE) {
                    current.copy(kind = Kind.UPDATE, fields = GameFields.ALL)
                } else {
                    current
                }
                store.putPendingChange(pending)
                store.putGame(GameFields.merge(server, local, pending.fields))
                PushOutcome.Done
            }
        }
    }

    private suspend fun onPushFailed(run: Run, sent: PendingChange, error: AppError): PushOutcome = when {
        error.isGameNotFound() -> {
            // Deleted on another device; delete wins.
            store.transaction {
                run.checkOwner()
                store.removeGame(sent.gameId)
                store.removePendingChange(sent.gameId)
            }
            PushOutcome.Done
        }
        error.isPermanentRejection() -> undo(run, sent)
        else -> PushOutcome.Failed(error)
    }

    /** A change the server rejected permanently is undone (offline-sync.md, "Rejected changes"). */
    private suspend fun undo(run: Run, sent: PendingChange): PushOutcome {
        val id = sent.gameId
        if (sent.kind == Kind.CREATE) {
            store.transaction {
                run.checkOwner()
                store.removePendingChange(id)
                store.removeGame(id)
            }
            run.rejectedChanges++
            return PushOutcome.Done
        }
        val restored = apiCall { api.game(id) }
        if (restored is ApiResult.Failure && !restored.error.isNotFound()) return PushOutcome.Failed(restored.error)
        val undone = store.transaction {
            run.checkOwner()
            // A delete made after the rejected update is a new intention; it stays queued.
            if (sent.kind != Kind.DELETE && store.pendingChange(id)?.kind == Kind.DELETE) return@transaction false
            store.removePendingChange(id)
            if (restored is ApiResult.Success) store.putGame(restored.value) else store.removeGame(id)
            true
        }
        if (undone) run.rejectedChanges++
        return PushOutcome.Done
    }

    /** Pulls the change feed until `hasMore` is false; returns the error that ended it early. */
    private suspend fun pull(run: Run): AppError? {
        var wasReset = false
        while (true) {
            val cursor = store.syncState().cursor
            val page = when (val result = apiCall { api.changes(cursor, PAGE_SIZE) }) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> {
                    if (wasReset || !result.error.requiresReset(cursor)) return result.error
                    // The cursor cannot be continued: keep only unsynced games and pull from the beginning.
                    store.transaction {
                        run.checkOwner()
                        store.removeGamesWithoutPendingChange()
                        store.putSyncState(store.syncState().copy(cursor = null))
                    }
                    wasReset = true
                    continue
                }
            }
            store.transaction {
                run.checkOwner()
                applyPage(page)
            }
            if (!page.hasMore) return null
        }
    }

    /** Merges one page into the local data and stores its cursor (offline-sync.md, "Pull"). */
    private suspend fun applyPage(page: GameChanges) {
        val pending = store.pendingChanges().associateBy { it.gameId }
        for (game in page.games) {
            val change = pending[game.id]
            when (change?.kind) {
                null -> store.putGame(game)
                Kind.UPDATE -> {
                    val local = store.game(game.id)
                    store.putGame(if (local == null) game else GameFields.merge(game, local, change.fields))
                }
                // CREATE: the local version is newer. DELETE: stays deleted.
                Kind.CREATE, Kind.DELETE -> Unit
            }
        }
        for (id in page.deletedIds) {
            store.removeGame(id)
            if (id in pending) store.removePendingChange(id)
        }
        val state = store.syncState()
        store.putSyncState(
            state.copy(
                cursor = page.cursor,
                lastSyncedAt = if (page.hasMore) state.lastSyncedAt else clock.instant(),
            ),
        )
    }

    /** Throws when the run's user is no longer signed in or no longer owns the local data. */
    private suspend fun Run.checkOwner() {
        if (signedInUserId.value != owner || store.syncState().ownerUserId != owner) {
            throw OwnerChangedException()
        }
    }

    private class Run(val owner: String) {
        var rejectedChanges = 0
    }

    private class Attempt(val change: PendingChange, val game: Game?) {
        fun requireGame(): Game = checkNotNull(game)
    }

    private class Upsert(val game: Game, val alreadyExisted: Boolean)

    private fun Response<Game>.toUpsert(): Upsert {
        if (!isSuccessful) throw HttpException(this)
        return Upsert(checkNotNull(body()) { "Empty response body" }, alreadyExisted = code() != HTTP_CREATED)
    }

    private sealed interface PushOutcome {
        data object Done : PushOutcome

        /** The change was converted and has to be pushed again in this run. */
        data object Again : PushOutcome
        data class Failed(val error: AppError) : PushOutcome
    }

    private sealed interface RunOutcome {
        data object Success : RunOutcome
        data class Failure(val error: AppError) : RunOutcome

        /** Nobody signed in, or the local data is not (or no longer) the signed-in user's. */
        data object Skipped : RunOutcome
    }

    private data class RunState(
        val isSyncing: Boolean = false,
        val isUnreachable: Boolean = false,
        val lastError: AppError? = null,
    ) {
        fun finishedWith(outcome: RunOutcome): RunState = when (outcome) {
            RunOutcome.Success -> RunState()
            is RunOutcome.Failure -> RunState(
                isUnreachable = outcome.error == AppError.Network,
                lastError = outcome.error,
            )
            RunOutcome.Skipped -> copy(isSyncing = false)
        }
    }

    private class OwnerChangedException : RuntimeException()

    companion object {
        private const val TAG = "SyncEngine"
        private const val PAGE_SIZE = 500
        private const val HTTP_GONE = 410
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val INITIAL_BACKOFF_MILLIS = 2_000L
        private const val MAX_BACKOFF_MILLIS = 5 * 60_000L

        /** 2 s, 4 s, 8 s … at most 5 minutes. */
        @VisibleForTesting
        internal fun backoffMillis(failedRuns: Int): Long =
            (INITIAL_BACKOFF_MILLIS shl (failedRuns - 1).coerceIn(0, 20)).coerceAtMost(MAX_BACKOFF_MILLIS)

        private fun AppError.isNotFound() = this is AppError.Api && statusCode == HTTP_NOT_FOUND

        private fun AppError.isGameNotFound() = isNotFound() && (this as AppError.Api).code == ErrorCode.GAME_NOT_FOUND

        /** `400`, `409` and other `4xx` except `401` / `429`: the change can never succeed. */
        private fun AppError.isPermanentRejection() = this is AppError.Api &&
            statusCode in 400..499 && statusCode != HTTP_UNAUTHORIZED && statusCode != HTTP_TOO_MANY_REQUESTS

        /** `410 SYNC_RESET_REQUIRED`, or a malformed cursor (`400 VALIDATION_FAILED`). */
        private fun AppError.requiresReset(cursor: String?) = this is AppError.Api && (
            statusCode == HTTP_GONE || code == ErrorCode.SYNC_RESET_REQUIRED ||
                (cursor != null && statusCode == HTTP_BAD_REQUEST && code == ErrorCode.VALIDATION_FAILED)
            )
    }
}
