package org.meetagain.app.core.auth

/** The member the app is signed in as, their access token and what the server lets that token do. */
data class Session(val memberId: Int, val name: String, val token: String, val scopes: Set<String>) {
    fun may(scope: String) = scope in scopes

    companion object {
        const val ME_READ = "me:read"
        const val ME_WRITE = "me:write"
        const val MEMBERSHIPS_READ = "memberships:read"
        const val MEMBERSHIPS_WRITE = "memberships:write"
        const val EVENTS_READ = "event-actions:read"
        const val EVENTS_WRITE = "event-actions:write"
    }
}

/** Whether anyone is signed in. [Unknown] lasts until the stored session has been read, once per start. */
sealed interface SessionState {
    data object Unknown : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val session: Session) : SessionState
}

val SessionState.member: Session? get() = (this as? SessionState.SignedIn)?.session
