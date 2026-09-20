package org.meetagain.app.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Signs API calls with the member's access token, and ends the session the moment the server refuses it.
 *
 * Only calls to the app's own API are signed. A refused token is never retried: failed attempts count against the
 * server's rate limiter, which would lock this address out of the API altogether.
 */
class SessionInterceptor(private val token: () -> String?, private val onInvalidToken: () -> Unit) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = token().takeIf { request.url.encodedPath.startsWith(API_PATH) }
            ?: return chain.proceed(request)
        val response = chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
        if (response.code == UNAUTHORIZED) onInvalidToken()
        return response
    }

    private companion object {
        const val API_PATH = "/api/"
        const val UNAUTHORIZED = 401
    }
}
