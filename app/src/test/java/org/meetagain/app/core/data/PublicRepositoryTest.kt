package org.meetagain.app.core.data

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meetagain.app.core.cache.CachedAnswer
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.publicRepository

class PublicRepositoryTest {
    private val server = MockWebServer()
    private val now = Instant.parse("2026-09-19T08:00:00Z")
    private val answers = MemoryAnswers()
    private var language = "en"

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun repository(at: Instant = now) =
        publicRepository(server, answers, { language }, clock = Clock.fixed(at, ZoneOffset.UTC))

    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/api/$name")).readText()

    private fun respond(body: String, status: Int = 200) {
        server.enqueue(
            MockResponse.Builder().code(status).setHeader("Content-Type", "application/json").body(body).build()
        )
    }

    private suspend fun upcoming(group: String? = null) = repository().run {
        assertEquals(ApiResult.Success(Unit), refreshUpcomingEvents(group))
        checkNotNull(upcomingEvents(group).first()).value
    }

    @Test
    fun `upcoming events are asked for from now and parsed as instants`() = runTest {
        respond(fixture("events.json"))
        val upcoming = upcoming()

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
        assertFalse(upcoming().complete)
    }

    @Test
    fun `a list of one group carries its kinds and is complete`() = runTest {
        respond(
            """{"items":[${summary(2, "Picnic", type = 3)},${summary(3, "Dinner", type = 4)}],
            |"total":2,"limit":100,"offset":0}
            """.trimMargin()
        )
        val upcoming = upcoming(group = "my-community")
        assertEquals(listOf(2, 3), upcoming.events.map { it.id })
        assertEquals(listOf(EventKind.Outdoor, EventKind.Dinner), upcoming.events.map { it.kind })
        assertTrue(upcoming.complete)
        assertEquals("my-community", server.takeRequest().url.queryParameter("group"))
    }

    @Test
    fun `images on another host are dropped`() = runTest {
        respond(fixture("events.json"))
        val repository = publicRepository(server, answers, { language }, baseUrl = "http://localhost:8000")
        repository.refreshUpcomingEvents()
        assertNull(checkNotNull(repository.upcomingEvents().first()).value.events.first().imageUrl)
    }

    @Test
    fun `event detail keeps the location as a map query`() = runTest {
        respond(fixture("event-detail.json"))
        val repository = repository()
        repository.refreshEvent(117)
        val details = checkNotNull(repository.event(117).first()).value
        assertEquals("Travolta, Wiener Strasse. 14b, 10999 Berlin", details.location?.query)
        assertTrue(details.description!!.startsWith("Every Tuesday"))
    }

    @Test
    fun `an event the server does not have is forgotten`() = runTest {
        respond(fixture("event-detail.json"))
        val repository = repository()
        repository.refreshEvent(117)
        respond(fixture("error-not-found.json"), status = 404)
        assertEquals(ApiResult.Failure(ApiError.Http(404, "not_found")), repository.refreshEvent(117))
        assertNull(repository.event(117).first())
    }

    @Test
    fun `an unreadable start is malformed and not stored`() = runTest {
        respond(fixture("event-detail.json").replace("2026-09-22T19:00:00+02:00", "next Tuesday"))
        val repository = repository()
        assertEquals(ApiResult.Failure(ApiError.Malformed), repository.refreshEvent(117))
        assertNull(repository.event(117).first())
    }

    @Test
    fun `the groups are the ones the server lists`() = runTest {
        respond(fixture("groups.json"))
        val repository = repository()
        repository.refreshGroups()
        assertEquals(
            listOf(
                "another-country-bookshop",
                "berlin-activities",
                "my-community",
                "german-english-language-exchange-in-berlin"
            ),
            checkNotNull(repository.groups().first()).value.map { it.slug }
        )
    }

    @Test
    fun `a public group has its website and languages`() = runTest {
        respond(fixture("group-detail.json"))
        val repository = repository()
        repository.refreshGroup("my-community")
        val group = checkNotNull(repository.group("my-community").first()).value
        assertEquals("Dragon Descendants", group.group.name)
        assertEquals(46, group.memberCount)
        assertEquals("https://dragon-descendants.de/", group.websiteUrl)
        assertEquals(listOf("de", "en", "zh"), group.languages)
    }

    @Test
    fun `a group the member may see is shown whatever its visibility`() = runTest {
        // The server decides: a Hidden group of the signed-in member answers, and the app shows what it gets.
        respond(fixture("group-detail.json").replace("\"public\"", "\"hidden\""))
        val repository = repository()
        assertEquals(ApiResult.Success(Unit), repository.refreshGroup("my-community"))
        assertEquals("Dragon Descendants", checkNotNull(repository.group("my-community").first()).value.group.name)
    }

    @Test
    fun `a stored answer is kept with its time when the refresh fails`() = runTest {
        respond(fixture("event-detail.json"))
        repository().refreshEvent(117)

        respond("", status = 503)
        val later = repository(at = now + Duration.ofHours(3))
        assertEquals(ApiResult.Failure(ApiError.Http(503)), later.refreshEvent(117))
        val stored = checkNotNull(later.event(117).first())
        assertEquals(117, stored.value.event.id)
        assertEquals(now, stored.syncedAt)
    }

    @Test
    fun `a 404 forgets the stored answer`() = runTest {
        respond(fixture("group-detail.json"))
        val repository = repository()
        repository.refreshGroup("my-community")
        assertNotNull(repository.group("my-community").first())

        respond(fixture("error-not-found.json"), status = 404)
        repository.refreshGroup("my-community")
        assertNull(repository.group("my-community").first())
    }

    @Test
    fun `an answer stored in another language is not shown`() = runTest {
        respond(fixture("groups.json"))
        repository().refreshGroups()
        language = "de"
        assertNull(repository().groups().first())
    }

    @Test
    fun `ended events drop out of a stored list`() = runTest {
        respond(fixture("events.json"))
        repository().refreshUpcomingEvents()
        val afterFirst = repository(at = Instant.parse("2026-09-22T21:00:00Z"))
        val events = checkNotNull(afterFirst.upcomingEvents().first()).value.events
        assertFalse(events.any { it.id == 117 })
        assertTrue(events.isNotEmpty())
    }

    @Test
    fun `a body that no longer decodes counts as nothing stored`() = runTest {
        answers.put(CachedAnswer("groups", "en", """{"unexpected":true}""", now, now))
        assertNull(repository().groups().first())
    }

    @Test
    fun `answers not shown for 30 days are forgotten`() = runTest {
        answers.put(CachedAnswer("groups", "en", "{}", now, now))
        answers.put(CachedAnswer("event/1", "en", "{}", now, now + Duration.ofDays(20)))
        repository(at = now + Duration.ofDays(31)).forgetUnused()
        assertEquals(setOf("event/1" to "en"), answers.rows.value.keys)
    }

    @Test
    fun `showing a stored answer marks it as used`() = runTest {
        answers.put(CachedAnswer("groups", "en", """{"items":[],"total":0}""", now, now))
        val later = now + Duration.ofDays(5)
        repository(at = later).groups().first()
        assertEquals(later, answers.rows.value.getValue("groups" to "en").usedAt)
    }

    private fun summary(id: Int, title: String, type: Int = 2) =
        """{"id":$id,"title":"$title","start":"2026-09-22T19:00:00+02:00","type":$type,"rsvpCount":0,
        |"attendeeCount":0,"canceled":false,
        |"detailUrl":"https://meetagain.org/api/v1/events/$id","webUrl":"https://meetagain.org/en/event/$id"}"""
            .trimMargin()
}
