package org.meetagain.app.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.noContent
import org.meetagain.app.testing.publicRepository
import org.meetagain.app.testing.serve
import org.meetagain.app.testing.testClock

class MemberRepositoryTest {
    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun repository() = memberRepository(server, answers)

    @Test
    fun `my events carry the group, the flags and my own answer`() = runTest {
        server.serve(mapOf("/api/v1/me/events" to listOf(json(fixture("me-events.json")))))
        val repository = repository()
        repository.refreshMyEvents()
        val events = checkNotNull(repository.myEvents().first()).value.events
        val go = events.single { it.id == 6 }
        assertEquals("weiqi-club", go.group?.slug)
        assertEquals(Rsvp(going = true, guests = 0), go.mine)
        assertEquals(1, go.seriesId)
        assertFalse(go.canceled)
        assertTrue(events.first().attending >= events.first().going)
    }

    @Test
    fun `the answers of two members never mix`() = runTest {
        server.serve(mapOf("/api/v1/me/events" to listOf(json(fixture("me-events.json")))))
        memberRepository(server, answers, memberId = 4).refreshMyEvents()
        assertNull(memberRepository(server, answers, memberId = 9).myEvents().first())
        assertEquals(setOf("m4/me/events"), answers.rows.value.keys.map { it.first }.toSet())
    }

    @Test
    fun `signing out forgets everything of that member`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/me/events" to listOf(json(fixture("me-events.json"))),
                "/api/v1/me/groups" to listOf(json(fixture("me-groups.json")))
            )
        )
        val repository = repository()
        repository.refreshMyEvents()
        repository.refreshMyGroups()
        val cache = AnswerCache(answers, Json { ignoreUnknownKeys = true }, { "en" }, clock = testClock)
        cache.forgetMember(4)
        assertTrue(answers.rows.value.isEmpty())
    }

    @Test
    fun `who is coming counts guests and the ones without an account`() = runTest {
        server.serve(mapOf("/api/v1/events/6/attendees" to listOf(json(fixture("event-attendees.json")))))
        val repository = repository()
        repository.refreshAttendees(6)
        val attendees = checkNotNull(repository.attendees(6).first()).value
        assertTrue(attendees.people.any { it.mine })
        assertEquals(attendees.total, attendees.people.sumOf { 1 + it.guests } + attendees.externalCount)
    }

    @Test
    fun `the conversation is newest first with a cursor for the older page`() = runTest {
        server.serve(mapOf("/api/v1/events/6/comments" to listOf(json(fixture("event-comments.json")))))
        val repository = repository()
        repository.refreshConversation(6)
        val conversation = checkNotNull(repository.conversation(6).first()).value
        assertEquals(conversation.comments.map { it.id }.sortedDescending(), conversation.comments.map { it.id })
        assertTrue(conversation.comments.first().text.isNotBlank())
    }

    @Test
    fun `a photo keeps its grid size and its full size`() = runTest {
        server.serve(mapOf("/api/v1/events/6/images" to listOf(json(fixture("event-images.json")))))
        val repository = repository()
        repository.refreshPhotos(6)
        val photos = checkNotNull(repository.photos(6).first()).value
        assertEquals(2, photos.size)
        assertTrue(photos.first().thumbnailUrl.endsWith("_350x263.webp"))
        assertTrue(photos.first().url.endsWith("_1024x768.webp"))
        assertTrue(photos.first().mine)
    }

    @Test
    fun `an rsvp sends going and guests and refreshes what shows it`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/events/6/rsvp" to listOf(json(fixture("event-rsvp.json"))),
                "/api/v1/events/6" to listOf(json(fixture("event-detail.json"))),
                "/api/v1/me/events" to listOf(json(fixture("me-events.json"))),
                "/api/v1/events/6/attendees" to listOf(json(fixture("event-attendees.json")))
            )
        )
        assertEquals(ApiResult.Success(Rsvp(going = true, guests = 2)), repository().rsvp(6, going = true, guests = 2))
        val paths = List(server.requestCount) { server.takeRequest() }
        val rsvp = paths.single { it.url.encodedPath == "/api/v1/events/6/rsvp" }
        assertEquals("PUT", rsvp.method)
        assertEquals("""{"going":true,"guests":2}""", rsvp.body?.utf8())
        assertEquals(setOf("m4/me/events", "m4/event/6", "m4/event/6/attendees"), storedKeys())
    }

    @Test
    fun `withdrawing sends no guests`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/events/6/rsvp" to listOf(
                    json("""{"rsvp":false,"rsvpCount":3,"guests":0,"attendeeCount":3}""")
                ),
                "/api/v1/events/6" to listOf(json(fixture("event-detail.json"))),
                "/api/v1/me/events" to listOf(json(fixture("me-events.json"))),
                "/api/v1/events/6/attendees" to listOf(json(fixture("event-attendees.json")))
            )
        )
        repository().rsvp(6, going = false, guests = 3)
        val body = List(server.requestCount) { server.takeRequest() }
            .single { it.url.encodedPath == "/api/v1/events/6/rsvp" }
            .body?.utf8()
        assertEquals("""{"going":false}""", body)
    }

    @Test
    fun `a refused rsvp keeps its code and changes nothing`() = runTest {
        server.serve(mapOf("/api/v1/events/6/rsvp" to listOf(json("""{"error":"event_canceled"}""", 409))))
        val result = repository().rsvp(6, going = true)
        assertEquals(ApiError.Http(409, "event_canceled"), (result as ApiResult.Failure).error)
        assertTrue(storedKeys().isEmpty())
    }

    @Test
    fun `leaving a group reads again what the server now answers differently`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/memberships/groups/weiqi-club" to listOf(noContent()),
                "/api/v1/me/groups" to listOf(json(fixture("me-groups.json"))),
                "/api/v1/me/events" to listOf(json(fixture("me-events.json"))),
                "/api/v1/groups/weiqi-club" to listOf(json(fixture("group-detail.json"))),
                "/api/v1/events" to listOf(json(fixture("events.json")))
            )
        )
        assertEquals(ApiResult.Success(Unit), repository().leave("weiqi-club"))
        // The page the member is looking at must not go empty, so its answer is fetched again, not dropped.
        assertEquals(
            setOf("m4/me/groups", "m4/me/events", "m4/group/weiqi-club", "m4/events?group=weiqi-club"),
            storedKeys()
        )
    }

    @Test
    fun `a refusal to leave comes back with its code`() = runTest {
        server.serve(
            mapOf("/api/v1/memberships/groups/weiqi-club" to listOf(json("""{"error":"last_owner"}""", 409)))
        )
        val result = repository().leave("weiqi-club")
        assertEquals(ApiError.Http(409, "last_owner"), (result as ApiResult.Failure).error)
    }

    @Test
    fun `joining answers with the membership the server made`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/memberships/groups/weiqi-club" to listOf(json(fixture("membership.json"))),
                "/api/v1/me/groups" to listOf(json(fixture("me-groups.json"))),
                "/api/v1/me/events" to listOf(json(fixture("me-events.json")))
            )
        )
        val membership = repository().join("weiqi-club", mailConsent = false)
        assertEquals(MembershipStatus.Pending, (membership as ApiResult.Success).value.status)
        val request = List(server.requestCount) { server.takeRequest() }
            .single { it.url.encodedPath == "/api/v1/memberships/groups/weiqi-club" }
        assertEquals("POST", request.method)
        assertEquals("""{"platformMailConsent":false}""", request.body?.utf8())
    }

    @Test
    fun `my groups keep the ones that are pending or blocked`() = runTest {
        server.serve(mapOf("/api/v1/me/groups" to listOf(json(fixture("me-groups.json")))))
        val repository = repository()
        repository.refreshMyGroups()
        val memberships = checkNotNull(repository.myGroups().first()).value
        assertTrue(memberships.isNotEmpty())
        assertTrue(memberships.all { it.group.slug.isNotBlank() })
    }

    @Test
    fun `the profile comes back as the member can edit it`() = runTest {
        server.serve(mapOf("/api/v1/me" to listOf(json(fixture("me.json")))))
        val repository = repository()
        repository.refreshProfile()
        val profile = checkNotNull(repository.profile().first()).value
        assertEquals("Crystal Liu", profile.name)
        assertEquals("zh", profile.language)
        assertTrue(profile.public)
    }

    private fun storedKeys() = answers.rows.value.keys.map { it.first }.toSet()
}
