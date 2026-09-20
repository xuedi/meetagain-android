package org.meetagain.app.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import org.meetagain.app.core.network.CommunityError
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.noContent
import org.meetagain.app.testing.serve

/** The community endpoints: the inbox, a thread, sending, editing, members, following and blocking. */
class CommunityRepositoryTest {
    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun repository() = memberRepository(server, answers)

    private fun requests() = List(server.requestCount) { server.takeRequest() }

    @Test
    fun `the inbox carries the partner, the counts and when the last message came`() = runTest {
        server.serve(mapOf(CONVERSATIONS to listOf(json(fixture("conversations.json")))))
        val repository = repository()
        repository.refreshInbox()
        val inbox = checkNotNull(repository.inbox().first()).value
        assertEquals(2, inbox.total)
        val adem = inbox.entries.first()
        assertEquals("Adem Lane", adem.partner.name)
        assertEquals(12, adem.messages)
        assertEquals(2, adem.unread)
        assertEquals("2026-09-19T05:40:00Z", adem.lastMessageAt.toString())
        assertNull(inbox.entries[1].partner.avatarUrl)
    }

    /** A field the app does not know must never cost the member their inbox. */
    @Test
    fun `an unknown field in an answer is ignored`() = runTest {
        server.serve(mapOf(CONVERSATIONS to listOf(json(fixture("conversations.json")))))
        val repository = repository()
        assertEquals(ApiResult.Success(Unit), repository.refreshInbox())
        assertEquals(2, checkNotNull(repository.inbox().first()).value.entries.size)
    }

    @Test
    fun `the first page is stored and the next one only read`() = runTest {
        server.serve(
            mapOf(
                CONVERSATIONS to listOf(
                    json(fixture("conversations.json")),
                    json(fixture("conversations-page-two.json"))
                )
            )
        )
        val repository = repository()
        repository.refreshInbox()
        val more = repository.moreConversations(offset = 20)
        assertTrue(more is ApiResult.Success)
        assertEquals("Mila Novak", (more as ApiResult.Success).value.entries.single().partner.name)
        assertEquals(2, checkNotNull(repository.inbox().first()).value.entries.size)
        assertEquals("20", requests().last().url.queryParameter("offset"))
    }

    @Test
    fun `a thread that fits in one page is one call`() = runTest {
        server.serve(mapOf(THREAD to listOf(json(fixture("message-thread.json")))))
        val repository = repository()
        repository.refreshThread(6)
        val thread = checkNotNull(repository.thread(6).first()).value
        assertEquals(listOf(31, 32, 33), thread.messages.map { it.id })
        assertFalse(thread.hasEarlier)
        assertFalse(thread.blocked)
        assertTrue(thread.messages.first().systemNote)
        assertEquals(1, server.requestCount)
        assertEquals("100", requests().single().url.queryParameter("limit"))
    }

    /** Oldest first with offset paging, so the newest page of a long thread is a second call and no more. */
    @Test
    fun `a long thread is fetched twice, at the offset the total gives`() = runTest {
        server.serve(
            mapOf(
                THREAD to listOf(json(fixture("message-thread-long.json")), json(fixture("message-thread-newest.json")))
            )
        )
        val repository = repository()
        repository.refreshThread(6)
        val thread = checkNotNull(repository.thread(6).first()).value
        assertEquals(240, thread.total)
        assertEquals("The newest one.", thread.messages.single().text)
        assertTrue(thread.hasEarlier)
        val offsets = requests().map { it.url.queryParameter("offset") }
        assertEquals(listOf(null, "140"), offsets)
    }

    @Test
    fun `earlier messages are asked for by where the page on screen starts`() = runTest {
        server.serve(
            mapOf(
                THREAD to listOf(json(fixture("message-thread-long.json")), json(fixture("message-thread-newest.json")))
            )
        )
        val repository = repository()
        repository.earlierMessages(6, before = 140)
        val asked = requests().single().url
        assertEquals("40", asked.queryParameter("offset"))
        assertEquals("100", asked.queryParameter("limit"))
    }

    @Test
    fun `the last page before the first message asks for exactly what is left`() = runTest {
        server.serve(mapOf(THREAD to listOf(json(fixture("message-thread.json")))))
        repository().earlierMessages(6, before = 40)
        val asked = requests().single().url
        assertNull(asked.queryParameter("offset"))
        assertEquals("40", asked.queryParameter("limit"))
    }

    /** The server's own window is trusted, and the one counted here closes it on a stored answer that has aged. */
    @Test
    fun `an own message past the ten minutes is no longer editable`() = runTest {
        server.serve(mapOf(THREAD to listOf(json(fixture("message-thread.json")))))
        val repository = repository()
        repository.refreshThread(6)
        val messages = checkNotNull(repository.thread(6).first()).value.messages
        // 09:58+02:00 is two minutes before the test clock; the one before it is a day old.
        assertTrue(messages.single { it.id == 33 }.editable)
        assertFalse(messages.single { it.id == 32 }.editable)
    }

    @Test
    fun `sending refreshes the thread and the inbox`() = runTest {
        server.serve(
            mapOf(
                THREAD to listOf(
                    json(fixture("message-sent.json"), status = 201),
                    json(fixture("message-thread.json"))
                ),
                CONVERSATIONS to listOf(json(fixture("conversations.json")))
            )
        )
        assertEquals(ApiResult.Success(Unit), repository().sendMessage(6, "On my way."))
        val sent = requests().first { it.method == "POST" }
        assertEquals(THREAD, sent.url.encodedPath)
        assertEquals("""{"content":"On my way."}""", sent.body?.utf8())
        assertEquals(
            setOf("m4/community/conversation/6", "m4/community/conversations"),
            answers.rows.value.keys.map {
                it.first
            }.toSet()
        )
    }

    @Test
    fun `a send across a block is refused with its own code`() = runTest {
        server.serve(mapOf(THREAD to listOf(json(fixture("error-blocked.json"), status = 409))))
        val result = repository().sendMessage(6, "Hello")
        assertEquals(CommunityError.BLOCKED, ((result as ApiResult.Failure).error as ApiError.Http).code)
    }

    @Test
    fun `a late edit is refused with its own code`() = runTest {
        server.serve(mapOf(MESSAGE to listOf(json(fixture("error-edit-window.json"), status = 409))))
        val result = repository().editMessage(6, 33, "Changed my mind.")
        val error = (result as ApiResult.Failure).error as ApiError.Http
        assertEquals(CommunityError.EDIT_WINDOW_EXPIRED, error.code)
        assertEquals(409, error.status)
        assertEquals("PATCH", requests().single().method)
    }

    @Test
    fun `marking a thread read is one call that refreshes the inbox`() = runTest {
        server.serve(
            mapOf(
                "$THREAD/read" to listOf(noContent()),
                CONVERSATIONS to listOf(json(fixture("conversations.json")))
            )
        )
        assertEquals(ApiResult.Success(Unit), repository().markThreadRead(6))
        val paths = requests().map { it.url.encodedPath }
        assertEquals(listOf("$THREAD/read", CONVERSATIONS), paths)
        assertEquals(listOf("m4/community/conversations"), answers.rows.value.keys.map { it.first })
    }

    @Test
    fun `a member page carries the standing between the two of them`() = runTest {
        server.serve(mapOf(MEMBER to listOf(json(fixture("member-profile.json")))))
        val repository = repository()
        repository.refreshMember(6)
        val member = checkNotNull(repository.member(6).first()).value
        assertEquals("Adem Lane", member.name)
        assertFalse(member.following)
        assertTrue(member.followsMe)
        assertFalse(member.blockedByMe)
        assertTrue(member.canMessage)
        assertEquals("2024-03-11T13:22:00Z", member.memberSince.toString())
    }

    /** The other member has blocked the caller: the page is refused, and that is its own sentence, not a session end. */
    @Test
    fun `a member who has blocked the caller answers forbidden`() = runTest {
        server.serve(mapOf(MEMBER to listOf(json(fixture("error-forbidden.json"), status = 403))))
        val result = repository().refreshMember(6)
        val error = (result as ApiResult.Failure).error as ApiError.Http
        assertEquals(CommunityError.FORBIDDEN, error.code)
        assertEquals(403, error.status)
    }

    @Test
    fun `a group's member list keeps only pictures on the app's own server`() = runTest {
        server.serve(mapOf(GROUP_MEMBERS to listOf(json(fixture("group-members.json")))))
        val repository = repository()
        repository.refreshGroupMembers("my-community")
        val members = checkNotNull(repository.groupMembers("my-community").first()).value
        assertEquals(3, members.total)
        assertTrue(members.people[1].avatarUrl!!.startsWith("https://meetagain.org/"))
        assertNull(members.people[2].avatarUrl)
    }

    @Test
    fun `following is a post and unfollowing a delete, and both refresh that member`() = runTest {
        server.serve(
            mapOf(
                "$MEMBER/follow" to listOf(noContent()),
                MEMBER to listOf(json(fixture("member-profile.json")))
            )
        )
        val repository = repository()
        assertEquals(ApiResult.Success(Unit), repository.follow(6))
        assertEquals(ApiResult.Success(Unit), repository.unfollow(6))
        val methods = requests().filter { it.url.encodedPath == "$MEMBER/follow" }.map { it.method }
        assertEquals(listOf("POST", "DELETE"), methods)
    }

    @Test
    fun `blocking refreshes the inbox, the blocked list and that member`() = runTest {
        server.serve(
            mapOf(
                "$BLOCKS/6" to listOf(noContent()),
                BLOCKS to listOf(json(fixture("blocks.json"))),
                CONVERSATIONS to listOf(json(fixture("conversations.json"))),
                MEMBER to listOf(json(fixture("member-profile.json")))
            )
        )
        val repository = repository()
        assertEquals(ApiResult.Success(Unit), repository.block(6))
        assertEquals(
            setOf("m4/community/conversations", "m4/community/blocks", "m4/community/member/6"),
            answers.rows.value.keys.map { it.first }.toSet()
        )
        assertEquals("POST", requests().first { it.url.encodedPath == "$BLOCKS/6" }.method)
    }

    @Test
    fun `unblocking is the same call the other way round`() = runTest {
        server.serve(
            mapOf(
                "$BLOCKS/6" to listOf(noContent()),
                BLOCKS to listOf(json(fixture("blocks.json"))),
                CONVERSATIONS to listOf(json(fixture("conversations.json"))),
                MEMBER to listOf(json(fixture("member-profile.json")))
            )
        )
        assertEquals(ApiResult.Success(Unit), repository().unblock(6))
        assertEquals("DELETE", requests().first { it.url.encodedPath == "$BLOCKS/6" }.method)
    }

    @Test
    fun `a blocked thread still reads and says nothing can be sent`() = runTest {
        server.serve(
            mapOf("/api/v1/community/conversations/12" to listOf(json(fixture("message-thread-blocked.json"))))
        )
        val repository = repository()
        repository.refreshThread(12)
        val thread = checkNotNull(repository.thread(12).first()).value
        assertTrue(thread.blocked)
        assertEquals(1, thread.messages.size)
    }

    private companion object {
        const val CONVERSATIONS = "/api/v1/community/conversations"
        const val THREAD = "/api/v1/community/conversations/6"
        const val MESSAGE = "/api/v1/community/messages/33"
        const val MEMBER = "/api/v1/community/members/6"
        const val BLOCKS = "/api/v1/community/blocks"
        const val GROUP_MEMBERS = "/api/v1/community/groups/my-community/members"
    }
}
