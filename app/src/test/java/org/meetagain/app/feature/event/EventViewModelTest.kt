package org.meetagain.app.feature.event

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.PublicRepository
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.Stale
import org.meetagain.app.testing.MainDispatcherRule
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.publicRepository
import org.meetagain.app.testing.serve
import org.meetagain.app.testing.testClock

class EventViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun eventViewModel(
        repository: PublicRepository,
        member: MemberRepository = memberRepository(server),
        signedIn: Boolean = false,
        id: Int = 117
    ) = main.keep(EventViewModel(repository, member, signedIn, id))

    @Test
    fun `the event loads`() = runTest {
        server.serve(mapOf("/api/v1/events/117" to listOf(json(fixture("event-detail.json")))))
        eventViewModel(publicRepository(server), memberRepository(server), signedIn = false, id = 117).state.test {
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals("Travolta", (awaitItem() as Loadable.Loaded).value.location?.name)
        }
    }

    @Test
    fun `a missing event can be retried`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/events/117" to
                    listOf(json(fixture("error-not-found.json"), 404), json(fixture("event-detail.json")))
            )
        )
        val viewModel = eventViewModel(publicRepository(server), memberRepository(server), signedIn = false, id = 117)
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(Loadable.Failed(ApiError.Http(404, "not_found")), awaitItem())
            viewModel.load()
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(117, (awaitItem() as Loadable.Loaded).value.event.id)
        }
    }

    @Test
    fun `a stored event shows at once and the fresh one replaces it`() = runTest {
        val answers = MemoryAnswers()
        server.serve(
            mapOf(
                "/api/v1/events/117" to listOf(
                    json(fixture("event-detail.json")),
                    json(fixture("event-detail.json").replace("\"rsvpCount\": 1,", "\"rsvpCount\": 2,"))
                )
            )
        )
        publicRepository(server, answers).refreshEvent(117)

        eventViewModel(
            publicRepository(server, answers),
            memberRepository(server),
            signedIn = false,
            id = 117
        ).state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val stored = awaitItem() as Loadable.Loaded
            assertEquals(1, stored.value.event.going)
            assertEquals(null, stored.stale)
            assertEquals(2, (awaitItem() as Loadable.Loaded).value.event.going)
        }
    }

    @Test
    fun `without a connection the stored event shows with its age`() = runTest {
        val answers = MemoryAnswers()
        server.serve(mapOf("/api/v1/events/117" to listOf(json(fixture("event-detail.json")))))
        publicRepository(server, answers).refreshEvent(117)
        val repository = publicRepository(server, answers)
        server.close()

        eventViewModel(repository, memberRepository(server), signedIn = false, id = 117).state.test {
            assertEquals(Loadable.Loading, awaitItem())
            var state = awaitItem() as Loadable.Loaded
            if (state.stale == null) state = awaitItem() as Loadable.Loaded
            assertEquals(117, state.value.event.id)
            assertEquals(Stale(testClock.instant(), ApiError.Offline), state.stale)
        }
    }

    @Test
    fun `without a connection and nothing stored the error shows`() = runTest {
        val repository = publicRepository(server)
        server.close()
        eventViewModel(repository, memberRepository(server), signedIn = false, id = 117).state.test {
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(Loadable.Failed(ApiError.Offline), awaitItem())
        }
    }
}
