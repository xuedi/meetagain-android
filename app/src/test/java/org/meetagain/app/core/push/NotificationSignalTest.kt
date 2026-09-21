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
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.SessionInterceptor
import org.meetagain.app.core.network.SessionRefusal
import org.meetagain.app.testing.MovableClock
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.noContent
import org.meetagain.app.testing.serve
import org.meetagain.app.testing.testSignalStore
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The signal token and what it is allowed to touch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotificationSignalTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private val store = testSignalStore(context)
    private val clock = MovableClock()
    private val refusals = mutableListOf<SessionRefusal>()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun signal(): NotificationSignal {
        val base = server.url("/").toString()
        // The member's client, as the app builds it: every call signed with the member's own token.
        val session = OkHttpClient.Builder()
            .addInterceptor(SessionInterceptor({ MEMBER_TOKEN }, json) { refusals += it })
            .build()
        return NotificationSignal(
            ApiClient(base, session, json, { "en" }),
            ApiClient(base, OkHttpClient(), json, { "en" }),
            store,
            clock
        )
    }

    @Test
    fun `turning the lock on asks for a token with the member's own and sets the mark quietly`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/me/signal-token" to listOf(json(fixture("signal-token.json"), 201)),
                "/api/v1/signal" to listOf(json(fixture("signal.json")))
            )
        )
        signal().ensureToken()
        val mint = server.takeRequest()
        assertEquals("/api/v1/me/signal-token", mint.url.encodedPath)
        assertEquals("Bearer $MEMBER_TOKEN", mint.headers["Authorization"])
        assertEquals(SIGNAL_TOKEN, store.token()?.value)
        assertEquals("4gL7mIdjrDaeFVD3eeqsAQ", store.lastState())
    }

    @Test
    fun `the signal call carries the signal token and never the member's`() = runTest {
        server.serve(mapOf("/api/v1/signal" to listOf(json(fixture("signal.json")))))
        store.writeToken(SignalToken(SIGNAL_TOKEN, clock.instant().plus(Duration.ofDays(90))))
        signal().check()
        assertEquals("Bearer $SIGNAL_TOKEN", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `a new value is news and the same one is not`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/signal" to listOf(
                    json(fixture("signal.json")),
                    json(fixture("signal.json")),
                    json(fixture("signal-changed.json"))
                )
            )
        )
        store.writeToken(SignalToken(SIGNAL_TOKEN, clock.instant().plus(Duration.ofDays(90))))
        val signal = signal()
        assertEquals(SignalCheck.Same, signal.check())
        assertEquals(SignalCheck.Same, signal.check())
        assertEquals(SignalCheck.Changed, signal.check())
    }

    @Test
    fun `a refused signal token goes, and the session never hears of it`() = runTest {
        server.serve(mapOf("/api/v1/signal" to listOf(json(fixture("error-invalid-token.json"), 401))))
        store.writeToken(SignalToken(SIGNAL_TOKEN, clock.instant().plus(Duration.ofDays(90))))
        assertEquals(SignalCheck.Refused, signal().check())
        assertNull(store.token())
        assertTrue(refusals.isEmpty())
    }

    @Test
    fun `without a token there is nothing to ask`() = runTest {
        assertEquals(SignalCheck.NoToken, signal().check())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a token with more than a month left is kept`() = runTest {
        store.writeToken(SignalToken(SIGNAL_TOKEN, clock.instant().plus(Duration.ofDays(60))))
        signal().ensureToken()
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a token close to its end is replaced, and the mark carries on`() = runTest {
        server.serve(mapOf("/api/v1/me/signal-token" to listOf(json(fixture("signal-token.json"), 201))))
        store.writeToken(SignalToken("mapat_old", clock.instant().plus(Duration.ofDays(10))))
        store.writeLastState("earlier")
        signal().ensureToken()
        assertEquals(SIGNAL_TOKEN, store.token()?.value)
        assertEquals("earlier", store.lastState())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a server without the signal leaves the phone without a token`() = runTest {
        server.serve(emptyMap())
        signal().ensureToken()
        assertNull(store.token())
    }

    @Test
    fun `discarding revokes the token with itself and forgets it`() = runTest {
        server.serve(mapOf("/api/v1/auth/logout" to listOf(noContent())))
        store.writeToken(SignalToken(SIGNAL_TOKEN, clock.instant().plus(Duration.ofDays(90))))
        store.writeLastState("4gL7mIdjrDaeFVD3eeqsAQ")
        signal().discard()
        val logout = server.takeRequest()
        assertEquals("/api/v1/auth/logout", logout.url.encodedPath)
        assertEquals("Bearer $SIGNAL_TOKEN", logout.headers["Authorization"])
        assertNull(store.token())
        assertNull(store.lastState())
    }

    private companion object {
        const val MEMBER_TOKEN = "mapat_member"
        const val SIGNAL_TOKEN = "mapat_ssssssssssssssssssssssssssssssssssssssssss"
    }
}
