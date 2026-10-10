package cz.gameshelf.app.testing

import cz.gameshelf.app.data.sync.SyncController
import cz.gameshelf.app.data.sync.SyncEvent
import cz.gameshelf.app.data.sync.SyncStatus
import cz.gameshelf.app.domain.model.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Instant

/**
 * Sync status set by the test; [syncNow] answers [nextResult], after [gate] when one is set. Events sent to
 * [eventChannel] wait for a collector, like the engine's.
 */
class FakeSyncController(
    initial: SyncStatus = SyncStatus(lastSyncedAt = Instant.EPOCH),
) : SyncController {

    override val status = MutableStateFlow(initial)
    val eventChannel = Channel<SyncEvent>(Channel.UNLIMITED)
    override val events = eventChannel.receiveAsFlow()

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
