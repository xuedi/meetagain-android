package org.meetagain.app.feature.home

import app.cash.turbine.test
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
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
import org.meetagain.app.core.data.Rsvp
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.feature.rsvp.RsvpMessage
import org.meetagain.app.feature.rsvp.RsvpRefusal
import org.meetagain.app.testing.MainDispatcherRule
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.serve
import org.meetagain.app.testing.testClock

class HomeViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun viewModel(clock: Clock = testClock) =
        mainDispatcher.keep(HomeViewModel(memberRepository(server, answers, clock = clock), clock))

    private fun events() = mapOf(
        "/api/v1/me/events" to listOf(json(fixture("me-events.json"))),
        "/api/v1/me/groups" to listOf(json(fixture("me-groups.json")))
    )

    @Test
    fun `the next meeting comes first and the rest follow`() = runTest {
        server.serve(events())
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val home = (awaitItem() as Loadable.Loaded).value
            assertEquals(73, home.next?.id)
            assertEquals(listOf(6, 58), home.later.map { it.id })
        }
    }

    @Test
    fun `only the next four weeks are shown`() = runTest {
        server.serve(events())
        // Four weeks from 27 August ends before the last of the three meetings in the fixture.
        val earlier = Clock.fixed(testClock.instant() - Duration.ofDays(23), ZoneOffset.UTC)
        viewModel(clock = earlier).state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val home = (awaitItem() as Loadable.Loaded).value
            assertEquals(73, home.next?.id)
            assertEquals(listOf(6), home.later.map { it.id })
            assertTrue(home.beyondWindow)
        }
    }

    @Test
    fun `the groups the member may answer in are the approved ones`() = runTest {
        server.serve(events())
        val viewModel = viewModel()
        viewModel.joined.test {
            assertNull(awaitItem())
            val joined = awaitItem()
            assertTrue(checkNotNull(joined).contains("weiqi-club"))
        }
    }

    @Test
    fun `an answer is saved and can be taken back`() = runTest {
        server.serve(
            events() + mapOf(
                "/api/v1/events/73/rsvp" to listOf(json(fixture("event-rsvp.json"))),
                "/api/v1/events/73" to listOf(json(fixture("event-detail.json"))),
                "/api/v1/events/73/attendees" to listOf(json(fixture("event-attendees.json")))
            )
        )
        val viewModel = viewModel()
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val next = checkNotNull((awaitItem() as Loadable.Loaded).value.next)
            assertEquals(Rsvp(going = false, guests = 0), next.mine)
            viewModel.rsvp.answer(next, going = true, guests = 2)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.rsvp.message.test {
            assertNull(awaitItem())
            val message = awaitItem() as RsvpMessage.Saved
            assertEquals(73, message.eventId)
            assertTrue(message.going)
            assertEquals(Rsvp(going = false, guests = 0), message.undoTo)
        }
    }

    @Test
    fun `a refusal is worded, not swallowed`() = runTest {
        server.serve(
            events() + mapOf(
                "/api/v1/events/73/rsvp" to listOf(json("""{"error":"event_canceled"}""", 409))
            )
        )
        val viewModel = viewModel()
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val next = checkNotNull((awaitItem() as Loadable.Loaded).value.next)
            viewModel.rsvp.answer(next, going = true)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.rsvp.message.test {
            assertNull(awaitItem())
            assertEquals(RsvpMessage.Refused(RsvpRefusal.Canceled), awaitItem())
        }
    }

    @Test
    fun `without a connection the home list is empty rather than wrong`() = runTest {
        server.close()
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            assertTrue(awaitItem() is Loadable.Failed)
        }
    }

    @Test
    fun `an ended meeting is not shown as coming up`() = runTest {
        server.serve(events())
        val later = Clock.fixed(testClock.instant() + Duration.ofDays(400), ZoneOffset.UTC)
        viewModel(clock = later).state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val home = (awaitItem() as Loadable.Loaded).value
            assertNull(home.next)
            assertFalse(home.beyondWindow)
        }
    }
}
