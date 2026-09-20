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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * The parts of push that belong to this phone rather than to the member: how often it looks for news without a
 * distributor, and whether Android's notification permission has already been asked for once.
 *
 * The categories and quiet hours are not here - those are the member's, the same on every device they use, and
 * live on the server.
 */
class PushPreferences(private val store: DataStore<Preferences>) {
    val interval: Flow<PushInterval> = data().map { stored ->
        stored[INTERVAL]?.let { name -> PushInterval.entries.firstOrNull { it.name == name } } ?: PushInterval.Hourly
    }

    /** Asked once. A member who said no is not asked again; the system settings are where they change their mind. */
    val permissionAsked: Flow<Boolean> = data().map { it[PERMISSION_ASKED] == true }

    suspend fun setInterval(interval: PushInterval) {
        store.edit { it[INTERVAL] = interval.name }
    }

    suspend fun markPermissionAsked() {
        store.edit { it[PERMISSION_ASKED] = true }
    }

    private fun data() = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    companion object {
        private val INTERVAL = stringPreferencesKey("push_interval")
        private val PERMISSION_ASKED = booleanPreferencesKey("push_permission_asked")

        fun open(context: Context): PushPreferences = PushPreferences(
            PreferenceDataStoreFactory.create { File(context.filesDir, "push.preferences_pb") }
        )
    }
}
