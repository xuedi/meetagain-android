package org.meetagain.app.core.push

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meetagain.app.core.data.PushCategory
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.serve
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a ping turns into. The ping says nothing, so everything here comes from comparing the new answer with the
 * one the app already had.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PushWorkTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val server = MockWebServer()
    private val answers = MemoryAnswers()
    private val posted = mutableListOf<Raised>()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun work(
        settings: String = "notification-settings-push-on.json",
        events: List<MockResponse>,
        bell: List<MockResponse> = listOf(json(fixture("notifications-empty.json"))),
        clock: Clock = CLOCK
    ): PushWork {
        server.serve(
            mapOf(
                "/api/v1/me/events" to events,
                "/api/v1/me/notifications" to bell,
                "/api/v1/me/notification-settings" to listOf(json(fixture(settings)))
            )
        )
        val repository = memberRepository(server, answers, clock = clock)
        val raised = RaisedNotifications(answers, Json { ignoreUnknownKeys = true }, { "m4/" }, clock)
        return PushWork(context, repository, raised, FakeNotifier(posted), clock)
    }

    private suspend fun seed(events: String = "me-events.json") {
        val repository = memberRepository(server, answers, clock = CLOCK)
        repository.refreshNotificationSettings()
        repository.refreshMyEvents()
        repository.refreshNotifications()
    }

    @Test
    fun `a meeting called off is announced`() = runTest {
        val work = work(events = listOf(json(fixture("me-events.json")), json(fixture("me-events-changed.json"))))
        seed()
        work.run(fromTimer = false)
        val canceled = posted.single { it.key == "event-6-canceled" }
        assertEquals(PushCategory.EventChanges, canceled.category)
        assertTrue(canceled.text.contains("Weekly Go Study Group"))
    }

    @Test
    fun `a meeting that moved is announced`() = runTest {
        val work = work(events = listOf(json(fixture("me-events.json")), json(fixture("me-events-changed.json"))))
        seed()
        work.run(fromTimer = false)
        assertTrue(posted.any { it.key.startsWith("event-73-moved") })
    }

    /** A ping and the timer a minute apart must not say the same thing twice. */
    @Test
    fun `the same change is announced only once`() = runTest {
        val work = work(
            events = listOf(
                json(fixture("me-events.json")),
                json(fixture("me-events-changed.json")),
                json(fixture("me-events-changed.json"))
            )
        )
        seed()
        work.run(fromTimer = false)
        val first = posted.count { it.key == "event-6-canceled" }
        work.run(fromTimer = false)
        assertEquals(1, first)
        assertEquals(1, posted.count { it.key == "event-6-canceled" })
    }

    @Test
    fun `a category the member did not ask for is not announced`() = runTest {
        val work = work(
            settings = "notification-settings.json",
            events = listOf(json(fixture("me-events.json")), json(fixture("me-events-changed.json")))
        )
        seed()
        work.run(fromTimer = false)
        assertTrue(posted.isEmpty())
    }

    /** With the master switch off the member hears nothing, whatever the categories say. */
    @Test
    fun `the master switch off silences everything`() = runTest {
        val work = work(
            settings = "notification-settings-push-off-master.json",
            events = listOf(json(fixture("me-events.json")), json(fixture("me-events-changed.json")))
        )
        seed()
        work.run(fromTimer = false)
        assertTrue(posted.isEmpty())
    }

    /** The server gates pings on quiet hours; the timer has no such gate, so the app applies it. */
    @Test
    fun `the timer stays quiet at night but a cancellation gets through`() = runTest {
        val night = Clock.fixed(Instant.parse("2026-09-19T23:00:00Z"), ZoneId.of("Europe/Berlin"))
        val work = work(
            settings = "notification-settings-push-quiet.json",
            events = listOf(json(fixture("me-events.json")), json(fixture("me-events-changed.json"))),
            clock = night
        )
        seed()
        work.run(fromTimer = true)
        assertTrue(posted.any { it.key == "event-6-canceled" })
        assertTrue(posted.none { it.key.startsWith("event-73-moved") })
    }

    /** A push has already been through the server's own quiet-hours check, so the app does not re-apply it. */
    @Test
    fun `a push is not held back by quiet hours`() = runTest {
        val night = Clock.fixed(Instant.parse("2026-09-19T23:00:00Z"), ZoneId.of("Europe/Berlin"))
        val work = work(
            settings = "notification-settings-push-quiet.json",
            events = listOf(json(fixture("me-events.json")), json(fixture("me-events-changed.json"))),
            clock = night
        )
        seed()
        work.run(fromTimer = false)
        assertTrue(posted.any { it.key.startsWith("event-73-moved") })
    }

    /** A new bell entry is said in the server's own words, which are already in the member's language. */
    @Test
    fun `a new bell entry is announced as the server worded it`() = runTest {
        val work = work(
            events = listOf(json(fixture("me-events.json"))),
            bell = listOf(json(fixture("notifications-empty.json")), json(fixture("notifications.json")))
        )
        seed()
        work.run(fromTimer = false)
        val message = posted.single { it.text == "3 unread messages" }
        assertEquals(PushCategory.Messages, message.category)
        assertTrue(posted.any { it.text == "1 group invitation" && it.category == PushCategory.Announcements })
    }

    private class FakeNotifier(private val posted: MutableList<Raised>) : Notifier {
        override fun canPost() = true

        override fun post(raised: Raised) {
            posted += raised
        }
    }

    private companion object {
        /** Before every event in the fixtures, and outside quiet hours. */
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-19T08:00:00Z"), ZoneId.of("Europe/Berlin"))
    }
}
