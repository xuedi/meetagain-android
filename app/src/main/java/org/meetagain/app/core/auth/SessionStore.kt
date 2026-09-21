package org.meetagain.app.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * Keeps the session on disk between starts: the member behind the token, the scopes the server granted it, and the
 * token itself, which is never written in the clear.
 *
 * With the app lock on, the token is sealed by a [LockedCipher] instead, which only a prompt can open: it is then
 * read back sealed, and the member stays known while the token does not.
 */
class SessionStore(private val store: DataStore<Preferences>, private val cipher: TokenCipher) {
    /** Null when nobody is signed in, or when the stored token can no longer be decrypted. */
    suspend fun read(): StoredSession? {
        val stored = data().first()
        val id = stored[MEMBER_ID] ?: return null
        val token = stored[TOKEN] ?: return null
        val name = stored[MEMBER_NAME].orEmpty()
        val scopes = stored[SCOPES].orEmpty()
        if (stored[LOCKED] == true) return StoredSession.Locked(id, name, scopes, sealed = token)
        return cipher.decrypt(token)?.let { StoredSession.Open(Session(id, name, it, scopes)) }
    }

    /** Whether the app lock is on, read from disk: work in the background may start before anything else has. */
    suspend fun lockOn(): Boolean = data().first()[LOCKED] == true

    suspend fun write(session: Session) {
        store.edit {
            it[MEMBER_ID] = session.memberId
            it[MEMBER_NAME] = session.name
            it[TOKEN] = cipher.encrypt(session.token)
            it[SCOPES] = session.scopes
            it.remove(LOCKED)
        }
    }

    /** [sealed] is the token as the [LockedCipher] left it. */
    suspend fun writeLocked(session: Session, sealed: String) {
        store.edit {
            it[MEMBER_ID] = session.memberId
            it[MEMBER_NAME] = session.name
            it[TOKEN] = sealed
            it[SCOPES] = session.scopes
            it[LOCKED] = true
        }
    }

    /** The member's name can change while the session lasts; the token does not. */
    suspend fun writeName(name: String) = store.edit { it[MEMBER_NAME] = name }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun data() = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    companion object {
        private val MEMBER_ID = intPreferencesKey("member_id")
        private val MEMBER_NAME = stringPreferencesKey("member_name")
        private val TOKEN = stringPreferencesKey("token")
        private val SCOPES = stringSetPreferencesKey("scopes")
        private val LOCKED = booleanPreferencesKey("locked")

        fun open(context: Context, cipher: TokenCipher = keystoreCipher()) = SessionStore(
            PreferenceDataStoreFactory.create { File(context.filesDir, "session.preferences_pb") },
            cipher
        )
    }
}

/** What is on disk: a session ready to use, or one whose token waits for the member's fingerprint or PIN. */
sealed interface StoredSession {
    data class Open(val session: Session) : StoredSession

    data class Locked(val memberId: Int, val name: String, val scopes: Set<String>, val sealed: String) : StoredSession
}
