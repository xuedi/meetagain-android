package org.meetagain.app.core.cache

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CachedAnswerDaoTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Application>(),
        CacheDatabase::class.java
    ).build()
    private val answers = database.answers()
    private val t0 = Instant.parse("2026-09-19T08:00:00Z")

    @After
    fun close() = database.close()

    private fun answer(key: String, language: String = "en", body: String = "{}", usedAt: Instant = t0) =
        CachedAnswer(key, language, body, t0, usedAt)

    @Test
    fun `an answer is stored per key and language and replaced`() = runTest {
        answers.put(answer("groups", body = "old"))
        answers.put(answer("groups", language = "de", body = "alt"))
        answers.put(answer("groups", body = "new"))

        assertEquals("new", answers.observe("groups", "en").first()?.body)
        assertEquals("alt", answers.observe("groups", "de").first()?.body)
        assertEquals(t0, answers.observe("groups", "en").first()?.syncedAt)
    }

    @Test
    fun `an answer can be deleted`() = runTest {
        answers.put(answer("event/1"))
        answers.delete("event/1", "en")
        assertNull(answers.observe("event/1", "en").first())
    }

    @Test
    fun `answers unused since a time are deleted`() = runTest {
        answers.put(answer("old"))
        answers.put(answer("used", usedAt = t0.plusSeconds(60)))
        answers.put(answer("touched"))
        answers.markUsed("touched", "en", t0.plusSeconds(120))

        answers.deleteUnusedSince(t0.plusSeconds(30))

        assertNull(answers.observe("old", "en").first())
        assertEquals("used", answers.observe("used", "en").first()?.key)
        assertEquals(t0.plusSeconds(120), answers.observe("touched", "en").first()?.usedAt)
    }
}
