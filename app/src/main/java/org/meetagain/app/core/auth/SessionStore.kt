package org.meetagain.app.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
 */
class SessionStore(private val store: DataStore<Preferences>, private val cipher: TokenCipher) {
    /** Null when nobody is signed in, or when the stored token can no longer be decrypted. */
    suspend fun read(): Session? {
        val stored = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()
        val token = stored[TOKEN]?.let(cipher::decrypt) ?: return null
        val id = stored[MEMBER_ID] ?: return null
        return Session(id, stored[MEMBER_NAME].orEmpty(), token, stored[SCOPES].orEmpty())
    }

    suspend fun write(session: Session) {
        store.edit {
            it[MEMBER_ID] = session.memberId
            it[MEMBER_NAME] = session.name
            it[TOKEN] = cipher.encrypt(session.token)
            it[SCOPES] = session.scopes
        }
    }

    /** The member's name can change while the session lasts; the token does not. */
    suspend fun writeName(name: String) = store.edit { it[MEMBER_NAME] = name }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    companion object {
        private val MEMBER_ID = intPreferencesKey("member_id")
        private val MEMBER_NAME = stringPreferencesKey("member_name")
        private val TOKEN = stringPreferencesKey("token")
        private val SCOPES = stringSetPreferencesKey("scopes")

        fun open(context: Context, cipher: TokenCipher = keystoreCipher()) = SessionStore(
            PreferenceDataStoreFactory.create { File(context.filesDir, "session.preferences_pb") },
            cipher
        )
    }
}
