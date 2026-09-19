package org.meetagain.app.feature.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meetagain.app.AppInfo
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult

data class AboutUiState(val appInfo: AppInfo, val serverCheck: ServerCheck = ServerCheck.Checking)

sealed interface ServerCheck {
    data object Checking : ServerCheck

    data object Reachable : ServerCheck

    data class Failed(val error: ApiError) : ServerCheck
}

class AboutViewModel(private val api: ApiClient, appInfo: AppInfo) : ViewModel() {
    private val _state = MutableStateFlow(AboutUiState(appInfo))
    val state: StateFlow<AboutUiState> = _state.asStateFlow()

    init {
        checkServer()
    }

    fun checkServer() {
        _state.update { it.copy(serverCheck = ServerCheck.Checking) }
        viewModelScope.launch {
            val check = when (val result = api.status()) {
                is ApiResult.Success -> ServerCheck.Reachable
                is ApiResult.Failure -> ServerCheck.Failed(result.error)
            }
            _state.update { it.copy(serverCheck = check) }
        }
    }
}
