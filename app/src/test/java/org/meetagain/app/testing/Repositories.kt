package org.meetagain.app.testing

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.Json
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.meetagain.app.AppContainer
import org.meetagain.app.AppInfo
import org.meetagain.app.core.auth.SessionStore
import org.meetagain.app.core.auth.TokenCipher
import org.meetagain.app.core.cache.CacheDatabase
import org.meetagain.app.core.cache.CachedAnswer
import org.meetagain.app.core.cache.CachedAnswerDao
import org.meetagain.app.core.data.AnswerCache
import org.meetagain.app.core.data.MemberRepository
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
    clock: Clock = testClock,
    memberId: Int? = null
): PublicRepository {
    val json = Json { ignoreUnknownKeys = true }
    val api = ApiClient(server.url("/").toString(), OkHttpClient(), json, language)
    val cache = AnswerCache(answers, json, language, owner = { AnswerCache.ownerOf(memberId) }, clock = clock)
    return PublicRepository(api, baseUrl, cache, clock)
}

/** A member repository on [server], with its answers under one member's own keys. */
fun memberRepository(
    server: MockWebServer,
    answers: CachedAnswerDao = MemoryAnswers(),
    memberId: Int = 4,
    language: () -> String = { "en" },
    baseUrl: String = "https://meetagain.org",
    clock: Clock = testClock
): MemberRepository {
    val json = Json { ignoreUnknownKeys = true }
    val api = ApiClient(server.url("/").toString(), OkHttpClient(), json, language)
    val cache = AnswerCache(answers, json, language, owner = { AnswerCache.ownerOf(memberId) }, clock = clock)
    return MemberRepository(api, baseUrl, cache, clock)
}

/** The app's container on [server], with an empty cache in memory and nobody signed in. */
fun testContainer(
    context: Context,
    server: MockWebServer,
    sessionStore: SessionStore = testSessionStore(context),
    scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher())
): AppContainer = AppContainer(
    AppInfo("0.1.0", testBuild = false, server.url("/").toString().trimEnd('/')),
    Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build(),
    sessionStore,
    deviceName = { "Test device (abcd)" },
    scope = scope,
    clock = testClock
)

/** A session store in a fresh file, whose token is scrambled rather than encrypted: no Keystore in a unit test. */
fun testSessionStore(context: Context, cipher: TokenCipher = ReversingCipher()): SessionStore = SessionStore(
    PreferenceDataStoreFactory.create { File.createTempFile("session", ".preferences_pb", context.cacheDir) },
    cipher
)

/** Stands in for the Keystore: enough to prove the token is not written as it is read. */
class ReversingCipher : TokenCipher {
    override fun encrypt(value: String) = value.reversed()

    override fun decrypt(stored: String) = stored.reversed()
}

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

    override suspend fun deleteWithPrefix(prefix: String) =
        rows.update { rows -> rows.filterKeys { !it.first.startsWith(prefix) } }
}
