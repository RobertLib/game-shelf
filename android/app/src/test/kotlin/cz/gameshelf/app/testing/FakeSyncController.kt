package cz.gameshelf.app.testing

import cz.gameshelf.app.data.sync.SyncController
import cz.gameshelf.app.data.sync.SyncEvent
import cz.gameshelf.app.data.sync.SyncStatus
import cz.gameshelf.app.domain.model.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/** Sync status set by the test; [syncNow] answers [nextResult], after [gate] when one is set. */
class FakeSyncController(
    initial: SyncStatus = SyncStatus(lastSyncedAt = Instant.EPOCH),
) : SyncController {

    override val status = MutableStateFlow(initial)
    override val events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 8)

    var requestCount = 0
    var syncNowCount = 0
    var nextResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var gate: CompletableDeferred<Unit>? = null

    override fun requestSync() {
        requestCount++
    }

    override suspend fun syncNow(): ApiResult<Unit> {
        syncNowCount++
        gate?.await()
        return nextResult
    }
}
