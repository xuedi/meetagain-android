package org.meetagain.app.core.network

import java.time.Duration
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
    fun `unknown fields are ignored`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"status":"OK","uptime":3}""").build())
        assertEquals(ApiResult.Success(Status("OK")), client().status())
    }

    @Test
    fun `not found error`() = runTest {
        respond(404, "error-not-found.json")
        assertEquals(ApiResult.Failure(ApiError.Http(404, "Not found")), client().status())
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
