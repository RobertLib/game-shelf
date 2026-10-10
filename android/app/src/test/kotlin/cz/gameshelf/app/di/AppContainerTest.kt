package cz.gameshelf.app.di

import android.app.Application
import androidx.datastore.preferences.preferencesDataStoreFile
import cz.gameshelf.app.data.auth.SessionState
import cz.gameshelf.app.data.local.GameShelfDatabase
import cz.gameshelf.app.data.local.LocalGameStore
import cz.gameshelf.app.testing.testGame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * A wipe left pending by a sign-out the app couldn't finish (offline-sync.md, "Local data") is finished when the
 * app starts, before the stored session is read and the local data opened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppContainerTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private val marker = File(context.noBackupFilesDir, "pending-wipe")
    private val sessionFile = context.preferencesDataStoreFile("session")
    private val covers = File(context.cacheDir, "coil3_disk_cache")

    /** What a signed-in user leaves on the device: a stored session, the collection and cached covers. */
    private fun leaveUserData() = runBlocking {
        sessionFile.parentFile?.mkdirs()
        sessionFile.writeText("the stored session")
        val database = GameShelfDatabase.create(context)
        LocalGameStore(database).putGame(testGame("10000000-0000-4000-8000-000000000001"))
        database.close()
        covers.mkdirs()
        covers.resolve("cover").writeText("jpeg")
    }

    @Test
    fun `a wipe left pending is finished before anything is read`() = runBlocking {
        leaveUserData()
        // The app was killed half-way through signing out.
        marker.createNewFile()

        val container = AppContainer(context)

        assertFalse(sessionFile.exists())
        assertFalse(covers.exists())
        assertFalse(marker.exists())
        assertEquals(SessionState.SignedOut, container.sessionManager.state.first { it != SessionState.Loading })
        assertEquals(emptyList<Any>(), container.gamesRepository.games.first())
    }

    @Test
    fun `without a pending wipe the data stays`() = runBlocking {
        leaveUserData()

        val container = AppContainer(context)

        assertTrue(sessionFile.exists())
        assertTrue(covers.resolve("cover").exists())
        assertEquals(1, container.gamesRepository.games.first().size)
    }
}
