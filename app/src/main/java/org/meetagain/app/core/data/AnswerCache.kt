package org.meetagain.app.core.data

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.meetagain.app.core.cache.CachedAnswer
import org.meetagain.app.core.cache.CachedAnswerDao
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult

/**
 * The store behind every read: the last answer the server gave, in the member's language, shown at once while a
 * refresh goes out for a new one.
 *
 * Answers depend on who asks - a signed-in member sees their own groups and their own RSVP - so every key is written
 * under [owner], which is empty while nobody is signed in and the member's id once someone is. Signing out deletes
 * that whole prefix.
 */
class AnswerCache(
    private val answers: CachedAnswerDao,
    private val json: Json,
    private val language: () -> String,
    private val owner: () -> String = { "" },
    private val clock: Clock = Clock.systemUTC()
) {
    /** A stored body that no longer decodes, after an app update changed a DTO, counts as nothing stored. */
    fun <D, T : Any> observe(key: String, serializer: KSerializer<D>, map: (D) -> T?): Flow<Cached<T>?> {
        val language = language()
        val full = owner() + key
        return answers.observe(full, language)
            .map { row ->
                row?.let { stored ->
                    decode(stored.body, serializer)?.let(map)?.let { Cached(it, stored.syncedAt) }
                }
            }
            .distinctUntilChanged()
            .onStart { answers.markUsed(full, language, clock.instant()) }
    }

    /** [check] turns an answer the app must not show into an error: 404 forgets the stored answer, others keep it. */
    suspend fun <D> refresh(
        key: String,
        serializer: KSerializer<D>,
        check: (D) -> ApiError? = { null },
        fetch: suspend () -> ApiResult<D>
    ): ApiResult<Unit> {
        val language = language()
        val full = owner() + key
        val error = when (val result = fetch()) {
            is ApiResult.Failure -> result.error

            is ApiResult.Success -> check(result.value) ?: run {
                val now = clock.instant()
                answers.put(CachedAnswer(full, language, json.encodeToString(serializer, result.value), now, now))
                return ApiResult.Success(Unit)
            }
        }
        if (error.isNotFound) answers.delete(full, language)
        return ApiResult.Failure(error)
    }

    /** Forgets what has not been shown for [unused], so the cache holds only what the member still looks at. */
    suspend fun forgetUnused(unused: Duration = CACHE_RETENTION) = answers.deleteUnusedSince(clock.instant() - unused)

    /** Everything stored for one member, as they sign out. */
    suspend fun forgetMember(memberId: Int) = answers.deleteWithPrefix(ownerOf(memberId))

    private fun <D> decode(body: String, serializer: KSerializer<D>): D? = try {
        json.decodeFromString(serializer, body)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        private val CACHE_RETENTION: Duration = Duration.ofDays(30)

        /** The prefix every key of one member carries; nobody signed in owns the plain keys. */
        fun ownerOf(memberId: Int?) = if (memberId == null) "" else "m$memberId/"
    }
}

/** The last answer stored for a read, as the app's model, and when the server gave it. */
data class Cached<T>(val value: T, val syncedAt: Instant)

val ApiError.isNotFound: Boolean get() = this is ApiError.Http && status == 404

/** The names the app stores its answers under, one per read, shared by the repositories that refresh them. */
object Keys {
    const val GROUPS = "groups"
    const val MY_EVENTS = "me/events"
    const val MY_GROUPS = "me/groups"
    const val INVITATIONS = "me/invitations"
    const val ME = "me"

    fun events(group: String? = null) = if (group == null) "events" else "events?group=$group"

    fun event(id: Int) = "event/$id"

    fun group(slug: String) = "group/$slug"

    fun occurrences(id: Int) = "event/$id/occurrences"

    fun attendees(id: Int) = "event/$id/attendees"

    fun comments(id: Int) = "event/$id/comments"

    fun photos(id: Int) = "event/$id/images"
}
