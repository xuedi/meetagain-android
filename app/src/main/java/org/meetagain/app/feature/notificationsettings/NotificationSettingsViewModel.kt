package org.meetagain.app.feature.notificationsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.NotificationSetting
import org.meetagain.app.core.data.NotificationSettings
import org.meetagain.app.core.data.PushCategory
import org.meetagain.app.core.data.QuietHours
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.push.PushInterval
import org.meetagain.app.core.push.PushPreferences
import org.meetagain.app.core.push.PushRegistrar
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent

/**
 * The same switches as the website's profile settings, plus the push categories and quiet hours this phone acts on.
 *
 * A switch applies at once and sends only its own key; the server answers with everything it has stored, so the
 * settings this screen cannot show cannot be written back stale. A refused change says so and the switch is back
 * where it was, because the stored answer never moved.
 */
class NotificationSettingsViewModel(
    private val repository: MemberRepository,
    private val preferences: PushPreferences? = null,
    private val registrar: PushRegistrar? = null,
    private val onPushWanted: (Boolean, PushInterval) -> Unit = { _, _ -> },
    private val canPostNotifications: () -> Boolean = { true }
) : ViewModel() {
    private val content = StoredContent(viewModelScope, repository.notificationSettings()) {
        repository.refreshNotificationSettings()
    }

    val state: StateFlow<Loadable<NotificationSettings>> = content.state

    private val saving = MutableStateFlow<Set<String>>(emptySet())
    val pending: StateFlow<Set<String>> = saving

    private val failure = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = failure

    private val currentPush = MutableStateFlow(PushUiState())
    val push: StateFlow<PushUiState> = currentPush

    init {
        preferences?.let { store ->
            viewModelScope.launch { currentPush.value = currentPush.value.copy(interval = store.interval.first()) }
        }
    }

    fun load() = content.reload()

    fun set(setting: NotificationSetting, value: Boolean) = write(setting.key) {
        repository.setNotificationSetting(setting, value)
    }

    /**
     * Turning on the first category is what asks Android for permission - never the first launch, and never twice.
     * It is also what gets this phone registered; turning the last one off unregisters it again.
     */
    fun setCategory(category: PushCategory, value: Boolean) = write(category.key) {
        val result = repository.setPushCategory(category, value)
        if (result is ApiResult.Success) afterCategoryChange(value)
        result
    }

    fun setQuietHours(quietHours: QuietHours) = write(QUIET_HOURS) { repository.setQuietHours(quietHours) }

    fun matchEmailSettings() {
        val settings = (state.value as? Loadable.Loaded)?.value ?: return
        write(MATCH) {
            val result = repository.matchPushToEmail(settings)
            if (result is ApiResult.Success) afterCategoryChange(true)
            result
        }
    }

    fun setInterval(interval: PushInterval) {
        currentPush.value = currentPush.value.copy(interval = interval)
        viewModelScope.launch {
            preferences?.setInterval(interval)
            if (anyCategoryOn()) onPushWanted(true, interval)
        }
    }

    /** Android has answered the permission request; what it said decides whether anything can be shown at all. */
    fun onPermissionResult(granted: Boolean) {
        currentPush.value = currentPush.value.copy(askPermission = false, permissionDenied = !granted)
        viewModelScope.launch { preferences?.markPermissionAsked() }
    }

    fun dismissFailure() {
        failure.value = false
    }

    private suspend fun afterCategoryChange(turnedOn: Boolean) {
        val on = anyCategoryOn()
        if (turnedOn && !canPostNotifications()) {
            val asked = preferences?.permissionAsked?.first() == true
            currentPush.value = currentPush.value.copy(askPermission = !asked, permissionDenied = asked)
        }
        currentPush.value = currentPush.value.copy(
            obstacle = if (on) registrar?.register() else null
        )
        if (!on) registrar?.unregister()
        onPushWanted(on, currentPush.value.interval)
    }

    private suspend fun anyCategoryOn(): Boolean =
        repository.notificationSettings().first()?.value?.other?.push?.values?.any { it } == true

    private fun write(key: String, action: suspend () -> ApiResult<Unit>) {
        if (key in saving.value) return
        saving.value = saving.value + key
        viewModelScope.launch {
            val result = action()
            saving.value = saving.value - key
            if (result is ApiResult.Failure) failure.value = true
        }
    }

    companion object {
        const val QUIET_HOURS = "quietHours"
        const val MATCH = "match"
    }
}
