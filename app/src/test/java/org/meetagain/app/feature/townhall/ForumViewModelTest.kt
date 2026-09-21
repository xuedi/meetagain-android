package org.meetagain.app.feature.townhall

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.testing.MainDispatcherRule
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.serve
import org.meetagain.app.testing.townHallRepository

class ForumViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun viewModel() = mainDispatcher.keep(ForumViewModel(townHallRepository(server, answers), SLUG))

    private suspend fun ForumViewModel.loaded(): Forum {
        var forum: Forum? = null
        state.test {
            assertEquals(Loadable.Loading, awaitItem())
            forum = (awaitItem() as Loadable.Loaded).value
            cancelAndIgnoreRemainingEvents()
        }
        return checkNotNull(forum)
    }

    @Test
    fun `the forum is the whole tree in the server's order, each topic before its subtopics`() = runTest {
        server.serve(mapOf(TOPICS to listOf(json(fixture("town-hall-topics.json")))))
        val forum = viewModel().loaded()
        assertEquals(listOf(1, 4, 7, 2), forum.topics.map { it.id })
        assertEquals(listOf(1, 2, 3, 1), forum.topics.map { it.depth })
        assertEquals(listOf(4), forum.subtopicsOf(1).map { it.id })
        assertFalse(forum.topic(7)!!.canHaveSubtopics)
        assertTrue(forum.topic(4)!!.canHaveSubtopics)
    }

    @Test
    fun `a new topic is sent with its title alone, and the dialog closes`() = runTest {
        server.serve(
            mapOf(
                TOPICS to listOf(
                    json(fixture("town-hall-topics.json")),
                    json(fixture("town-hall-topic-created.json"), 201),
                    json(fixture("town-hall-topics.json"))
                )
            )
        )
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.newTopic()
        viewModel.editTitle("  Where to buy stones  ")
        viewModel.dialog.test {
            assertEquals("  Where to buy stones  ", awaitItem()?.text)
            viewModel.confirmTitle()
            assertTrue(awaitItem()!!.busy)
            assertNull(awaitItem())
        }
        val posted = List(server.requestCount) { server.takeRequest() }.first { it.method == "POST" }
        assertEquals("""{"title":"Where to buy stones"}""", posted.body?.utf8())
    }

    @Test
    fun `an empty title never reaches the server`() = runTest {
        server.serve(mapOf(TOPICS to listOf(json(fixture("town-hall-topics.json")))))
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.newTopic()
        viewModel.editTitle("   ")
        viewModel.confirmTitle()
        assertEquals(TownHallProblem.EmptyTitle, viewModel.dialog.value?.problem)
        assertTrue(List(server.requestCount) { server.takeRequest() }.none { it.method == "POST" })
    }

    /** Town Hall refuses with 422 and its own codes; the dialog stays open and says why. */
    @Test
    fun `a title the server refuses keeps the dialog open with the reason`() = runTest {
        server.serve(
            mapOf(
                TOPICS to listOf(
                    json(fixture("town-hall-topics.json")),
                    json("""{"error":"title_too_long"}""", 422)
                )
            )
        )
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.newTopic()
        viewModel.editTitle("A title")
        viewModel.dialog.test {
            awaitItem()
            viewModel.confirmTitle()
            assertTrue(awaitItem()!!.busy)
            val refused = awaitItem()!!
            assertFalse(refused.busy)
            assertEquals(TownHallProblem.TitleTooLong, refused.problem)
        }
    }

    /** A closed Town Hall answers 404 like a missing group, and the memberships are asked again for the bar. */
    @Test
    fun `a Town Hall that closed is not found, and the memberships are read again`() = runTest {
        server.serve(
            mapOf(
                TOPICS to listOf(json(fixture("error-not-found.json"), 404)),
                "/api/v1/me/groups" to listOf(json(fixture("me-groups.json")))
            )
        )
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val failed = awaitItem() as Loadable.Failed
            assertEquals(404, (failed.error as ApiError.Http).status)
        }
        val paths = List(server.requestCount) { server.takeRequest().url.encodedPath }
        assertTrue(paths.contains("/api/v1/me/groups"))
    }

    @Test
    fun `the server's codes become the member's sentences`() {
        fun http(status: Int, code: String) = ApiError.Http(status, code)
        assertEquals(TownHallProblem.EmptyTitle, townHallProblemOf(http(422, "empty_title")))
        assertEquals(TownHallProblem.TooDeep, townHallProblemOf(http(422, "too_deep")))
        assertEquals(TownHallProblem.EmptyReply, townHallProblemOf(http(422, "content_required")))
        assertEquals(TownHallProblem.ReplyTooLong, townHallProblemOf(http(422, "content_too_long")))
        assertEquals(TownHallProblem.NotAllowed, townHallProblemOf(http(403, "forbidden")))
        assertEquals(TownHallProblem.Gone, townHallProblemOf(http(404, "not_found")))
        assertEquals(TownHallProblem.Offline, townHallProblemOf(ApiError.Offline))
    }

    private companion object {
        const val SLUG = "weiqi-club"
        const val TOPICS = "/api/v1/community/groups/$SLUG/town-hall/topics"
    }
}
