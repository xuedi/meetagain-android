package org.meetagain.app.feature.notifications

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.Stale
import org.meetagain.app.testing.MainDispatcherRule
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.serve
import org.meetagain.app.testing.testClock

class NotificationsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun viewModel() = mainDispatcher.keep(NotificationsViewModel(memberRepository(server, answers)))

    @Test
    fun `the bell shows what is waiting, worded by the server`() = runTest {
        server.serve(mapOf("/api/v1/me/notifications" to listOf(json(fixture("notifications.json")))))
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val items = (awaitItem() as Loadable.Loaded).value
            assertEquals(3, items.size)
            assertEquals("3 unread messages", items.first().text)
        }
    }

    @Test
    fun `a member with nothing waiting sees an empty list, not an error`() = runTest {
        server.serve(mapOf("/api/v1/me/notifications" to listOf(json(fixture("notifications-empty.json")))))
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(emptyList<Any>(), (awaitItem() as Loadable.Loaded).value)
        }
    }

    @Test
    fun `with nothing stored and no connection the screen fails`() = runTest {
        server.close()
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(ApiError.Offline, (awaitItem() as Loadable.Failed).error)
        }
    }

    /** What was stored stays readable, with the strip above it saying how old it is. */
    @Test
    fun `a failed refresh keeps the stored bell and marks it stale`() = runTest {
        server.serve(mapOf("/api/v1/me/notifications" to listOf(json(fixture("notifications.json")))))
        memberRepository(server, answers).refreshNotifications()
        server.close()
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            var state = awaitItem() as Loadable.Loaded
            if (state.stale == null) state = awaitItem() as Loadable.Loaded
            assertEquals(3, state.value.size)
            assertEquals(Stale(testClock.instant(), ApiError.Offline), state.stale)
        }
    }
}
