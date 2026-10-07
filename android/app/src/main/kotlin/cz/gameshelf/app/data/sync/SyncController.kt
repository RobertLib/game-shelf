package cz.gameshelf.app.data.sync

import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.AppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/** Sync state shown in the apps (offline-sync.md, "Status shown in the apps"). */
data class SyncStatus(
    /** A run is in progress. */
    val isSyncing: Boolean = false,
    /** The last attempt could not reach the server, or the device has no connection. */
    val isOffline: Boolean = false,
    /** Local changes waiting to be pushed. */
    val pendingCount: Int = 0,
    /** End of the last completed sync; `null` until the first complete pull. */
    val lastSyncedAt: Instant? = null,
    /** Why the last finished run failed; `null` when it succeeded or none finished yet. */
    val lastError: AppError? = null,
) {
    val hasCompletedInitialSync: Boolean get() = lastSyncedAt != null
}

sealed interface SyncEvent {
    /** Changes the server rejected permanently were undone. */
    data object ChangesRejected : SyncEvent
}

/** What the UI needs from the sync engine. */
interface SyncController {
    val status: StateFlow<SyncStatus>

    val events: Flow<SyncEvent>

    /** Runs a sync soon (or once more after the running one); failures are retried automatically. */
    fun requestSync()

    /**
     * Pull-to-refresh / "Sync now": runs a sync and reports its result. Throws
     * [kotlinx.coroutines.CancellationException] when the run is cancelled because the session ended.
     */
    suspend fun syncNow(): ApiResult<Unit>
}
