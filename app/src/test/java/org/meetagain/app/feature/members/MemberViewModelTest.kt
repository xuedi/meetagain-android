package org.meetagain.app.feature.members

import app.cash.turbine.test
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.meetagain.app.core.data.MemberProfile
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.testing.MainDispatcherRule
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.noContent
import org.meetagain.app.testing.serve

/** The block matrix as the app renders it, and follow and block applying at once. */
class MemberViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun viewModel() = mainDispatcher.keep(MemberViewModel(memberRepository(server, answers), MEMBER_ID))

    private fun settled(): List<RecordedRequest> =
        generateSequence { server.takeRequest(SETTLE_MILLIS, TimeUnit.MILLISECONDS) }.toList()

    private fun profile(body: String = fixture("member-profile.json")) = mapOf(
        MEMBER to listOf(json(body)),
        CONVERSATIONS to listOf(json(fixture("conversations.json"))),
        BLOCKS to listOf(json(fixture("blocks.json")))
    )

    private suspend fun loaded(routes: Map<String, List<mockwebserver3.MockResponse>>): MemberViewModel {
        server.serve(routes)
        val viewModel = viewModel()
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return viewModel
    }

    private fun shown(viewModel: MemberViewModel): MemberProfile = (viewModel.state.value as Loadable.Loaded).value

    /** Waits for what the member tapped to be over, which is what busy says. */
    private suspend fun settle(viewModel: MemberViewModel) {
        viewModel.busy.first { !it }
    }

    /** Runs what the member tapped and waits for it to be over, which is what busy says. */
    private suspend fun act(viewModel: MemberViewModel, tap: () -> Unit) {
        viewModel.busy.test {
            assertFalse(awaitItem())
            tap()
            assertTrue(awaitItem())
            assertFalse(awaitItem())
        }
    }

    @Test
    fun `a member page carries the standing between the two of them`() = runTest {
        val viewModel = loaded(profile())
        val member = shown(viewModel)
        assertEquals("Adem Lane", member.name)
        assertFalse(member.following)
        assertTrue(member.followsMe)
        assertTrue(member.canMessage)
        assertFalse(member.blockedByMe)
    }

    /** The other member has blocked the caller: their page is refused, and the screen says only that. */
    @Test
    fun `a member who has blocked the caller answers forbidden`() = runTest {
        server.serve(mapOf(MEMBER to listOf(json(fixture("error-forbidden.json"), 403))))
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val failed = awaitItem() as Loadable.Failed
            assertEquals("forbidden", (failed.error as ApiError.Http).code)
        }
    }

    /** The caller has blocked them: the page reads normally, and unblocking is the only thing on it. */
    @Test
    fun `a member the caller has blocked reads with blockedByMe`() = runTest {
        val viewModel = loaded(profile(fixture("member-profile-blocked.json")))
        val member = shown(viewModel)
        assertTrue(member.blockedByMe)
        assertFalse(member.canMessage)
    }

    @Test
    fun `following applies at once and is a post`() = runTest {
        val viewModel = loaded(
            profile() + mapOf("$MEMBER/follow" to listOf(noContent()))
        )
        viewModel.state.test {
            assertFalse((awaitItem() as Loadable.Loaded).value.following)
            viewModel.toggleFollow()
            assertTrue((awaitItem() as Loadable.Loaded).value.following)
            cancelAndIgnoreRemainingEvents()
        }
        settle(viewModel)
        assertEquals("POST", settled().single { it.url.encodedPath == "$MEMBER/follow" }.method)
    }

    /** A refused change was never made, so the stored answer is what the screen goes back to. */
    @Test
    fun `a refused follow goes back and says why`() = runTest {
        val viewModel = loaded(
            profile() + mapOf("$MEMBER/follow" to listOf(json(fixture("error-blocked.json"), 409)))
        )
        viewModel.problem.test {
            assertNull(awaitItem())
            act(viewModel) { viewModel.toggleFollow() }
            assertEquals(MemberProblem.Blocked, awaitItem())
        }
        assertFalse(shown(viewModel).following)
    }

    @Test
    fun `unfollowing is the same call the other way round`() = runTest {
        val viewModel = loaded(
            profile(fixture("member-profile-followed.json")) + mapOf("$MEMBER/follow" to listOf(noContent()))
        )
        assertTrue(shown(viewModel).following)
        act(viewModel) { viewModel.toggleFollow() }
        assertEquals("DELETE", settled().single { it.url.encodedPath == "$MEMBER/follow" }.method)
    }

    @Test
    fun `blocking applies at once and refreshes what it changes`() = runTest {
        val viewModel = loaded(profile() + mapOf("$BLOCKS/$MEMBER_ID" to listOf(noContent())))
        viewModel.state.test {
            assertFalse((awaitItem() as Loadable.Loaded).value.blockedByMe)
            viewModel.toggleBlock()
            val blocked = (awaitItem() as Loadable.Loaded).value
            assertTrue(blocked.blockedByMe)
            assertFalse(blocked.canMessage)
            cancelAndIgnoreRemainingEvents()
        }
        settle(viewModel)
        val paths = settled().map { it.url.encodedPath }
        assertTrue(paths.contains(BLOCKS))
        assertTrue(paths.contains(CONVERSATIONS))
    }

    @Test
    fun `unblocking is the same call the other way round`() = runTest {
        val viewModel = loaded(
            profile(fixture("member-profile-blocked.json")) + mapOf("$BLOCKS/$MEMBER_ID" to listOf(noContent()))
        )
        assertTrue(shown(viewModel).blockedByMe)
        act(viewModel) { viewModel.toggleBlock() }
        assertEquals("DELETE", settled().single { it.url.encodedPath == "$BLOCKS/$MEMBER_ID" }.method)
    }

    private companion object {
        const val MEMBER_ID = 6
        const val MEMBER = "/api/v1/community/members/6"
        const val BLOCKS = "/api/v1/community/blocks"
        const val CONVERSATIONS = "/api/v1/community/conversations"
        const val SETTLE_MILLIS = 500L
    }
}
