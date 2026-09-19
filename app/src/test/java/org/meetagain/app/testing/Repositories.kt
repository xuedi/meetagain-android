package org.meetagain.app.testing

import android.content.Context
import androidx.room.Room
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.meetagain.app.AppContainer
import org.meetagain.app.AppInfo
import org.meetagain.app.core.cache.CacheDatabase
import org.meetagain.app.core.cache.CachedAnswer
import org.meetagain.app.core.cache.CachedAnswerDao
import org.meetagain.app.core.data.PublicRepository
import org.meetagain.app.core.network.ApiClient

/** 19 September 2026, 08:00 UTC: before every event in the fixtures, so none of them has ended. */
val testClock: Clock = Clock.fixed(Instant.parse("2026-09-19T08:00:00Z"), ZoneOffset.UTC)

/**
 * A repository on [server] that counts meetagain.org, where the fixtures' images live, as its own host, with its
 * stored answers in [answers].
 */
fun publicRepository(
    server: MockWebServer,
    answers: CachedAnswerDao = MemoryAnswers(),
    language: () -> String = { "en" },
    baseUrl: String = "https://meetagain.org",
    clock: Clock = testClock
): PublicRepository {
    val json = Json { ignoreUnknownKeys = true }
    val api = ApiClient(server.url("/").toString(), OkHttpClient(), json, language)
    return PublicRepository(api, baseUrl, answers, json, language, clock)
}

/** The app's container on [server], with an empty cache in memory. */
fun testContainer(context: Context, server: MockWebServer): AppContainer = AppContainer(
    AppInfo("0.1.0", testBuild = false, server.url("/").toString().trimEnd('/')),
    Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build(),
    testClock
)

/** The stored answers in a map, for tests on the JVM without Room. */
class MemoryAnswers : CachedAnswerDao {
    val rows = MutableStateFlow<Map<Pair<String, String>, CachedAnswer>>(emptyMap())

    override fun observe(key: String, language: String): Flow<CachedAnswer?> = rows.map { it[key to language] }

    override suspend fun put(answer: CachedAnswer) = rows.update { it + ((answer.key to answer.language) to answer) }

    override suspend fun delete(key: String, language: String) = rows.update { it - (key to language) }

    override suspend fun markUsed(key: String, language: String, now: Instant) = rows.update { rows ->
        rows[key to language]?.let { rows + ((key to language) to it.copy(usedAt = now)) } ?: rows
    }

    override suspend fun deleteUnusedSince(before: Instant) =
        rows.update { rows -> rows.filterValues { !it.usedAt.isBefore(before) } }
}
