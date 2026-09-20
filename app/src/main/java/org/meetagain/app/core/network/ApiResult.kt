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
     * The server answered with a non-2xx status. [code] is the body's `error` field, a machine code the screens
     * turn into their own sentence; [message] is the server's English text, which is never shown.
     * [retryAfter] is the seconds the server asks the app to wait, where it says so.
     */
    data class Http(
        val status: Int,
        val code: String? = null,
        val message: String? = null,
        val retryAfter: Int? = null
    ) : ApiError

    /** A 2xx answer whose body does not match the expected shape. */
    data object Malformed : ApiError
}

/** The server's machine code for a refusal, for the screens that word each one differently. */
val ApiError.code: String? get() = (this as? ApiError.Http)?.code

fun ApiError.isCode(code: String) = this.code == code
