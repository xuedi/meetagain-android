package org.meetagain.app.core.network

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

sealed interface ApiError {
    /** No connection to the server: no network, DNS failure, connection refused or reset. */
    data object Offline : ApiError

    data object Timeout : ApiError

    /**
     * The server answered with a non-2xx status. [code] is the body's `error` field when the body is JSON;
     * only `invalid_token` is a stable identifier today, the rest are English messages.
     */
    data class Http(val status: Int, val code: String? = null, val message: String? = null) : ApiError

    /** A 2xx answer whose body does not match the expected shape. */
    data object Malformed : ApiError
}
