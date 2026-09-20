package org.meetagain.app.core.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
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

    /** The token of a sign-in still in flight: the call that asks who it belongs to already needs it. */
    @Volatile
    private var pending: String? = null

    val state: StateFlow<SessionState> = current.asStateFlow()

    /** The token for the request interceptor, which cannot wait for a coroutine. */
    val token: String? get() = pending ?: current.value.member?.token

    init {
        scope.launch { current.value = store.read()?.let(SessionState::SignedIn) ?: SessionState.SignedOut }
    }

    /**
     * Signing in runs in the app's own scope, not the screen's: the session becomes true only once it is stored, and
     * by then the sign-in screen is on its way out, which would otherwise cancel the work halfway.
     */
    suspend fun signIn(email: String, password: String): ApiResult<Unit> =
        scope.async { signIn(email.trim(), password, deviceName()) }.await()

    private suspend fun signIn(email: String, password: String, deviceName: String): ApiResult<Unit> {
        val answer = when (val result = api.login(email, password, deviceName)) {
            is ApiResult.Failure -> return ApiResult.Failure(result.error)
            is ApiResult.Success -> result.value
        }
        val session = Session(memberId = 0, name = "", token = answer.token, scopes = answer.scopes.toSet())
        pending = session.token
        try {
            val me = when (val result = api.me()) {
                is ApiResult.Failure -> return ApiResult.Failure(result.error)
                is ApiResult.Success -> result.value
            }
            val member = session.copy(memberId = me.id, name = me.name)
            store.write(member)
            current.value = SessionState.SignedIn(member)
            return ApiResult.Success(Unit)
        } finally {
            pending = null
        }
    }

    /**
     * Best effort: the server is told when it can be reached, and the session goes either way. Like signing in, this
     * runs in the app's own scope, because the screen that asked for it is gone as soon as the session is.
     */
    suspend fun signOut() {
        scope.async {
            val member = current.value.member
            if (member != null) api.logout()
            wipe(member?.memberId)
        }.await()
    }

    /** The server refused the token: it is gone, and so is the session. */
    fun onInvalidToken() {
        val member = current.value.member ?: return
        scope.launch { wipe(member.memberId) }
    }

    /** The member's own name, as the profile screen saved it. */
    suspend fun rename(name: String) {
        val member = current.value.member ?: return
        store.writeName(name)
        current.value = SessionState.SignedIn(member.copy(name = name))
    }

    /** Everything goes before the session does, so a screen that leaves cannot leave the member's content behind. */
    private suspend fun wipe(memberId: Int?) {
        pending = null
        store.clear()
        if (memberId != null) forget(memberId)
        current.value = SessionState.SignedOut
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
