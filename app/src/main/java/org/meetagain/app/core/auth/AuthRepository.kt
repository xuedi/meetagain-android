package org.meetagain.app.core.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult

/**
 * Who the app is signed in as. The session is read from disk once at start, kept in memory for the rest of the run,
 * and wiped whenever the member signs out or the server refuses the token.
 *
 * The password is only ever a parameter of [signIn]: it goes to the server and is never stored or logged.
 */
class AuthRepository(
    private val api: ApiClient,
    private val store: SessionStore,
    private val deviceName: () -> String,
    private val scope: CoroutineScope,
    /** Everything kept for the member that just left, dropped as they go. */
    private val forget: suspend (memberId: Int) -> Unit = {}
) {
    private val current = MutableStateFlow<SessionState>(SessionState.Unknown)

    val state: StateFlow<SessionState> = current.asStateFlow()

    /** The token for the request interceptor, which cannot wait for a coroutine. */
    val token: String? get() = current.value.member?.token

    init {
        scope.launch { current.value = store.read()?.let(SessionState::SignedIn) ?: SessionState.SignedOut }
    }

    suspend fun signIn(email: String, password: String): ApiResult<Unit> {
        val result = api.login(email.trim(), password, deviceName())
        val answer = when (result) {
            is ApiResult.Failure -> return ApiResult.Failure(result.error)
            is ApiResult.Success -> result.value
        }
        // The token is needed for the call that says who it belongs to, so it is held before it is stored.
        val session = Session(memberId = 0, name = "", token = answer.token, scopes = answer.scopes.toSet())
        current.value = SessionState.SignedIn(session)
        val me = api.me()
        val member = when (me) {
            is ApiResult.Failure -> {
                current.value = SessionState.SignedOut
                return ApiResult.Failure(me.error)
            }

            is ApiResult.Success -> session.copy(memberId = me.value.id, name = me.value.name)
        }
        store.write(member)
        current.value = SessionState.SignedIn(member)
        return ApiResult.Success(Unit)
    }

    /** Best effort: the server is told when it can be reached, and the session goes either way. */
    suspend fun signOut() {
        val member = current.value.member
        if (member != null) api.logout()
        wipe(member?.memberId)
    }

    /** The server refused the token: it is gone, and so is the session. */
    fun onInvalidToken() {
        val member = current.value.member ?: return
        current.value = SessionState.SignedOut
        scope.launch { wipe(member.memberId) }
    }

    /** The member's own name, as the profile screen saved it. */
    suspend fun rename(name: String) {
        val member = current.value.member ?: return
        store.writeName(name)
        current.value = SessionState.SignedIn(member.copy(name = name))
    }

    private suspend fun wipe(memberId: Int?) {
        current.value = SessionState.SignedOut
        store.clear()
        if (memberId != null) forget(memberId)
    }
}

/** The codes `POST auth/login` answers with, each one a different sentence on the sign-in screen. */
object LoginError {
    const val INVALID_CREDENTIALS = "invalid_credentials"
    const val ACCOUNT_BLOCKED = "account_blocked"
    const val EMAIL_NOT_VERIFIED = "email_not_verified"
    const val PENDING_APPROVAL = "pending_approval"
    const val LOGIN_RESTRICTED = "login_restricted"
    const val TOO_MANY_ATTEMPTS = "too_many_attempts"
    const val INVALID_DEVICE_NAME = "invalid_device_name"
}

/** The server refused the token itself; the app has already signed out by the time a screen sees this. */
val ApiError.isInvalidToken: Boolean get() = this is ApiError.Http && status == 401

val ApiError.isInsufficientScope: Boolean get() = this is ApiError.Http && code == "insufficient_scope"
