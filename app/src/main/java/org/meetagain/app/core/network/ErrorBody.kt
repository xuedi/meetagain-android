package org.meetagain.app.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The union of the JSON error shapes the server sends: `{error}`, `{error, error_description}` and `{error, errors}`. */
@Serializable
internal data class ErrorBody(
    val error: String? = null,
    @SerialName("error_description") val description: String? = null,
    val errors: List<String> = emptyList()
)

internal fun parseHttpError(status: Int, contentType: String?, body: String, json: Json): ApiError.Http {
    if (contentType?.contains("json") != true) return ApiError.Http(status)
    val parsed = runCatching { json.decodeFromString(ErrorBody.serializer(), body) }.getOrNull()
        ?: return ApiError.Http(status)
    val message = parsed.description ?: parsed.errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
    return ApiError.Http(status, parsed.error, message)
}
