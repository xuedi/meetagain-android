package org.meetagain.app.feature.explore

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

class ExploreViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    @Test
    fun `events and groups load together`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/events" to listOf(json(fixture("events.json"))),
                "/api/v1/groups" to listOf(json(fixture("groups.json")))
            )
        )
        ExploreViewModel(publicRepository(server)).state.test {
            assertEquals(ExploreUiState(), awaitItem())
            var state = awaitItem()
            while (state.events is Loadable.Loading || state.groups is Loadable.Loading) state = awaitItem()
            assertEquals(4, (state.events as Loadable.Loaded).value.events.size)
            assertEquals(4, (state.groups as Loadable.Loaded).value.size)
        }
    }

    @Test
    fun `a refresh keeps the list on screen until the new one arrives`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/events" to listOf(json(fixture("events.json"))),
                "/api/v1/groups" to listOf(json("", status = 500), json(fixture("groups.json")))
            )
        )
        val viewModel = ExploreViewModel(publicRepository(server))
        viewModel.state.test {
            var state = awaitItem()
            while (state.events is Loadable.Loading || state.groups is Loadable.Loading) state = awaitItem()
            assertEquals(Loadable.Failed(ApiError.Http(500)), state.groups)

            viewModel.loadEvents()
            val refreshing = awaitItem().events as Loadable.Loaded
            assertTrue(refreshing.refreshing)
            assertEquals(false, (awaitItem().events as Loadable.Loaded).refreshing)

            viewModel.loadGroups()
            assertEquals(Loadable.Loading, awaitItem().groups)
            assertEquals(4, (awaitItem().groups as Loadable.Loaded).value.size)
        }
    }
}
