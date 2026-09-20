package org.meetagain.app.core.network

import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.serve

/**
 * The two answers that end a session, and the one 403 that does not: the trigger is the error code, never the
 * status alone.
 */
class SessionInterceptorTest {
    private val server = MockWebServer()
    private val refusals = mutableListOf<SessionRefusal>()

    private val http = OkHttpClient.Builder()
        .addInterceptor(SessionInterceptor({ "mapat_test" }, Json { ignoreUnknownKeys = true }) { refusals += it })
        .build()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun call(path: String) = http.newCall(Request.Builder().url(server.url(path)).build()).execute()

    @Test
    fun `a refused token ends the session`() {
        server.serve(mapOf(PATH to listOf(json(fixture("error-invalid-token.json"), 401))))
        call(PATH).use { assertEquals(401, it.code) }
        assertEquals(listOf(SessionRefusal.TokenRefused), refusals)
    }

    @Test
    fun `a token without the new section ends the session`() {
        server.serve(mapOf(PATH to listOf(json(fixture("error-insufficient-scope.json"), 403))))
        call(PATH).use { assertEquals(403, it.code) }
        assertEquals(listOf(SessionRefusal.SectionMissing), refusals)
    }

    /** The same status, the other code: the other member has blocked the caller, which is a screen's own sentence. */
    @Test
    fun `a forbidden answer leaves the session alone`() {
        server.serve(mapOf(PATH to listOf(json(fixture("error-forbidden.json"), 403))))
        call(PATH).use { assertEquals(403, it.code) }
        assertTrue(refusals.isEmpty())
    }

    @Test
    fun `a 403 that is not json leaves the session alone`() {
        val html = MockResponse.Builder()
            .code(403)
            .setHeader("Content-Type", "text/html")
            .body(fixture("error-html.html"))
            .build()
        server.serve(mapOf(PATH to listOf(html)))
        call(PATH).use { assertEquals(403, it.code) }
        assertTrue(refusals.isEmpty())
    }

    /** Peeking at the body must leave it for whoever asked: the caller still gets the server's own answer. */
    @Test
    fun `the body is still readable after the session ends`() {
        server.serve(mapOf(PATH to listOf(json(fixture("error-insufficient-scope.json"), 403))))
        val body = call(PATH).use { it.body.string() }
        assertEquals("insufficient_scope", Json.decodeFromString(ErrorBody.serializer(), body).error)
        assertEquals(listOf(SessionRefusal.SectionMissing), refusals)
    }

    @Test
    fun `a call outside the api is not signed and never ends a session`() {
        server.serve(mapOf("/images/a.webp" to listOf(json("{}", 403))))
        call("/images/a.webp").use { assertNull(it.request.header("Authorization")) }
        assertTrue(refusals.isEmpty())
    }

    @Test
    fun `an ordinary answer carries the token and changes nothing`() {
        server.serve(mapOf(PATH to listOf(json(fixture("status.json")))))
        call(PATH).use { assertEquals("Bearer mapat_test", it.request.header("Authorization")) }
        assertTrue(refusals.isEmpty())
    }

    private companion object {
        const val PATH = "/api/v1/community/conversations"
    }
}
