package org.meetagain.app.core.network

import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun client(http: OkHttpClient = OkHttpClient()) =
        ApiClient(server.url("/").toString(), http, json, languageTag = { "de-DE" })

    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/api/$name")).readText()

    private fun respond(status: Int, name: String, contentType: String = "application/json") {
        server.enqueue(
            MockResponse.Builder().code(status).setHeader("Content-Type", contentType).body(fixture(name)).build()
        )
    }

    @Test
    fun `status is parsed`() = runTest {
        respond(200, "status.json")
        assertEquals(ApiResult.Success(Status("OK")), client().status())
    }

    @Test
    fun `requests ask for json in the app language`() = runTest {
        respond(200, "status.json")
        client().status()
        val request = server.takeRequest()
        assertEquals("/api/status", request.url.encodedPath)
        assertEquals("application/json", request.headers["Accept"])
        assertEquals("de-DE", request.headers["Accept-Language"])
    }

    @Test
    fun `events are asked for from a time, with a limit and optionally a group`() = runTest {
        respond(200, "events.json")
        respond(200, "events.json")
        val from = OffsetDateTime.of(2026, 9, 19, 10, 30, 15, 123_000_000, ZoneOffset.ofHours(2))
        val all = client().events(from, limit = 100)
        client().events(from, limit = 100, group = "my-community")

        val events = (all as ApiResult.Success).value
        assertEquals(28, events.total)
        assertEquals(117, events.items.first().id)
        assertEquals("2026-09-22T19:00:00+02:00", events.items.first().start)
        val plain = server.takeRequest().url
        assertEquals("/api/v1/events", plain.encodedPath)
        assertEquals("2026-09-19T10:30:15+02:00", plain.queryParameter("from"))
        assertEquals("100", plain.queryParameter("limit"))
        assertEquals(null, plain.queryParameter("group"))
        assertEquals("my-community", server.takeRequest().url.queryParameter("group"))
    }

    @Test
    fun `event detail is parsed`() = runTest {
        respond(200, "event-detail.json")
        val event = (client().event(117) as ApiResult.Success).value
        assertEquals("/api/v1/events/117", server.takeRequest().url.encodedPath)
        assertEquals("Travolta", event.location?.name)
        assertEquals("10999", event.location?.postcode)
    }

    @Test
    fun `groups are parsed`() = runTest {
        respond(200, "groups.json")
        val groups = (client().groups() as ApiResult.Success).value
        assertEquals("/api/v1/groups", server.takeRequest().url.encodedPath)
        assertEquals(4, groups.items.size)
        assertEquals("public", groups.items.first().visibility)
    }

    @Test
    fun `group detail is parsed and the slug is one path segment`() = runTest {
        respond(200, "group-detail.json")
        respond(404, "error-not-found.json")
        val group = (client().group("my-community") as ApiResult.Success).value
        assertEquals("/api/v1/groups/my-community", server.takeRequest().url.encodedPath)
        assertEquals(46, group.memberCount)
        assertEquals(listOf("de", "en", "zh"), group.languages)
        assertEquals(ApiResult.Failure(ApiError.Http(404, "not_found")), client().group("a/b"))
        assertEquals("/api/v1/groups/a%2Fb", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `unknown fields are ignored`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"status":"OK","uptime":3}""").build())
        assertEquals(ApiResult.Success(Status("OK")), client().status())
    }

    @Test
    fun `not found error`() = runTest {
        respond(404, "error-not-found.json")
        assertEquals(ApiResult.Failure(ApiError.Http(404, "not_found")), client().status())
    }

    @Test
    fun `token error keeps code and description`() = runTest {
        respond(401, "error-invalid-token.json")
        assertEquals(
            ApiResult.Failure(ApiError.Http(401, "invalid_token", "The access token provided is invalid.")),
            client().status()
        )
    }

    @Test
    fun `validation error joins the field errors`() = runTest {
        respond(400, "error-validation.json")
        assertEquals(
            ApiResult.Failure(
                ApiError.Http(
                    400,
                    "Validation failed",
                    "title: This value should not be blank.\ncontent: This value is too long."
                )
            ),
            client().status()
        )
    }

    @Test
    fun `html error page gives the status only`() = runTest {
        respond(404, "error-html.html", contentType = "text/html; charset=UTF-8")
        assertEquals(ApiResult.Failure(ApiError.Http(404)), client().status())
    }

    @Test
    fun `wrong shape on success is malformed`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"state":"OK"}""").build())
        assertEquals(ApiResult.Failure(ApiError.Malformed), client().status())
    }

    @Test
    fun `html on success is malformed`() = runTest {
        respond(200, "error-html.html", contentType = "text/html")
        assertEquals(ApiResult.Failure(ApiError.Malformed), client().status())
    }

    @Test
    fun `refused connection is offline`() = runTest {
        val url = server.url("/").toString()
        server.close()
        val result = ApiClient(url, OkHttpClient(), json, languageTag = { "en" }).status()
        assertEquals(ApiResult.Failure(ApiError.Offline), result)
    }

    @Test
    fun `slow answer is a timeout`() = runTest {
        server.enqueue(MockResponse.Builder().body(fixture("status.json")).headersDelay(2, TimeUnit.SECONDS).build())
        val http = OkHttpClient.Builder().readTimeout(Duration.ofMillis(200)).build()
        assertEquals(ApiResult.Failure(ApiError.Timeout), client(http).status())
    }
}
