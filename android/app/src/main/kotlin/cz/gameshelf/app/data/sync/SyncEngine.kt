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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Response
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_CONFLICT
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.time.Clock

/**
 * Pushes pending local changes to the API and pulls the change feed (offline-sync.md, "Sync run").
 *
 * Runs are single-flight: a request while a run is active schedules exactly one more run after it.
 * A change that fails stays queued while the run goes on with the next one; only a failure that concerns the
 * whole server ends the run early. Failed runs are retried with exponential backoff while the app is in the
 * foreground.
 * A run captures the signed-in user and re-checks it, together with the stored owner, at the start
 * of every write transaction, so it never writes data of a user who is no longer signed in.
 *
 * [appVersion] is the running app's `versionCode`: a cursor stored by another version is not continued
 * (offline-sync.md, "After an app update").
 */
class SyncEngine(
    private val api: GamesApi,
    private val store: LocalGameStore,
    private val signedInUserId: StateFlow<String?>,
    private val isConnected: StateFlow<Boolean>,
    private val isForeground: StateFlow<Boolean>,
    private val scope: CoroutineScope,
    private val appVersion: Int,
    private val clock: Clock = Clock.systemUTC(),
) : SyncController {

    private val runState = MutableStateFlow(RunState())

    // Stored with the sync state until shown, so a restart doesn't lose it; several runs' rejections show once.
    override val events: Flow<SyncEvent> = store.observeSyncState()
        .map { it.reportRejectedChanges }
        .distinctUntilChanged()
        .filter { it }
        .map { SyncEvent.ChangesRejected }

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

    /** Signed out, but still signed in until the session ends: [clearUserData] wiped this user's data. */
    @Volatile
    private var signingOutUserId: String? = null

    /** Starts following the session, the app coming to the foreground and the network coming back. */
    fun start() {
        scope.launch {
            signedInUserId.collect { userId ->
                if (userId == null) {
                    // The session ended (or expired): stop, but keep the data for the next sign-in.
                    cancelRuns()
                } else {
                    // Sign-in already did this; a session restored at launch needs it here. Should it fail, the
                    // run repairs it (see adoptUserData), and this keeps following the session.
                    try {
                        prepareUserData(userId)
                    } catch (e: RuntimeException) {
                        Log.e(TAG, "Couldn't hand the local data over to the signed-in user", e)
                    }
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

    override fun onChangesRejectedShown() {
        scope.launch {
            store.transaction {
                val state = store.syncState()
                if (state.reportRejectedChanges) store.putSyncState(state.copy(reportRejectedChanges = false))
            }
        }
    }

    /**
     * Sign-out or account deletion: stops syncing and deletes everything stored for the user. Until the session
     * ends right after, no run starts that could bring the data back.
     */
    suspend fun clearUserData() {
        signingOutUserId = signedInUserId.value
        cancelRuns()
        store.wipe(owner = null)
        resetState()
    }

    /**
     * Makes the local data [userId]'s: another user's data is wiped. Sign-in calls it before switching
     * the session, so the new user's screens never show someone else's collection.
     */
    suspend fun prepareUserData(userId: String) {
        signingOutUserId = null
        if (store.syncState().ownerUserId == userId) return
        cancelRuns()
        store.wipe(owner = userId)
        resetState()
    }

    /** After the data was wiped (its rejected changes with it): no status of the previous data is shown. */
    private fun resetState() {
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
        if (owner == signingOutUserId) return RunOutcome.Skipped
        runState.update { it.copy(isSyncing = true) }
        val run = Run(owner)
        var outcome: RunOutcome = RunOutcome.Skipped
        try {
            adoptUserData(run)
            // The error that ended the run early; otherwise the first change that failed, if any.
            val error = push(run) ?: pull(run) ?: run.changeError
            outcome = if (error == null) RunOutcome.Success else RunOutcome.Failure(error)
            // Games a reset didn't find on the server got a CREATE: push them.
            if (run.requestsAnotherRun) requestSync()
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
        }
        return outcome
    }

    /**
     * The signed-in user doesn't own the local data – e.g. handing it over at launch failed, or an older version
     * was interrupted between wiping the data and ending the session. Rather than skipping every run, the data is
     * handed over now (another user's data is wiped), like at sign-in.
     */
    private suspend fun adoptUserData(run: Run) {
        if (store.syncState().ownerUserId == run.owner) return
        store.transaction {
            if (signedInUserId.value != run.owner) throw OwnerChangedException()
            store.wipe(owner = run.owner)
        }
        Log.w(TAG, "The local data was not the signed-in user's; it was handed over to them")
    }

    /**
     * Pushes every pending change, oldest first. A change that fails stays queued and the push goes on with the
     * next one ([Run.changeError]); returns the error when the server can't be used now, which ends the run.
     */
    private suspend fun push(run: Run): AppError? {
        for (gameId in store.pendingChanges().map { it.gameId }) {
            var outcome = pushChange(run, gameId)
            while (outcome == PushOutcome.Again) outcome = pushChange(run, gameId)
            when (outcome) {
                is PushOutcome.Failed -> run.changeFailed(outcome.error)
                is PushOutcome.ServerUnavailable -> return outcome.error
                PushOutcome.Done, PushOutcome.Again -> Unit
            }
        }
        return null
    }

    private suspend fun pushChange(run: Run, gameId: String): PushOutcome {
        val prepared = store.transaction {
            run.checkOwner()
            val change = store.pendingChange(gameId) ?: return@transaction Prepared.NothingToSend
            val game = store.game(gameId)
            when {
                change.kind == Kind.UPDATE && change.fields.isEmpty() -> {
                    store.removePendingChange(gameId)
                    Prepared.NothingToSend
                }
                // Every deletion replaces the change with a DELETE, so the game's row is unreadable (or was
                // lost): a local problem, never a sign that the game is gone on the server. This change fails.
                change.kind != Kind.DELETE && game == null -> Prepared.GameMissing
                else -> {
                    // From now on the server may have the change even if the response gets lost.
                    if (!change.attempted) store.putPendingChange(change.copy(attempted = true))
                    Prepared.Attempt(change, game)
                }
            }
        }
        val attempt = when (prepared) {
            Prepared.NothingToSend -> return PushOutcome.Done
            Prepared.GameMissing -> {
                Log.e(TAG, "Game $gameId has a pending change but is missing locally")
                return PushOutcome.Failed(AppError.Unexpected)
            }
            is Prepared.Attempt -> prepared
        }

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
                // Already deleted counts as success – but only when the API itself says so.
                if (result is ApiResult.Failure && !result.error.isGameNotFound()) {
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
            response.alreadyExisted && current.fields.isNotEmpty() -> {
                // An earlier attempt created the game without us learning about it, so the fields edited
                // after that attempt – also while this request was in flight – are not on the server yet:
                // push just those, right away. All fields would overwrite what other devices changed since.
                store.putPendingChange(current.copy(kind = Kind.UPDATE))
                store.putGame(GameFields.merge(server, local, current.fields))
                PushOutcome.Again
            }
            current.revision == sent.revision -> {
                store.removePendingChange(id)
                store.putGame(server)
                PushOutcome.Done
            }
            else -> {
                // Edited locally while the request was in flight: keep the change. A CREATE becomes an UPDATE of
                // the fields edited since its first attempt (all fields would overwrite what other devices changed
                // meanwhile); an UPDATE keeps its fields.
                val pending = current.copy(kind = Kind.UPDATE)
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
        error.isGameUnknown() && sent.kind == Kind.UPDATE -> recreate(run, sent)
        // A CREATE is not undone: the game exists only here. It stays with its CREATE and fails (see below).
        error.isPermanentRejection() && sent.kind != Kind.CREATE -> undo(run, sent, error)
        error.isServerUnavailable() -> PushOutcome.ServerUnavailable(error)
        else -> PushOutcome.Failed(error)
    }

    /**
     * The server has no record of the game (it was restored from a backup older than the game): the UPDATE becomes
     * a CREATE that was not attempted – a later delete then needs no request – and is pushed again at once.
     */
    private suspend fun recreate(run: Run, sent: PendingChange): PushOutcome = store.transaction {
        run.checkOwner()
        val current = store.pendingChange(sent.gameId) ?: return@transaction PushOutcome.Done
        // Deleted locally meanwhile: the DELETE is pushed instead.
        if (current.kind == Kind.UPDATE) {
            store.putPendingChange(current.copy(kind = Kind.CREATE, fields = emptySet(), attempted = false))
        }
        PushOutcome.Again
    }

    /**
     * An UPDATE or DELETE the server rejected permanently ([rejection]) is undone with the server's version
     * (offline-sync.md, "Rejected changes") – but only while the change is still the one that was sent (same revision).
     * One the user edited meanwhile stays queued and fails: the newer edit may have fixed the rejected value, and
     * undoing it would lose that edit; if it is rejected again, it is undone then. A delete made after the rejected
     * change is a new intention; it stays queued (the game is gone locally already) and, as nothing the user sees is
     * undone, is not reported.
     *
     * When the server has no record of the game (`GAME_UNKNOWN`, restored from an older backup), there is nothing to
     * restore and the local game is the only copy: an UPDATE becomes a CREATE that was not attempted and is pushed
     * again at once, a DELETE is dropped.
     */
    private suspend fun undo(run: Run, sent: PendingChange, rejection: AppError): PushOutcome {
        val id = sent.gameId
        changedSince(sent, store.pendingChange(id) ?: return PushOutcome.Done, rejection)?.let { return it }
        val restored = apiCall { api.game(id) }
        // This change failed; it is undone in a later run.
        if (restored is ApiResult.Failure && !restored.error.isGameNotFound() && !restored.error.isGameUnknown()) {
            return PushOutcome.Failed(restored.error)
        }
        return store.transaction {
            run.checkOwner()
            val current = store.pendingChange(id) ?: return@transaction PushOutcome.Done
            changedSince(sent, current, rejection)?.let { return@transaction it }
            when {
                restored is ApiResult.Failure && restored.error.isGameUnknown() -> {
                    if (sent.kind == Kind.UPDATE) {
                        store.putPendingChange(current.copy(kind = Kind.CREATE, fields = emptySet(), attempted = false))
                        PushOutcome.Again
                    } else {
                        store.removePendingChange(id)
                        PushOutcome.Done
                    }
                }
                else -> {
                    store.removePendingChange(id)
                    if (restored is ApiResult.Success) store.putGame(restored.value) else store.removeGame(id)
                    // Reported until a screen has shown it, also after a restart.
                    store.putSyncState(store.syncState().copy(reportRejectedChanges = true))
                    PushOutcome.Done
                }
            }
        }
    }

    /**
     * What becomes of the rejected change [sent] when [current], the pending change now, is not the one that was sent
     * (see [undo]); `null` when it is the same.
     */
    private fun changedSince(sent: PendingChange, current: PendingChange, rejection: AppError): PushOutcome? = when {
        current.revision == sent.revision -> null
        current.kind == Kind.DELETE && sent.kind != Kind.DELETE -> PushOutcome.Done
        else -> PushOutcome.Failed(rejection)
    }

    /**
     * Pulls the change feed until `hasMore` is false; returns the error that ended it early.
     *
     * A pull that starts from the beginning – after a reset, after an app update, or with no cursor stored – first
     * marks every stored game without a pending change as unconfirmed (offline-sync.md, "Reset"): the server may have
     * been restored from a backup without a `410` telling us, and the games the pull doesn't bring are pushed back when
     * it ends. With nothing stored (a fresh sign-in), nothing is marked. The marks are stored, so a pull interrupted
     * by a failure or a restart continues from its cursor with them. At most one reset per run.
     */
    private suspend fun pull(run: Run): AppError? {
        var wasReset = false
        while (true) {
            val state = store.syncState()
            // A cursor of another app version is not continued: that version may have stored games it couldn't read,
            // so the whole collection is pulled again (offline-sync.md, "After an app update"). Unlike a reset, the
            // cursor stays stored until the first page replaces it, together with the new version.
            val cursor = state.cursor.takeIf { state.appVersion == appVersion }
            if (cursor == null && !wasReset) {
                store.transaction {
                    run.checkOwner()
                    store.markGamesWithoutPendingChangeUnconfirmed()
                }
                wasReset = true
            }
            val page = when (val result = apiCall { api.changes(cursor, PAGE_SIZE) }) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> {
                    if (wasReset || !result.error.requiresReset(cursor)) return result.error
                    // The cursor cannot be continued (offline-sync.md, "Reset"): pull from the beginning. Nothing is
                    // removed, so the list stays as it is; the games the pull doesn't bring are pushed when it ends.
                    store.transaction {
                        run.checkOwner()
                        store.markGamesWithoutPendingChangeUnconfirmed()
                        store.putSyncState(store.syncState().copy(cursor = null))
                    }
                    wasReset = true
                    continue
                }
            }
            store.transaction {
                run.checkOwner()
                applyPage(run, page)
            }
            if (!page.hasMore) return null
        }
    }

    /** Merges one page into the local data and stores its cursor (offline-sync.md, "Pull"). */
    private suspend fun applyPage(run: Run, page: GameChanges) {
        val pending = store.pendingChanges().associateBy { it.gameId }
        // Left by a reset, also by one a failure or a restart interrupted; usually empty.
        val unconfirmed = store.unconfirmedGameIds().toMutableSet()
        for (game in page.games) {
            if (unconfirmed.remove(game.id)) store.confirmGame(game.id)
            val change = pending[game.id]
            when (change?.kind) {
                null -> store.putGame(game)
                Kind.UPDATE -> {
                    val local = store.game(game.id)
                    when {
                        local != null -> store.putGame(GameFields.merge(game, local, change.fields))
                        // Stored, but this version can't read it: like for a CREATE, the server's game would replace
                        // the values still waiting to be pushed.
                        store.isStored(game.id) -> Unit
                        else -> store.putGame(game)
                    }
                }
                // CREATE: the local version is newer. DELETE: stays deleted.
                Kind.CREATE, Kind.DELETE -> Unit
            }
        }
        // Skipped, as this version can't decode them; the local copy stays as it is. The server has them, though, so
        // they are not pushed back as new games.
        for (id in page.undecodableGameIds) {
            if (unconfirmed.remove(id)) store.confirmGame(id)
        }
        for (id in page.deletedIds) {
            if (unconfirmed.remove(id)) store.confirmGame(id)
            store.removeGame(id)
            if (id in pending) store.removePendingChange(id)
        }
        if (!page.hasMore && unconfirmed.isNotEmpty()) {
            // The rest of a reset's games is not on the server (e.g. created after the backup it was restored
            // from): pushed as new games. A game edited or deleted meanwhile has its own pending change already.
            val now = clock.millis()
            for (id in unconfirmed) {
                if (store.pendingChange(id) == null && store.game(id) != null) {
                    store.putPendingChange(PendingChange.create(id, now))
                }
            }
            store.clearUnconfirmedGames()
            run.requestsAnotherRun = true
        }
        val state = store.syncState()
        store.putSyncState(
            state.copy(
                cursor = page.cursor,
                lastSyncedAt = if (page.hasMore) state.lastSyncedAt else clock.instant(),
                appVersion = appVersion,
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
        /** The first change that failed; the run counts as failed although it went on. */
        var changeError: AppError? = null
            private set

        /** One more run has to follow this one. */
        var requestsAnotherRun = false

        fun changeFailed(error: AppError) {
            if (changeError == null) changeError = error
        }
    }

    private sealed interface Prepared {
        data object NothingToSend : Prepared

        /** The game of a pending CREATE / UPDATE is not in the local store. */
        data object GameMissing : Prepared

        class Attempt(val change: PendingChange, val game: Game?) : Prepared {
            fun requireGame(): Game = checkNotNull(game)
        }
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

        /** This change failed: it stays queued, the push goes on with the next one. */
        data class Failed(val error: AppError) : PushOutcome

        /** The server can't be used now: the run ends, every change stays queued. */
        data class ServerUnavailable(val error: AppError) : PushOutcome
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
        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val INITIAL_BACKOFF_MILLIS = 2_000L
        private const val MAX_BACKOFF_MILLIS = 5 * 60_000L

        /** 2 s, 4 s, 8 s … at most 5 minutes. */
        @VisibleForTesting
        internal fun backoffMillis(failedRuns: Int): Long =
            (INITIAL_BACKOFF_MILLIS shl (failedRuns - 1).coerceIn(0, 20)).coerceAtMost(MAX_BACKOFF_MILLIS)

        /** Deleted (on another device). A `404` without this code says nothing about the game. */
        private fun AppError.isGameNotFound() =
            this is AppError.Api && statusCode == HTTP_NOT_FOUND && code == ErrorCode.GAME_NOT_FOUND

        /** The server has no record of the game at all, not even a tombstone (`PATCH`, `PUT`, `GET`). */
        private fun AppError.isGameUnknown() =
            this is AppError.Api && statusCode == HTTP_NOT_FOUND && code == ErrorCode.GAME_UNKNOWN

        /**
         * `400 VALIDATION_FAILED` or `409 CONFLICT` from the API: the change can never succeed as it is. Any other
         * error of the API only fails this change, which is sent again in a later run.
         */
        private fun AppError.isPermanentRejection() = this is AppError.Api && (
            (statusCode == HTTP_BAD_REQUEST && code == ErrorCode.VALIDATION_FAILED) ||
                (statusCode == HTTP_CONFLICT && code == ErrorCode.CONFLICT)
            )

        /**
         * Network failure, timeout, `401`, `408`, `429` or a response without an API error body ([AppError.Http],
         * which did not come from the API): nothing can be pushed now, so the run ends.
         */
        private fun AppError.isServerUnavailable() = when (this) {
            AppError.Network, is AppError.Http -> true
            is AppError.Api -> statusCode in setOf(HTTP_UNAUTHORIZED, HTTP_REQUEST_TIMEOUT, HTTP_TOO_MANY_REQUESTS)
            AppError.Unexpected -> false
        }

        /** `410 SYNC_RESET_REQUIRED`, or a malformed cursor (`400 VALIDATION_FAILED`). */
        private fun AppError.requiresReset(cursor: String?) = this is AppError.Api && (
            statusCode == HTTP_GONE || code == ErrorCode.SYNC_RESET_REQUIRED ||
                (cursor != null && statusCode == HTTP_BAD_REQUEST && code == ErrorCode.VALIDATION_FAILED)
            )
    }
}
