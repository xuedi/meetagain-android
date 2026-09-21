package org.meetagain.app.core.push

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meetagain.app.R
import org.meetagain.app.core.data.QuietHours
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.testing.MovableClock
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.serve
import org.meetagain.app.testing.testSignalStore
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Push while the app lock is on: one notification that says something is new, and never what. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LockedPushWorkTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val server = MockWebServer()
    private val store = testSignalStore(context)
    private val clock = MovableClock()
    private val posted = mutableListOf<Raised>()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private suspend fun work(vararg answers: String): LockedPushWork {
        server.serve(mapOf("/api/v1/signal" to answers.map { json(fixture(it)) }))
        store.writeToken(SignalToken("mapat_signal", clock.instant().plus(Duration.ofDays(90))))
        val json = Json { ignoreUnknownKeys = true }
        val api = ApiClient(server.url("/").toString(), OkHttpClient(), json, { "en" })
        val signal = NotificationSignal(api, api, store, clock)
        return LockedPushWork(context, signal, store, FakeNotifier(posted), clock)
    }

    @Test
    fun `a ping says something is new, without a word about what`() = runTest {
        work("signal.json").run(fromTimer = false)
        val raised = posted.single()
        assertNull(raised.category)
        assertNull(raised.webUrl)
        assertEquals(context.getString(R.string.push_something_new), raised.text)
    }

    @Test
    fun `a ping still speaks when there is no signal token`() = runTest {
        val work = work("signal.json")
        store.dropToken()
        work.run(fromTimer = false)
        assertEquals(1, posted.size)
    }

    @Test
    fun `the timer stays quiet while nothing changed`() = runTest {
        store.writeLastState("4gL7mIdjrDaeFVD3eeqsAQ")
        work("signal.json").run(fromTimer = true)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `the timer speaks once the value moved`() = runTest {
        store.writeLastState("4gL7mIdjrDaeFVD3eeqsAQ")
        work("signal-changed.json").run(fromTimer = true)
        assertEquals(1, posted.size)
    }

    /** A ping and the timer a minute apart must not say the same thing twice. */
    @Test
    fun `a ping moves the mark, so the timer does not repeat it`() = runTest {
        store.writeLastState("4gL7mIdjrDaeFVD3eeqsAQ")
        val work = work("signal-changed.json", "signal-changed.json")
        work.run(fromTimer = false)
        work.run(fromTimer = true)
        assertEquals(1, posted.size)
    }

    @Test
    fun `the timer keeps the member's quiet hours`() = runTest {
        store.writeLastState("4gL7mIdjrDaeFVD3eeqsAQ")
        store.writeSettings(PushSnapshot(wanted = true, quietHours = QuietHours(true, "09:00", "11:00", "UTC", true)))
        work("signal-changed.json").run(fromTimer = true)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `the timer is quiet when the member wants no push`() = runTest {
        store.writeLastState("4gL7mIdjrDaeFVD3eeqsAQ")
        store.writeSettings(PushSnapshot(wanted = false, quietHours = null))
        work("signal-changed.json").run(fromTimer = true)
        assertTrue(posted.isEmpty())
    }

    private class FakeNotifier(private val posted: MutableList<Raised>) : Notifier {
        override fun canPost() = true

        override fun post(raised: Raised) {
            posted += raised
        }
    }
}
