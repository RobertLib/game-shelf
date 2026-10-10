package cz.gameshelf.app.testing

import cz.gameshelf.app.data.auth.SessionStore
import cz.gameshelf.app.data.auth.StoredSession

/** In-memory [SessionStore]; [loadFailure] / [saveFailure] make it fail like a broken Keystore or disk. */
class FakeSessionStore(var session: StoredSession? = null) : SessionStore {

    var loadFailure: Exception? = null
    var saveFailure: Exception? = null

    override suspend fun load(): StoredSession? {
        loadFailure?.let { throw it }
        return session
    }

    override suspend fun save(session: StoredSession) {
        saveFailure?.let { throw it }
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }
}
