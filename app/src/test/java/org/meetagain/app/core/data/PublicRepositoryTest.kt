package org.meetagain.app.core.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult

class PublicRepositoryTest {
    private val server = MockWebServer()
    private val now = Instant.parse("2026-09-19T08:00:00Z")

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    /** Images in the fixtures are on meetagain.org, so that is the app's own host here. */
    private fun repository(baseUrl: String = "https://meetagain.org"): PublicRepository {
        val api = ApiClient(server.url("/").toString(), OkHttpClient(), Json { ignoreUnknownKeys = true }, { "en" })
        return PublicRepository(api, baseUrl, Clock.fixed(now, ZoneOffset.UTC))
    }

    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/api/$name")).readText()

    private fun respond(body: String, status: Int = 200) {
        server.enqueue(
            MockResponse.Builder().code(status).setHeader("Content-Type", "application/json").body(body).build()
        )
    }

    private fun <T> ApiResult<T>.value(): T = (this as ApiResult.Success).value

    @Test
    fun `upcoming events are asked for from now and parsed as instants`() = runTest {
        respond(fixture("events.json"))
        val upcoming = repository().upcomingEvents().value()

        val request = server.takeRequest().url
        assertEquals("2026-09-19T08:00:00Z", request.queryParameter("from"))
        assertEquals("100", request.queryParameter("limit"))
        val first = upcoming.events.first()
        assertEquals(Instant.parse("2026-09-22T17:00:00Z"), first.start)
        assertEquals(Instant.parse("2026-09-22T20:30:00Z"), first.end)
        assertEquals("https://meetagain.org/en/event/117", first.webUrl)
        assertNull(first.kind)
    }

    @Test
    fun `a list shorter than the total is not complete`() = runTest {
        respond(fixture("events.json"))
        assertFalse(repository().upcomingEvents().value().complete)
    }

    @Test
    fun `events without a title in the language are dropped`() = runTest {
        respond(
            """{"items":[${summary(1, "")},${summary(2, "Picnic", type = 3)},${summary(3, "Dinner", type = 4)}],
            |"total":3,"limit":100,"offset":0}
            """.trimMargin()
        )
        val upcoming = repository().upcomingEvents(group = "my-community").value()
        assertEquals(listOf(2, 3), upcoming.events.map { it.id })
        assertEquals(listOf(EventKind.Outdoor, EventKind.Dinner), upcoming.events.map { it.kind })
        assertTrue(upcoming.complete)
        assertEquals("my-community", server.takeRequest().url.queryParameter("group"))
    }

    @Test
    fun `images on another host are dropped`() = runTest {
        respond(fixture("events.json"))
        val event = repository(baseUrl = "http://localhost:8000").upcomingEvents().value().events.first()
        assertNull(event.imageUrl)
    }

    @Test
    fun `event detail keeps the location as a map query`() = runTest {
        respond(fixture("event-detail.json"))
        val details = repository().event(117).value()
        assertEquals("Travolta, Wiener Strasse. 14b, 10999 Berlin", details.location?.query)
        assertTrue(details.description!!.startsWith("Every Tuesday"))
    }

    @Test
    fun `an event without a title in the language is not found`() = runTest {
        respond(fixture("event-detail.json").replace("\"German English Language Exchange\"", "\"\""))
        assertEquals(ApiResult.Failure(ApiError.Http(404)), repository().event(117))
    }

    @Test
    fun `an unreadable start is malformed`() = runTest {
        respond(fixture("event-detail.json").replace("2026-09-22T19:00:00+02:00", "next Tuesday"))
        assertEquals(ApiResult.Failure(ApiError.Malformed), repository().event(117))
    }

    @Test
    fun `only public groups are listed`() = runTest {
        respond(fixture("groups.json"))
        val slugs = repository().groups().value().map { it.slug }
        assertEquals(
            listOf(
                "another-country-bookshop",
                "berlin-activities",
                "my-community",
                "german-english-language-exchange-in-berlin"
            ),
            slugs
        )
    }

    @Test
    fun `a public group has its website`() = runTest {
        respond(fixture("group-detail.json"))
        val group = repository().group("my-community").value()
        assertEquals("Dragon Descendants", group.group.name)
        assertEquals(45, group.memberCount)
        assertEquals("https://dragon-descendants.de/", group.websiteUrl)
    }

    @Test
    fun `a group that is not public is not found`() = runTest {
        respond(fixture("group-detail.json").replace("\"public\"", "\"private\""))
        assertEquals(ApiResult.Failure(ApiError.Http(404)), repository().group("my-community"))
    }

    private fun summary(id: Int, title: String, type: Int = 2) =
        """{"id":$id,"title":"$title","start":"2026-09-22T19:00:00+02:00","type":$type,"rsvpCount":0,
        |"detailUrl":"https://meetagain.org/api/v1/events/$id","webUrl":"https://meetagain.org/en/event/$id"}"""
            .trimMargin()
}
