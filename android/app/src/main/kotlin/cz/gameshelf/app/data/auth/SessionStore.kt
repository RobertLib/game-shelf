package cz.gameshelf.app.data.auth

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cz.gameshelf.app.domain.model.User
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

@Serializable
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val user: User,
)

interface SessionStore {
    suspend fun load(): StoredSession?
    suspend fun save(session: StoredSession)
    suspend fun clear()
}

/** Persists the session in Preferences DataStore as a single value encrypted by [cipher]. */
class EncryptedSessionStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: PayloadCipher,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SessionStore {

    override suspend fun load(): StoredSession? = withContext(ioDispatcher) {
        val encoded = dataStore.data.first()[SESSION_KEY] ?: return@withContext null
        try {
            val plaintext = cipher.decrypt(Base64.getDecoder().decode(encoded))
            json.decodeFromString<StoredSession>(plaintext.decodeToString())
        } catch (e: Exception) {
            // Key invalidated, data restored onto another device, or a format change: start signed out.
            Log.w(TAG, "Discarding unreadable session", e)
            clear()
            null
        }
    }

    override suspend fun save(session: StoredSession) = withContext(ioDispatcher) {
        val payload = cipher.encrypt(json.encodeToString(StoredSession.serializer(), session).encodeToByteArray())
        val encoded = Base64.getEncoder().encodeToString(payload)
        dataStore.edit { it[SESSION_KEY] = encoded }
        Unit
    }

    override suspend fun clear() {
        withContext(ioDispatcher) { dataStore.edit { it.remove(SESSION_KEY) } }
    }

    private companion object {
        const val TAG = "SessionStore"
        val SESSION_KEY = stringPreferencesKey("session")
    }
}
