package org.meetagain.app.core.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.crypto.Cipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.network.SessionRefusal

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
    private val forget: suspend (memberId: Int) -> Unit = {},
    /**
     * Anything that needs the token while it still works - taking this phone off the push register, which is the
     * only way the deletion is confirmed rather than assumed.
     */
    private val beforeSignOut: suspend () -> Unit = {},
    private val lockedCipher: LockedCipher = KeystoreLockedCipher(),
    /** What else moves with the app lock: the signal token, and what push keeps for the locked phone. */
    private val onLock: suspend (LockEvent) -> Unit = {},
    private val clock: Clock = Clock.systemUTC()
) {
    private val current = MutableStateFlow<SessionState>(SessionState.Unknown)

    private val lock = MutableStateFlow(false)

    /** When the app last went into the background, while the lock is on. */
    @Volatile
    private var leftAt: Instant? = null

    /** The token of a sign-in still in flight: the call that asks who it belongs to already needs it. */
    @Volatile
    private var pending: String? = null

    val state: StateFlow<SessionState> = current.asStateFlow()

    /** Whether the member turned the app lock on. */
    val lockOn: StateFlow<Boolean> = lock.asStateFlow()

    /** The token for the request interceptor, which cannot wait for a coroutine. */
    val token: String? get() = pending ?: current.value.member?.token

    init {
        scope.launch {
            current.value = when (val stored = store.read()) {
                is StoredSession.Open -> SessionState.SignedIn(stored.session)
                is StoredSession.Locked -> SessionState.Locked(stored.memberId, stored.name).also { lock.value = true }
                null -> SessionState.SignedOut()
            }
        }
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
            if (member != null) {
                runCatching { beforeSignOut() }
                api.logout()
            }
            wipe(member?.memberId)
        }.await()
    }

    /**
     * The server would not take the token. Either it is gone, or it predates a section the app now calls; both end
     * the session, and [refusal] is what the sign-in screen then says.
     */
    fun onRefusedToken(refusal: SessionRefusal) {
        val member = current.value.member ?: return
        scope.launch { wipe(member.memberId, refusal) }
    }

    /**
     * The cipher the unlock prompt opens the stored token with. Null when there is nothing to unlock, or when the key
     * is gone for good - then the session ends here, and the sign-in screen says why.
     */
    suspend fun unlockCipher(): Cipher? {
        val stored = store.read() as? StoredSession.Locked ?: return null
        return lockedCipher.forDecryption(stored.sealed) ?: run {
            scope.async { wipe(stored.memberId, SessionRefusal.LockReset) }.await()
            null
        }
    }

    /** [cipher] is the one the prompt handed back. Runs in the app's scope: the unlock screen leaves as it succeeds. */
    suspend fun unlock(cipher: Cipher): Boolean = scope.async {
        val stored = store.read() as? StoredSession.Locked ?: return@async false
        val token = lockedCipher.open(cipher, stored.sealed) ?: return@async false
        current.value = SessionState.SignedIn(Session(stored.memberId, stored.name, token, stored.scopes))
        runCatching { onLock(LockEvent.Unlocked) }
        true
    }.await()

    fun leftApp() {
        if (lock.value) leftAt = clock.instant()
    }

    /** Back after [RELOCK_AFTER] or more: the token leaves memory, and the member unlocks again. */
    fun cameBack() {
        val left = leftAt ?: return
        leftAt = null
        if (Duration.between(left, clock.instant()) >= RELOCK_AFTER) relock()
    }

    private fun relock() {
        val member = current.value.member ?: return
        if (!lock.value) return
        scope.launch {
            runCatching { onLock(LockEvent.Locking) }
            current.value = SessionState.Locked(member.memberId, member.name)
        }
    }

    /** The cipher the prompt seals the token with when the lock is turned on. Null when this phone cannot lock. */
    fun lockCipher(): Cipher? = runCatching { lockedCipher.forEncryption() }.getOrNull()

    suspend fun turnLockOn(cipher: Cipher): Boolean = scope.async {
        val member = current.value.member ?: return@async false
        val sealed = runCatching { lockedCipher.seal(cipher, member.token) }.getOrNull() ?: return@async false
        store.writeLocked(member, sealed)
        lock.value = true
        runCatching { onLock(LockEvent.TurnedOn) }
        true
    }.await()

    /** Turning the lock off asks for the fingerprint or PIN too: whoever holds the open phone is not enough. */
    suspend fun unlockCipherForTurningOff(): Cipher? {
        val stored = store.read() as? StoredSession.Locked ?: return null
        return lockedCipher.forDecryption(stored.sealed)
    }

    suspend fun turnLockOff(cipher: Cipher): Boolean = scope.async {
        val member = current.value.member ?: return@async false
        val stored = store.read() as? StoredSession.Locked ?: return@async false
        if (lockedCipher.open(cipher, stored.sealed) != member.token) return@async false
        store.write(member)
        lockedCipher.delete()
        lock.value = false
        runCatching { onLock(LockEvent.TurnedOff) }
        true
    }.await()

    /** From the unlock screen: this member signs in again with their password, and the sealed session goes. */
    suspend fun leaveLocked() {
        val locked = current.value as? SessionState.Locked ?: return
        scope.async { wipe(locked.memberId) }.await()
    }

    /** The member's own name, as the profile screen saved it. */
    suspend fun rename(name: String) {
        val member = current.value.member ?: return
        store.writeName(name)
        current.value = SessionState.SignedIn(member.copy(name = name))
    }

    /** Everything goes before the session does, so a screen that leaves cannot leave the member's content behind. */
    private suspend fun wipe(memberId: Int?, refusal: SessionRefusal? = null) {
        pending = null
        store.clear()
        if (lock.value) lockedCipher.delete()
        lock.value = false
        leftAt = null
        if (memberId != null) forget(memberId)
        current.value = SessionState.SignedOut(refusal)
    }

    private companion object {
        val RELOCK_AFTER: Duration = Duration.ofMinutes(5)
    }
}

/** What the app lock just did, for the parts of the app that move with it. */
enum class LockEvent {
    TurnedOn,
    TurnedOff,
    Unlocked,

    /** Before the token leaves memory, while the member's own cached answers can still be read. */
    Locking
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
