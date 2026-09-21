package org.meetagain.app.feature.conversation

import app.cash.turbine.test
import java.io.ByteArrayInputStream
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
import org.meetagain.app.core.network.Upload
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.UNDO_WINDOW
import org.meetagain.app.testing.MainDispatcherRule
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.noContent
import org.meetagain.app.testing.serve

class ConversationViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun viewModel(undoWindow: Duration = UNDO_WINDOW) =
        mainDispatcher.keep(ConversationViewModel(memberRepository(server, answers), EVENT, undoWindow))

    private fun conversation() = mapOf(
        "/api/v1/events/$EVENT/comments" to listOf(json(fixture("event-comments.json"))),
        "/api/v1/events/$EVENT/images" to listOf(json(fixture("event-images.json")))
    )

    @Test
    fun `the newest comments show first`() = runTest {
        server.serve(conversation())
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val comments = (awaitItem() as Loadable.Loaded).value
            assertTrue(comments.visible.isNotEmpty())
            assertEquals(comments.visible.map { it.id }.sortedDescending(), comments.visible.map { it.id })
        }
    }

    @Test
    fun `a comment is sent and the list is read again`() = runTest {
        server.serve(
            conversation() + mapOf(
                "/api/v1/events/$EVENT/comments" to listOf(
                    json(fixture("event-comments.json")),
                    json("""{"id":99,"content":"See you there","createdAt":null}""", 201),
                    json(fixture("event-comments.json"))
                )
            )
        )
        val viewModel = viewModel()
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.draft("See you there")
        viewModel.busy.test {
            assertFalse(awaitItem())
            viewModel.send()
            assertTrue(awaitItem())
            assertFalse(awaitItem())
        }
        assertEquals("", viewModel.draft.value)
        val posted = List(server.requestCount) { server.takeRequest() }.first { it.method == "POST" }
        assertEquals("""{"content":"See you there"}""", posted.body?.utf8())
    }

    @Test
    fun `a refused comment says only members can write`() = runTest {
        server.serve(
            conversation() + mapOf(
                "/api/v1/events/$EVENT/comments" to listOf(
                    json(fixture("event-comments.json")),
                    json("""{"error":"forbidden"}""", 403)
                )
            )
        )
        val viewModel = viewModel()
        // The list is read first; only then does the next answer for that path belong to the comment.
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.draft("Hello")
        viewModel.problem.test {
            assertNull(awaitItem())
            viewModel.send()
            assertEquals(ConversationProblem.NotAMember, awaitItem())
        }
    }

    @Test
    fun `an empty comment never reaches the server`() = runTest {
        server.serve(conversation())
        val viewModel = viewModel()
        viewModel.draft("   ")
        viewModel.send()
        assertEquals(ConversationProblem.Empty, viewModel.problem.value)
        assertTrue(List(server.requestCount) { server.takeRequest() }.none { it.method == "POST" })
    }

    @Test
    fun `a delete taken back never reaches the server`() = runTest {
        server.serve(conversation())
        val viewModel = viewModel()
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val first = (awaitItem() as Loadable.Loaded).value.visible.first()
            viewModel.deleteComment(first.id)
            assertFalse((awaitItem() as Loadable.Loaded).value.visible.any { it.id == first.id })
            viewModel.undoDeleteComment(first.id)
            assertTrue((awaitItem() as Loadable.Loaded).value.visible.any { it.id == first.id })
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(List(server.requestCount) { server.takeRequest() }.none { it.method == "DELETE" })
    }

    @Test
    fun `a delete that stands reaches the server after the undo window`() = runTest {
        server.serve(
            conversation() + mapOf("/api/v1/events/$EVENT/comments/10" to listOf(noContent()))
        )
        // With no window to wait out, the delete goes straight to the server.
        val viewModel = viewModel(undoWindow = Duration.ZERO)
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            awaitItem()
            viewModel.deleteComment(10)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        val deleted = generateSequence { server.takeRequest(2, TimeUnit.SECONDS) }
            .any { it.method == "DELETE" && it.url.encodedPath.endsWith("/comments/10") }
        assertTrue(deleted)
    }

    @Test
    fun `a photo is uploaded as a file`() = runTest {
        server.serve(
            conversation() + mapOf(
                "/api/v1/events/$EVENT/images" to listOf(
                    json(fixture("event-images.json")),
                    json("""{"id":51,"url":"https://meetagain.org/images/thumbnails/a_1024x768.webp"}""", 201),
                    json(fixture("event-images.json"))
                )
            )
        )
        val viewModel = viewModel()
        val bytes = ByteArray(16) { it.toByte() }
        viewModel.busy.test {
            assertFalse(awaitItem())
            viewModel.addPhoto(
                Upload("holiday.jpg", "image/jpeg", bytes.size.toLong()) {
                    ByteArrayInputStream(bytes)
                }
            )
            assertTrue(awaitItem())
            assertFalse(awaitItem())
        }
        val posted = List(server.requestCount) { server.takeRequest() }.first { it.method == "POST" }
        assertTrue(posted.headers["Content-Type"].orEmpty().startsWith("multipart/form-data"))
        assertTrue(posted.body?.utf8().orEmpty().contains("holiday.jpg"))
    }

    private companion object {
        const val EVENT = 6
    }
}
