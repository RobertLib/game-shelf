package cz.gameshelf.app.data.auth

import android.util.Log
import java.io.File
import java.io.IOException

/**
 * The *wipe pending* marker (offline-sync.md, "Local data"): stored before a sign-out clears the stored session and
 * wipes the local data, and removed only when both are done. A wipe the app couldn't finish – it failed, or the app
 * was killed half-way – is finished at the next start, before the session is restored or anything is shown
 * ([finishAtStart]).
 */
class PendingWipe(private val marker: File) {

    val isPending: Boolean get() = marker.exists()

    /** @return whether the marker is stored */
    fun mark(): Boolean = try {
        marker.createNewFile() || marker.exists()
    } catch (e: IOException) {
        Log.e(TAG, "Couldn't store the wipe pending marker", e)
        false
    }

    fun clear() {
        if (!marker.delete() && marker.exists()) Log.e(TAG, "Couldn't remove the wipe pending marker")
    }

    /**
     * At start, before the stored session is read and before the local data is opened: when a wipe is pending, runs
     * every one of [steps] – each deletes a part of the data and tells whether it is gone – and removes the marker only
     * when all of them succeeded, so that a part that is left is deleted at the next start.
     * @return whether a wipe was pending
     */
    fun finishAtStart(vararg steps: () -> Boolean): Boolean {
        if (!isPending) return false
        Log.w(TAG, "Finishing the wipe of a sign-out that didn't complete")
        val done = steps.map { step ->
            try {
                step()
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't wipe a part of the local data", e)
                false
            }
        }
        if (done.all { it }) clear()
        return true
    }

    private companion object {
        const val TAG = "PendingWipe"
    }
}
