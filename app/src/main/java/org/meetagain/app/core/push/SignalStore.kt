package org.meetagain.app.core.push

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.meetagain.app.core.auth.TokenCipher
import org.meetagain.app.core.auth.keystoreCipher
import org.meetagain.app.core.data.QuietHours
import org.meetagain.app.core.data.toDto
import org.meetagain.app.core.network.QuietHoursDto

/**
 * What push needs while the app lock is on, kept apart from the session: the signal token, the last signal value,
 * and a copy of the member's quiet hours and whether they want push at all.
 *
 * The token's key needs no fingerprint, so the timer can use it with the phone locked. That is safe because the token
 * can do nothing but ask whether anything changed; the key still never leaves the phone.
 */
class SignalStore(private val store: DataStore<Preferences>, private val cipher: TokenCipher) {
    val hasToken: Flow<Boolean> = data().map { it[TOKEN] != null }

    suspend fun token(): SignalToken? {
        val stored = data().first()
        val token = stored[TOKEN]?.let(cipher::decrypt) ?: return null
        val expires = stored[EXPIRES]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        return SignalToken(token, expires)
    }

    suspend fun writeToken(token: SignalToken) {
        store.edit {
            it[TOKEN] = cipher.encrypt(token.value)
            it[EXPIRES] = token.expiresAt.toString()
        }
    }

    /** The last value stays: it belongs to the member, not to the token, so the next token carries on from it. */
    suspend fun dropToken() {
        store.edit {
            it.remove(TOKEN)
            it.remove(EXPIRES)
        }
    }

    suspend fun lastState(): String? = data().first()[LAST_STATE]

    suspend fun writeLastState(state: String) {
        store.edit { it[LAST_STATE] = state }
    }

    suspend fun settings(): PushSnapshot {
        val stored = data().first()
        val quietHours = stored[QUIET_HOURS]?.let { text ->
            runCatching { json.decodeFromString(QuietHoursDto.serializer(), text) }.getOrNull()
        }
        return PushSnapshot(
            wanted = stored[WANTED] ?: true,
            quietHours = quietHours?.let { QuietHours(it.enabled, it.start, it.end, it.timeZone, it.allowUrgent) }
        )
    }

    suspend fun writeSettings(snapshot: PushSnapshot) {
        store.edit {
            it[WANTED] = snapshot.wanted
            val quietHours = snapshot.quietHours
            if (quietHours == null) {
                it.remove(QUIET_HOURS)
            } else {
                it[QUIET_HOURS] = json.encodeToString(QuietHoursDto.serializer(), quietHours.toDto())
            }
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun data() = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    companion object {
        private val TOKEN = stringPreferencesKey("token")
        private val EXPIRES = stringPreferencesKey("expires_at")
        private val LAST_STATE = stringPreferencesKey("last_state")
        private val WANTED = booleanPreferencesKey("push_wanted")
        private val QUIET_HOURS = stringPreferencesKey("quiet_hours")
        private val json = Json { ignoreUnknownKeys = true }

        fun open(context: Context, cipher: TokenCipher = keystoreCipher("signal-token")) = SignalStore(
            PreferenceDataStoreFactory.create { File(context.filesDir, "signal.preferences_pb") },
            cipher
        )
    }
}

data class SignalToken(val value: String, val expiresAt: Instant)

/** The member's push settings as they were when the app was last unlocked, for the timer to go by while locked. */
data class PushSnapshot(val wanted: Boolean, val quietHours: QuietHours?)
