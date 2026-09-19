package org.meetagain.app.core.network

import java.io.IOException
import java.io.InterruptedIOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class ApiClient(
    baseUrl: String,
    private val http: OkHttpClient,
    private val json: Json,
    private val languageTag: () -> String,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val base = baseUrl.toHttpUrl()

    suspend fun status(): ApiResult<Status> = get(url("api/status"), Status.serializer())

    /** Upcoming events from [from] on, optionally of one group. */
    suspend fun events(from: OffsetDateTime, limit: Int, group: String? = null): ApiResult<EventListDto> {
        val url = url("api/v1/events").newBuilder()
            .addQueryParameter(
                "from",
                from.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            )
            .addQueryParameter("limit", limit.toString())
            .apply { if (group != null) addQueryParameter("group", group) }
            .build()
        return get(url, EventListDto.serializer())
    }

    suspend fun event(id: Int): ApiResult<EventDetailDto> =
        get(url("api/v1/events").newBuilder().addPathSegment(id.toString()).build(), EventDetailDto.serializer())

    suspend fun groups(): ApiResult<GroupListDto> = get(url("api/v1/groups"), GroupListDto.serializer())

    suspend fun group(slug: String): ApiResult<GroupDetailDto> =
        get(url("api/v1/groups").newBuilder().addPathSegment(slug).build(), GroupDetailDto.serializer())

    private fun url(path: String): HttpUrl = base.newBuilder().addPathSegments(path).build()

    private suspend fun <T> get(url: HttpUrl, deserializer: DeserializationStrategy<T>): ApiResult<T> =
        withContext(io) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Accept-Language", languageTag())
                .build()
            try {
                http.newCall(request).await().use { response -> response.toResult(deserializer) }
            } catch (_: InterruptedIOException) {
                ApiResult.Failure(ApiError.Timeout)
            } catch (_: IOException) {
                ApiResult.Failure(ApiError.Offline)
            }
        }

    private fun <T> Response.toResult(deserializer: DeserializationStrategy<T>): ApiResult<T> {
        val text = body.string()
        if (!isSuccessful) return ApiResult.Failure(parseHttpError(code, header("Content-Type"), text, json))
        return try {
            ApiResult.Success(json.decodeFromString(deserializer, text))
        } catch (_: SerializationException) {
            ApiResult.Failure(ApiError.Malformed)
        } catch (_: IllegalArgumentException) {
            ApiResult.Failure(ApiError.Malformed)
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(e))
            }
        }
    )
}
