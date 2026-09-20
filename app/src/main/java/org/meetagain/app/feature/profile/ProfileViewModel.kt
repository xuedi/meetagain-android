package org.meetagain.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meetagain.app.core.auth.AuthRepository
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.Profile
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.network.ProfileChangeDto
import org.meetagain.app.core.network.Upload
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent

/** The profile as the form holds it, which is the stored one until the member changes something. */
data class ProfileEdit(
    val name: String,
    val bio: String,
    val language: String,
    val public: Boolean,
    val avatarUrl: String?
)

enum class ProfileMessage { Saved, NameRequired, NameTooLong, LanguageNotAllowed, AvatarRejected, Offline, Failed }

class ProfileViewModel(private val repository: MemberRepository, private val auth: AuthRepository) : ViewModel() {
    private val content = StoredContent(viewModelScope, repository.profile(), repository::refreshProfile)

    val state: StateFlow<Loadable<ProfileEdit>> = content.state
        .map { loadable ->
            when (loadable) {
                Loadable.Loading -> Loadable.Loading

                is Loadable.Failed -> loadable

                is Loadable.Loaded ->
                    Loadable.Loaded(loadable.value.toEdit(), loadable.refreshing, loadable.stale)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Loadable.Loading)

    /** Null while the member has changed nothing, so the form shows what is stored and "Save" is off. */
    private val currentEdit = MutableStateFlow<ProfileEdit?>(null)
    val edit: StateFlow<ProfileEdit?> = currentEdit

    private val currentMessage = MutableStateFlow<ProfileMessage?>(null)
    val message: StateFlow<ProfileMessage?> = currentMessage

    fun load() = content.reload()

    fun name(value: String) = change { it.copy(name = value) }

    fun bio(value: String) = change { it.copy(bio = value) }

    fun language(value: String) = change { it.copy(language = value) }

    fun public(value: Boolean) = change { it.copy(public = value) }

    fun save() {
        val edit = currentEdit.value ?: return
        viewModelScope.launch {
            val change = ProfileChangeDto(
                name = edit.name.trim(),
                bio = edit.bio.trim().takeIf { it.isNotEmpty() },
                locale = edit.language,
                public = edit.public
            )
            when (val result = repository.updateProfile(change)) {
                is ApiResult.Success -> {
                    currentEdit.value = null
                    currentMessage.value = ProfileMessage.Saved
                    auth.rename(result.value.name)
                }

                is ApiResult.Failure -> currentMessage.value = messageOf(result.error)
            }
        }
    }

    fun uploadAvatar(upload: Upload) {
        viewModelScope.launch {
            if (repository.uploadAvatar(upload) is ApiResult.Failure) {
                currentMessage.value = ProfileMessage.AvatarRejected
            }
        }
    }

    fun avatarRejected() {
        currentMessage.value = ProfileMessage.AvatarRejected
    }

    fun dismissMessage() {
        currentMessage.value = null
    }

    private fun change(transform: (ProfileEdit) -> ProfileEdit) {
        val base = currentEdit.value ?: (state.value as? Loadable.Loaded)?.value ?: return
        currentEdit.update { transform(base) }
    }

    private fun messageOf(error: ApiError): ProfileMessage {
        if (error is ApiError.Offline || error is ApiError.Timeout) return ProfileMessage.Offline
        val http = error as? ApiError.Http ?: return ProfileMessage.Failed
        val fields = http.message.orEmpty()
        return when {
            fields.contains(NAME_REQUIRED) -> ProfileMessage.NameRequired
            fields.contains(NAME_TOO_LONG) -> ProfileMessage.NameTooLong
            fields.contains(LOCALE_NOT_ENABLED) -> ProfileMessage.LanguageNotAllowed
            else -> ProfileMessage.Failed
        }
    }

    private fun Profile.toEdit() = ProfileEdit(name, bio.orEmpty(), language, public, avatarUrl)

    private companion object {
        const val NAME_REQUIRED = "name_required"
        const val NAME_TOO_LONG = "name_too_long"
        const val LOCALE_NOT_ENABLED = "locale_not_enabled"
    }
}
