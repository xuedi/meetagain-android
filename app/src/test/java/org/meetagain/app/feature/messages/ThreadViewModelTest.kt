package org.meetagain.app.feature.messages

import app.cash.turbine.test
import java.util.concurrent.TimeUnit
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
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.testing.MainDispatcherRule
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.noContent
import org.meetagain.app.testing.serve

class ThreadViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun viewModel(partner: Int = PARTNER) =
        mainDispatcher.keep(ThreadViewModel(memberRepository(server, answers), partner))

    /** Every request the server has had, waiting a moment for the ones a ViewModel still has on their way. */
    private fun settled(): List<RecordedRequest> =
        generateSequence { server.takeRequest(SETTLE_MILLIS, TimeUnit.MILLISECONDS) }.toList()

    private fun thread(vararg answers: mockwebserver3.MockResponse) = mapOf(
        THREAD to answers.toList(),
        "$THREAD/read" to listOf(noContent()),
        CONVERSATIONS to listOf(json(fixture("conversations.json")))
    )

    /** Oldest first, so the newest message is at the bottom where the composer is. */
    @Test
    fun `the thread reads oldest first`() = runTest {
        server.serve(thread(json(fixture("message-thread.json"))))
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val shown = (awaitItem() as Loadable.Loaded).value
            assertEquals(listOf(31, 32, 33), shown.messages.map { it.id })
            assertFalse(shown.hasEarlier)
            assertEquals("Adem Lane", shown.partner.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the thread is marked read once, after the newest page is shown`() = runTest {
        server.serve(thread(json(fixture("message-thread.json"))))
        val viewModel = viewModel()
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            awaitItem()
            viewModel.load()
            cancelAndIgnoreRemainingEvents()
        }
        val read = settled().filter { it.url.encodedPath == "$THREAD/read" }
        assertEquals(1, read.size)
        assertEquals("POST", read.single().method)
    }

    /** Nothing unread means nothing to mark: the call is not made at all. */
    @Test
    fun `a thread with nothing unread is not marked read`() = runTest {
        server.serve(thread(json(fixture("message-thread-blocked.json"))))
        val viewModel = mainDispatcher.keep(ThreadViewModel(memberRepository(server, answers), 12))
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(settled().none { it.url.encodedPath.endsWith("/read") })
    }

    @Test
    fun `a blocked thread reads and says nothing can be sent`() = runTest {
        server.serve(
            mapOf("/api/v1/community/conversations/12" to listOf(json(fixture("message-thread-blocked.json"))))
        )
        mainDispatcher.keep(ThreadViewModel(memberRepository(server, answers), 12)).state.test {
            assertEquals(Loadable.Loading, awaitItem())
            assertTrue((awaitItem() as Loadable.Loaded).value.blocked)
        }
    }

    @Test
    fun `a send across a block is its own sentence`() = runTest {
        server.serve(
            thread(json(fixture("message-thread.json")), json(fixture("error-blocked.json"), 409))
        )
        val viewModel = loaded()
        viewModel.draft("Hello again")
        viewModel.problem.test {
            assertNull(awaitItem())
            viewModel.send()
            assertEquals(ThreadProblem.Blocked, awaitItem())
        }
    }

    @Test
    fun `an empty message never reaches the server`() = runTest {
        server.serve(thread(json(fixture("message-thread.json"))))
        val viewModel = loaded()
        viewModel.draft("   ")
        viewModel.problem.test {
            assertNull(awaitItem())
            viewModel.send()
            assertEquals(ThreadProblem.Empty, awaitItem())
        }
        assertTrue(settled().none { it.method == "POST" && it.url.encodedPath == THREAD })
    }

    @Test
    fun `an over-long message is its own sentence`() = runTest {
        server.serve(
            thread(json(fixture("message-thread.json")), json("""{"error":"content_too_long"}""", 422))
        )
        val viewModel = loaded()
        viewModel.draft("Far too much")
        viewModel.problem.test {
            assertNull(awaitItem())
            viewModel.send()
            assertEquals(ThreadProblem.TooLong, awaitItem())
        }
    }

    /** The window closed while they were typing: the sentence, and the thread fetched again so the control goes. */
    @Test
    fun `a late edit says the window has passed and reads the thread again`() = runTest {
        server.serve(
            mapOf(
                THREAD to listOf(json(fixture("message-thread.json"))),
                "$THREAD/read" to listOf(noContent()),
                MESSAGE to listOf(json(fixture("error-edit-window.json"), 409))
            )
        )
        val viewModel = loaded()
        val editable = (viewModel.state.value as Loadable.Loaded).value.messages.single { it.editable }
        viewModel.edit(editable)
        assertEquals(editable.id, viewModel.draft.value.editing)
        viewModel.problem.test {
            assertNull(awaitItem())
            viewModel.send()
            assertEquals(ThreadProblem.EditWindowExpired, awaitItem())
        }
        assertNull(viewModel.draft.value.editing)
        assertTrue(settled().count { it.url.encodedPath == THREAD && it.method == "GET" } > 1)
    }

    /** A stored answer can age past the ten minutes; the control is then not offered at all. */
    @Test
    fun `a message outside the window cannot be edited`() = runTest {
        server.serve(thread(json(fixture("message-thread.json"))))
        val viewModel = loaded()
        val old = (viewModel.state.value as Loadable.Loaded).value.messages.single { it.id == 32 }
        assertFalse(old.editable)
        viewModel.problem.test {
            assertNull(awaitItem())
            viewModel.edit(old)
            assertEquals(ThreadProblem.EditWindowExpired, awaitItem())
        }
        assertNull(viewModel.draft.value.editing)
    }

    @Test
    fun `earlier messages go in front of the page on screen`() = runTest {
        server.serve(
            mapOf(
                THREAD to listOf(
                    json(fixture("message-thread-long.json")),
                    json(fixture("message-thread-newest.json")),
                    json(fixture("message-thread-long.json"))
                ),
                "$THREAD/read" to listOf(noContent())
            )
        )
        val viewModel = viewModel()
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            var shown = (awaitItem() as Loadable.Loaded).value
            assertTrue(shown.hasEarlier)
            viewModel.loadEarlier()
            var state = awaitItem()
            while ((state as Loadable.Loaded).value.loadingEarlier) state = awaitItem()
            shown = state.value
            assertEquals(listOf(1, 240), shown.messages.map { it.id })
            assertFalse(shown.hasEarlier)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Waits for the first page so the next answer on that path belongs to what the test does next. */
    private suspend fun loaded(): ThreadViewModel {
        val viewModel = viewModel()
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return viewModel
    }

    private companion object {
        const val PARTNER = 6
        const val THREAD = "/api/v1/community/conversations/6"
        const val MESSAGE = "/api/v1/community/messages/33"
        const val CONVERSATIONS = "/api/v1/community/conversations"
        const val SETTLE_MILLIS = 500L
    }
}
