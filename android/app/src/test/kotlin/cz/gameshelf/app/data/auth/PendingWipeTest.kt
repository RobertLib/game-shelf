package cz.gameshelf.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** The *wipe pending* marker and finishing a wipe at start (offline-sync.md, "Local data"). */
class PendingWipeTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val wipe by lazy { PendingWipe(File(folder.root, "pending-wipe")) }

    @Test
    fun `the marker is stored until cleared`() {
        assertFalse(wipe.isPending)

        assertTrue(wipe.mark())
        assertTrue(wipe.mark())
        assertTrue(wipe.isPending)

        wipe.clear()
        assertFalse(wipe.isPending)
    }

    @Test
    fun `without a marker nothing is deleted at start`() {
        var ran = false

        assertFalse(wipe.finishAtStart({ ran = true; true }))

        assertFalse(ran)
    }

    @Test
    fun `a pending wipe is finished at start and the marker removed`() {
        wipe.mark()
        val done = mutableListOf<String>()

        assertTrue(wipe.finishAtStart({ done += "session"; true }, { done += "database"; true }))

        assertEquals(listOf("session", "database"), done)
        assertFalse(wipe.isPending)
    }

    @Test
    fun `a part that can't be deleted keeps the marker, and the others are deleted anyway`() {
        wipe.mark()
        val done = mutableListOf<String>()

        wipe.finishAtStart({ false }, { throw IOException("disk") }, { done += "covers"; true })

        assertEquals(listOf("covers"), done)
        assertTrue(wipe.isPending)

        // The next start tries again.
        wipe.finishAtStart({ true }, { true }, { true })
        assertFalse(wipe.isPending)
    }
}
