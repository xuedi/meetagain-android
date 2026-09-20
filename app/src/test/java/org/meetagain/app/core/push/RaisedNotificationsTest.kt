package org.meetagain.app.core.push

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meetagain.app.testing.MemoryAnswers

/** What the phone has already said, so a ping and the timer do not repeat each other. */
class RaisedNotificationsTest {
    private val answers = MemoryAnswers()
    private val clock = Clock.fixed(Instant.parse("2026-09-20T08:00:00Z"), ZoneOffset.UTC)

    private fun store(owner: String = "m4/") =
        RaisedNotifications(answers, Json { ignoreUnknownKeys = true }, { owner }, clock)

    @Test
    fun `nothing is remembered to begin with`() = runTest {
        assertTrue(store().read().isEmpty())
    }

    @Test
    fun `what was said is remembered`() = runTest {
        val store = store()
        store.add(listOf("event-6-canceled"))
        assertEquals(setOf("event-6-canceled"), store.read())
    }

    @Test
    fun `adding nothing changes nothing`() = runTest {
        val store = store()
        store.add(emptyList())
        assertTrue(store.read().isEmpty())
    }

    /** The keys name the member's own meetings, so another member must never see them. */
    @Test
    fun `each member remembers their own`() = runTest {
        store("m4/").add(listOf("event-6-canceled"))
        assertTrue(store("m9/").read().isEmpty())
    }

    /** Signing out wipes the member's prefix, and this goes with it. */
    @Test
    fun `signing out forgets what was said`() = runTest {
        val store = store()
        store.add(listOf("event-6-canceled"))
        answers.deleteWithPrefix("m4/")
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun `it does not grow without end`() = runTest {
        val store = store()
        store.add((1..250).map { "key-$it" })
        val kept = store.read()
        assertEquals(200, kept.size)
        assertTrue(kept.contains("key-250"))
        assertFalse(kept.contains("key-1"))
    }
}
