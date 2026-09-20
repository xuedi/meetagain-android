package org.meetagain.app.feature.messages

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.serve

class MessagesViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun viewModel() = mainDispatcher.keep(MessagesViewModel(memberRepository(server, answers)))

    @Test
    fun `the conversations show newest first, with their counts`() = runTest {
        server.serve(mapOf(CONVERSATIONS to listOf(json(fixture("conversations.json")))))
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val inbox = (awaitItem() as Loadable.Loaded).value
            assertEquals(listOf("Adem Lane", "Ali Mahdi"), inbox.entries.map { it.partner.name })
            assertEquals(2, inbox.entries.first().unread)
            assertFalse(inbox.hasMore)
        }
    }

    @Test
    fun `an empty inbox is loaded, not failed`() = runTest {
        server.serve(mapOf(CONVERSATIONS to listOf(json("""{"items":[],"total":0,"limit":20,"offset":0}"""))))
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val inbox = (awaitItem() as Loadable.Loaded).value
            assertTrue(inbox.entries.isEmpty())
            assertFalse(inbox.hasMore)
        }
    }

    @Test
    fun `nothing stored and no connection is the error state`() = runTest {
        server.close()
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(Loadable.Failed(ApiError.Offline), awaitItem())
        }
    }

    /** A stored answer the refresh could not replace is shown, with the strip above it. */
    @Test
    fun `a stored inbox is shown when the refresh fails`() = runTest {
        server.serve(mapOf(CONVERSATIONS to listOf(json(fixture("conversations.json")))))
        memberRepository(server, answers).refreshInbox()
        server.close()
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            var state = awaitItem()
            while ((state as? Loadable.Loaded)?.stale == null) state = awaitItem()
            val loaded = state as Loadable.Loaded
            assertEquals(2, loaded.value.entries.size)
            assertEquals(ApiError.Offline, loaded.stale?.error)
        }
    }

    @Test
    fun `the next page is asked for by how much is already shown`() = runTest {
        server.serve(
            mapOf(
                CONVERSATIONS to listOf(
                    json(fixture("conversations-first-of-many.json")),
                    json(fixture("conversations-page-two.json"))
                )
            )
        )
        val viewModel = viewModel()
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            assertTrue((awaitItem() as Loadable.Loaded).value.hasMore)
            viewModel.loadMore()
            var state = awaitItem()
            while ((state as Loadable.Loaded).value.loadingMore) state = awaitItem()
            assertEquals(listOf("Adem Lane", "Mila Novak"), state.value.entries.map { it.partner.name })
            cancelAndIgnoreRemainingEvents()
        }
        val asked = List(server.requestCount) { server.takeRequest() }.last()
        assertEquals("1", asked.url.queryParameter("offset"))
    }

    private companion object {
        const val CONVERSATIONS = "/api/v1/community/conversations"
    }
}
