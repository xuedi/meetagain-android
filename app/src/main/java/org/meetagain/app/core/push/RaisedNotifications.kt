package org.meetagain.app.core.push

import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.meetagain.app.core.cache.CachedAnswer
import org.meetagain.app.core.cache.CachedAnswerDao

/**
 * What this phone has already said out loud, so a push and the timer firing a minute apart do not announce the same
 * cancellation twice.
 *
 * It lives in the stored-answers table under the member's own prefix, which is what gets wiped when they sign out -
 * the keys name their meetings, so they must not outlive the session.
 */
class RaisedNotifications(
    private val answers: CachedAnswerDao,
    private val json: Json,
    private val owner: () -> String,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun read(): Set<String> = answers.observe(key(), LANGUAGE).first()?.let { stored ->
        runCatching { json.decodeFromString(SERIALIZER, stored.body).toSet() }.getOrNull()
    }.orEmpty()

    /** Keeps the newest [LIMIT] keys, so a long-running install does not grow this without end. */
    suspend fun add(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val kept = (read() + keys).toList().takeLast(LIMIT)
        val now = clock.instant()
        answers.put(CachedAnswer(key(), LANGUAGE, json.encodeToString(SERIALIZER, kept), now, now))
    }

    private fun key() = owner() + KEY

    private companion object {
        const val KEY = "push/raised"

        /** Not language-dependent: these are identities, not text. */
        const val LANGUAGE = ""
        const val LIMIT = 200
        val SERIALIZER = ListSerializer(String.serializer())
    }
}
