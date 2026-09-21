package org.meetagain.app.core.auth

import org.meetagain.app.core.network.SessionRefusal

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
        const val COMMUNITY_READ = "community:read"
        const val COMMUNITY_WRITE = "community:write"
    }
}

/** Whether anyone is signed in. [Unknown] lasts until the stored session has been read, once per start. */
sealed interface SessionState {
    data object Unknown : SessionState

    /** [refusal] is set when the server ended the session rather than the member, which the sign-in screen says. */
    data class SignedOut(val refusal: SessionRefusal? = null) : SessionState

    data class SignedIn(val session: Session) : SessionState

    /** Signed in, but the app lock is on and the token stays sealed until the member's fingerprint or PIN opens it. */
    data class Locked(val memberId: Int, val name: String) : SessionState
}

val SessionState.member: Session? get() = (this as? SessionState.SignedIn)?.session
