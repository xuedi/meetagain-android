package org.meetagain.app.feature.notificationsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.NotificationSetting
import org.meetagain.app.core.data.NotificationSettings
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent

/**
 * The same switches as the website's profile settings. A switch applies at once and sends only its own key; the
 * server answers with everything it has stored, so the settings the app has no screen for cannot be written back
 * stale. A refused change says so and the switch goes back to where it was, because the stored answer never moved.
 */
class NotificationSettingsViewModel(private val repository: MemberRepository) : ViewModel() {
    private val content = StoredContent(viewModelScope, repository.notificationSettings()) {
        repository.refreshNotificationSettings()
    }

    val state: StateFlow<Loadable<NotificationSettings>> = content.state

    private val saving = MutableStateFlow<Set<NotificationSetting>>(emptySet())
    val pending: StateFlow<Set<NotificationSetting>> = saving

    private val failure = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = failure

    fun load() = content.reload()

    fun set(setting: NotificationSetting, value: Boolean) {
        if (setting in saving.value) return
        saving.value = saving.value + setting
        viewModelScope.launch {
            val result = repository.setNotificationSetting(setting, value)
            saving.value = saving.value - setting
            if (result is ApiResult.Failure) failure.value = true
        }
    }

    fun dismissFailure() {
        failure.value = false
    }
}
