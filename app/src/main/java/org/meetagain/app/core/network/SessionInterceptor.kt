package org.meetagain.app.core.network

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Signs API calls with the member's access token, and ends the session the moment the server refuses it.
 *
 * Only calls to the app's own API are signed. A refused token is never retried: failed attempts count against the
 * server's rate limiter, which would lock this address out of the API altogether.
 *
 * Two answers end a session, and they are told apart by the error *code*, never by the status alone:
 * a **401** means the token is gone, and a **403 `insufficient_scope`** means it was issued before a section the
 * app now calls, so signing in again is the only way to widen it. A **403 `forbidden`** is left untouched - that
 * one is a screen's own sentence about another member, not the end of the session.
 */
class SessionInterceptor(
    private val token: () -> String?,
    private val json: Json,
    private val onRefused: (SessionRefusal) -> Unit
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = token().takeIf { request.url.encodedPath.startsWith(API_PATH) }
            ?: return chain.proceed(request)
        val response = chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
        refusalOf(response)?.let(onRefused)
        return response
    }

    /** The body is only peeked at, so the caller still reads the answer it was given. */
    private fun refusalOf(response: Response): SessionRefusal? = when {
        response.code == UNAUTHORIZED -> SessionRefusal.TokenRefused
        response.code != FORBIDDEN -> null
        errorCodeOf(response) == INSUFFICIENT_SCOPE -> SessionRefusal.SectionMissing
        else -> null
    }

    private fun errorCodeOf(response: Response): String? {
        if (response.header("Content-Type")?.contains("json") != true) return null
        val body = runCatching { response.peekBody(MAX_ERROR_BODY).string() }.getOrNull() ?: return null
        return runCatching { json.decodeFromString(ErrorBody.serializer(), body).error }.getOrNull()
    }

    private companion object {
        const val API_PATH = "/api/"
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val INSUFFICIENT_SCOPE = "insufficient_scope"

        /** An error body is a few dozen bytes; anything larger is not one. */
        const val MAX_ERROR_BODY = 4096L
    }
}

/** Why the server would not take the token, which is what the sign-in screen says when the app sends a member back. */
enum class SessionRefusal {
    /** The token itself is gone: revoked, or from an account that no longer signs in. */
    TokenRefused,

    /** The token was issued before a part of the API the app now uses; a new one carries it. */
    SectionMissing
}
