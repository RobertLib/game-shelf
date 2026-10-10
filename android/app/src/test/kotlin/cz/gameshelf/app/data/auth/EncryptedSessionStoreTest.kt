package cz.gameshelf.app.data.auth

import android.security.keystore.KeyPermanentlyInvalidatedException
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
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import java.time.Instant
import javax.crypto.AEADBadTagException

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
    fun `a session that can never be decrypted is discarded`() = runTest {
        val store = store()

        listOf(
            AEADBadTagException("restored onto another device"),
            KeyPermanentlyInvalidatedException("new screen lock"),
            UnrecoverableKeyException("key gone"),
            IllegalArgumentException("malformed IV"),
        ).forEach { failure ->
            store.save(SESSION)

            cipher.failure = failure
            assertNull(store.load())

            cipher.failure = null
            assertNull(failure.toString(), store.load())
        }
    }

    @Test
    fun `a malformed payload is discarded`() = runTest {
        val store = store()
        store.save(SESSION)

        // Not the JSON of a session.
        cipher.plaintext = "{\"accessToken\":1}".encodeToByteArray()
        assertNull(store.load())

        cipher.plaintext = null
        assertNull(store.load())
    }

    @Test
    fun `a session the Keystore can't decrypt for now is kept for a later start`() = runTest {
        val store = store()
        store.save(SESSION)

        listOf(KeyStoreException("busy"), ProviderException("keystore daemon died"), GeneralSecurityException("?"))
            .forEach { failure ->
                cipher.failure = failure
                assertNull(store.load())

                cipher.failure = null
                assertEquals(failure.toString(), SESSION, store.load())
            }
    }

    /** Stands in for the Keystore cipher, which can fail (e.g. a key invalidated by a new screen lock). */
    private class FakeCipher : PayloadCipher {
        var failure: Exception? = null

        /** Decrypts to this instead, e.g. a payload of another format. */
        var plaintext: ByteArray? = null

        override fun encrypt(plaintext: ByteArray) = plaintext.reversedArray()

        override fun decrypt(payload: ByteArray): ByteArray {
            failure?.let { throw it }
            return plaintext ?: payload.reversedArray()
        }
    }

    private companion object {
        val SESSION = StoredSession("access", "refresh", User("user-1", "a@example.com", null, Instant.EPOCH))
    }
}
