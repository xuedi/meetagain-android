package org.meetagain.app.feature.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meetagain.app.core.auth.AuthRepository
import org.meetagain.app.core.auth.LoginError
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult

/**
 * Why a sign-in did not happen. Each one is a different sentence, and some send the member to the website, which is
 * where an account is fixed.
 */
sealed interface SignInProblem {
    /** The member can try again as they are. */
    data object WrongCredentials : SignInProblem

    data object Blocked : SignInProblem

    data object EmailNotVerified : SignInProblem

    data object PendingApproval : SignInProblem

    /** The website wants a check the app cannot show; signing in there once clears it. */
    data object SignInOnTheWebsite : SignInProblem

    data class TooManyAttempts(val minutes: Int) : SignInProblem

    /** The server would not take the name this installation gives itself. */
    data object DeviceName : SignInProblem

    /** Anything that is not about the account: no connection, a timeout, a server error. */
    data class Connection(val error: ApiError) : SignInProblem
}

data class SignInState(
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val problem: SignInProblem? = null
) {
    val canSubmit: Boolean get() = !busy && email.isNotBlank() && password.isNotEmpty()
}

class SignInViewModel(private val auth: AuthRepository) : ViewModel() {
    private val current = MutableStateFlow(SignInState())

    val state: StateFlow<SignInState> = current.asStateFlow()

    fun email(value: String) = current.update { it.copy(email = value, problem = null) }

    fun password(value: String) = current.update { it.copy(password = value, problem = null) }

    fun submit() {
        if (!current.value.canSubmit) return
        current.update { it.copy(busy = true, problem = null) }
        viewModelScope.launch {
            val state = current.value
            when (val result = auth.signIn(state.email, state.password)) {
                // The password is dropped whatever happens: a successful sign-in leaves this screen, and a failed
                // one starts from the address alone.
                is ApiResult.Success -> current.update { SignInState(email = it.email) }

                is ApiResult.Failure ->
                    current.update { it.copy(busy = false, password = "", problem = problemOf(result.error)) }
            }
        }
    }

    private fun problemOf(error: ApiError): SignInProblem {
        val http = error as? ApiError.Http ?: return SignInProblem.Connection(error)
        return when (http.code) {
            LoginError.INVALID_CREDENTIALS -> SignInProblem.WrongCredentials
            LoginError.ACCOUNT_BLOCKED -> SignInProblem.Blocked
            LoginError.EMAIL_NOT_VERIFIED -> SignInProblem.EmailNotVerified
            LoginError.PENDING_APPROVAL -> SignInProblem.PendingApproval
            LoginError.LOGIN_RESTRICTED -> SignInProblem.SignInOnTheWebsite
            LoginError.TOO_MANY_ATTEMPTS -> SignInProblem.TooManyAttempts(minutesOf(http.retryAfter))
            LoginError.INVALID_DEVICE_NAME -> SignInProblem.DeviceName
            else -> SignInProblem.Connection(error)
        }
    }

    /** Rounded up, so the member never comes back a few seconds too early. */
    private fun minutesOf(seconds: Int?) = ((seconds ?: DEFAULT_WAIT) + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE

    private companion object {
        const val SECONDS_PER_MINUTE = 60
        const val DEFAULT_WAIT = 900
    }
}
