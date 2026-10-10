package cz.gameshelf.app.data.auth

import cz.gameshelf.app.data.api.ApiJson
import cz.gameshelf.app.domain.model.User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.GeneralSecurityException
import java.time.Instant

/** The session's DataStore file on disk, with a stand-in for the Keystore cipher. */
class EncryptedSessionStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file: File get() = File(folder.root, "session.preferences_pb")
    private val cipher = FakeCipher()

    private fun store() = EncryptedSessionStore(sessionDataStore { file }, cipher, ApiJson)

    @Test
    fun `saves and loads the session`() = runTest {
        val store = store()

        store.save(SESSION)

        assertEquals(SESSION, store.load())
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun `a corrupted file starts signed out instead of failing every launch`() = runTest {
        file.writeText("not a preferences file")
        val store = store()

        assertNull(store.load())

        // And the store works again.
        store.save(SESSION)
        assertEquals(SESSION, store.load())
    }

    @Test
    fun `the app starts signed out with a corrupted file`() = runTest {
        file.writeText("not a preferences file")

        val manager = SessionManager(store(), this)

        assertEquals(SessionState.SignedOut, manager.state.first { it != SessionState.Loading })
    }

    @Test
    fun `a session that can't be decrypted is discarded`() = runTest {
        val store = store()
        store.save(SESSION)

        cipher.keyInvalidated = true
        assertNull(store.load())

        cipher.keyInvalidated = false
        assertNull(store.load())
    }

    /** Stands in for the Keystore cipher; its key can be invalidated, e.g. by a new screen lock. */
    private class FakeCipher : PayloadCipher {
        var keyInvalidated = false

        override fun encrypt(plaintext: ByteArray) = plaintext.reversedArray()

        override fun decrypt(payload: ByteArray): ByteArray {
            if (keyInvalidated) throw GeneralSecurityException("key invalidated")
            return payload.reversedArray()
        }
    }

    private companion object {
        val SESSION = StoredSession("access", "refresh", User("user-1", "a@example.com", null, Instant.EPOCH))
    }
}
