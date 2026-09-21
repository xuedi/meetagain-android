package org.meetagain.app.feature.townhall

import app.cash.turbine.test
import java.time.Duration
import java.util.concurrent.TimeUnit
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
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.UNDO_WINDOW
import org.meetagain.app.testing.MainDispatcherRule
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.noContent
import org.meetagain.app.testing.serve
import org.meetagain.app.testing.townHallRepository

class TopicViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun viewModel(id: Int = GAME_NIGHTS, undoWindow: Duration = UNDO_WINDOW) =
        mainDispatcher.keep(TopicViewModel(townHallRepository(server, answers), SLUG, id, undoWindow))

    /** Replies have the event comments' shape, so the event's fixture serves as a topic's replies. */
    private fun topic(id: Int = GAME_NIGHTS) = mapOf(
        TOPICS to listOf(json(fixture("town-hall-topics.json"))),
        replies(id) to listOf(json(fixture("event-comments.json")))
    )

    private suspend fun TopicViewModel.ready() {
        replies.test {
            assertEquals(Loadable.Loading, awaitItem())
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        header.test {
            if (awaitItem() == null) awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a topic shows its subtopics above its replies`() = runTest {
        server.serve(topic())
        val viewModel = viewModel()
        viewModel.ready()
        val header = viewModel.header.value!!
        assertEquals("Game nights", header.topic.title)
        assertEquals(listOf("Openings for beginners"), header.subtopics.map { it.title })
        val replies = (viewModel.replies.value as Loadable.Loaded).value
        assertEquals(listOf(10, 9), replies.visible.map { it.id })
        assertEquals(12, replies.visible.first().authorId)
    }

    @Test
    fun `a reply is sent and the replies and the forum are read again`() = runTest {
        server.serve(
            topic() + mapOf(
                replies(GAME_NIGHTS) to listOf(
                    json(fixture("event-comments.json")),
                    json("""{"id":99,"content":"Count me in","createdAt":null}""", 201),
                    json(fixture("event-comments.json"))
                )
            )
        )
        val viewModel = viewModel()
        viewModel.ready()
        viewModel.draft("Count me in")
        viewModel.busy.test {
            assertFalse(awaitItem())
            viewModel.send()
            assertTrue(awaitItem())
            assertFalse(awaitItem())
        }
        assertEquals("", viewModel.draft.value)
        val requests = List(server.requestCount) { server.takeRequest() }
        val posted = requests.first { it.method == "POST" }
        assertEquals("""{"content":"Count me in"}""", posted.body?.utf8())
        val afterPost = requests.dropWhile { it.method != "POST" }.drop(1).map { it.url.encodedPath }
        assertTrue(afterPost.contains(TOPICS))
    }

    @Test
    fun `a reply that is too long says so`() = runTest {
        server.serve(
            topic() + mapOf(
                replies(GAME_NIGHTS) to listOf(
                    json(fixture("event-comments.json")),
                    json("""{"error":"content_too_long"}""", 422)
                )
            )
        )
        val viewModel = viewModel()
        viewModel.ready()
        viewModel.draft("Hello")
        viewModel.problem.test {
            assertNull(awaitItem())
            viewModel.send()
            assertEquals(TownHallProblem.ReplyTooLong, awaitItem())
        }
    }

    @Test
    fun `a reply delete taken back never reaches the server`() = runTest {
        server.serve(topic())
        val viewModel = viewModel()
        viewModel.replies.test {
            assertEquals(Loadable.Loading, awaitItem())
            val first = (awaitItem() as Loadable.Loaded).value.visible.first()
            viewModel.deleteReply(first.id)
            assertFalse((awaitItem() as Loadable.Loaded).value.visible.any { it.id == first.id })
            viewModel.undoDeleteReply(first.id)
            assertTrue((awaitItem() as Loadable.Loaded).value.visible.any { it.id == first.id })
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(List(server.requestCount) { server.takeRequest() }.none { it.method == "DELETE" })
    }

    @Test
    fun `a reply delete that stands goes to the reply under its own topic`() = runTest {
        server.serve(topic() + mapOf("${replies(GAME_NIGHTS)}/10" to listOf(noContent())))
        val viewModel = viewModel(undoWindow = Duration.ZERO)
        viewModel.replies.test {
            assertEquals(Loadable.Loading, awaitItem())
            awaitItem()
            viewModel.deleteReply(10)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        val deleted = generateSequence { server.takeRequest(2, TimeUnit.SECONDS) }
            .any { it.method == "DELETE" && it.url.encodedPath == "${replies(GAME_NIGHTS)}/10" }
        assertTrue(deleted)
    }

    @Test
    fun `a subtopic is started under this topic`() = runTest {
        server.serve(
            topic() + mapOf(
                TOPICS to listOf(
                    json(fixture("town-hall-topics.json")),
                    json(fixture("town-hall-topic-created.json"), 201),
                    json(fixture("town-hall-topics.json"))
                )
            )
        )
        val viewModel = viewModel()
        viewModel.ready()
        viewModel.newSubtopic()
        viewModel.editTitle("Endgame drills")
        viewModel.dialog.test {
            awaitItem()
            viewModel.confirmTitle()
            assertTrue(awaitItem()!!.busy)
            assertNull(awaitItem())
        }
        val posted = List(server.requestCount) { server.takeRequest() }.first { it.method == "POST" }
        assertEquals("""{"title":"Endgame drills","parentId":$GAME_NIGHTS}""", posted.body?.utf8())
    }

    @Test
    fun `renaming starts from the topic's own title`() = runTest {
        server.serve(topic(MINE) + mapOf(topicPath(MINE) to listOf(json(fixture("town-hall-topic-created.json")))))
        val viewModel = viewModel(MINE)
        viewModel.ready()
        viewModel.rename()
        assertEquals(TitleDialog(TitleKind.Rename, "Tournament in October"), viewModel.dialog.value)
        viewModel.editTitle("Tournament in November")
        viewModel.dialog.test {
            awaitItem()
            viewModel.confirmTitle()
            assertTrue(awaitItem()!!.busy)
            assertNull(awaitItem())
        }
        val patched = List(server.requestCount) { server.takeRequest() }.first { it.method == "PATCH" }
        assertEquals(topicPath(MINE), patched.url.encodedPath)
        assertEquals("""{"title":"Tournament in November"}""", patched.body?.utf8())
    }

    @Test
    fun `a deleted topic lets the screen leave it`() = runTest {
        server.serve(topic(MINE) + mapOf(topicPath(MINE) to listOf(noContent())))
        val viewModel = viewModel(MINE)
        viewModel.ready()
        viewModel.deleted.test {
            assertFalse(awaitItem())
            viewModel.deleteTopic()
            assertTrue(awaitItem())
        }
        assertTrue(List(server.requestCount) { server.takeRequest() }.any { it.method == "DELETE" })
    }

    private companion object {
        const val SLUG = "weiqi-club"
        const val TOPICS = "/api/v1/community/groups/$SLUG/town-hall/topics"
        const val GAME_NIGHTS = 1
        const val MINE = 2

        fun topicPath(id: Int) = "$TOPICS/$id"

        fun replies(id: Int) = "$TOPICS/$id/replies"
    }
}
