package cz.gameshelf.app.data.sync

import cz.gameshelf.app.data.sync.PendingChange.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The table "Local changes" of offline-sync.md. */
class PendingChangeTest {

    private val create = PendingChange.create(ID, now = 1)

    @Test
    fun `an edit without a pending change becomes an UPDATE of its fields`() {
        assertEquals(
            PendingChange(ID, Kind.UPDATE, setOf("title"), revision = 1, queuedAt = 5),
            null.afterEdit(ID, setOf("title"), now = 5),
        )
    }

    @Test
    fun `edits of an UPDATE are merged and keep its place in the queue`() {
        val update = PendingChange(ID, Kind.UPDATE, setOf("title"), revision = 1, attempted = true, queuedAt = 1)

        assertEquals(
            update.copy(fields = setOf("title", "rating"), revision = 2),
            update.afterEdit(ID, setOf("rating"), now = 9),
        )
    }

    @Test
    fun `a CREATE that was not sent stays a CREATE`() {
        assertEquals(create.copy(revision = 2), create.afterEdit(ID, setOf("title"), now = 9))
    }

    @Test
    fun `a CREATE that was sent remembers the fields edited since`() {
        val attempted = create.copy(attempted = true)

        val edited = attempted.afterEdit(ID, setOf("title"), now = 9).afterEdit(ID, setOf("rating"), now = 10)

        assertEquals(attempted.copy(fields = setOf("title", "rating"), revision = 3), edited)
    }

    @Test
    fun `deleting a game without a pending change or with an UPDATE queues a DELETE`() {
        assertEquals(PendingChange(ID, Kind.DELETE, queuedAt = 5), null.afterDelete(ID, now = 5))

        val update = PendingChange(ID, Kind.UPDATE, setOf("title"), revision = 2, queuedAt = 1)
        assertEquals(PendingChange(ID, Kind.DELETE, revision = 3, queuedAt = 1), update.afterDelete(ID, now = 5))
    }

    @Test
    fun `deleting a CREATE the server never saw leaves nothing to push`() {
        assertNull(create.afterDelete(ID, now = 5))
    }

    @Test
    fun `deleting a CREATE that was sent queues a DELETE`() {
        val attempted = create.copy(attempted = true, fields = setOf("title"))

        assertEquals(
            PendingChange(ID, Kind.DELETE, revision = 2, attempted = true, queuedAt = 1),
            attempted.afterDelete(ID, now = 5),
        )
    }

    private companion object {
        const val ID = "game-1"
    }
}
