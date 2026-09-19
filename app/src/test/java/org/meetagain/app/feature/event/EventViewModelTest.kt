package org.meetagain.app.feature.event

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
import org.meetagain.app.testing.MainDispatcherRule
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.publicRepository
import org.meetagain.app.testing.serve

class EventViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    @Test
    fun `the event loads`() = runTest {
        server.serve(mapOf("/api/v1/events/117" to listOf(json(fixture("event-detail.json")))))
        EventViewModel(publicRepository(server), 117).state.test {
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
        val viewModel = EventViewModel(publicRepository(server), 117)
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(Loadable.Failed(ApiError.Http(404, "Not found")), awaitItem())
            viewModel.load()
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(117, (awaitItem() as Loadable.Loaded).value.event.id)
        }
    }
}
