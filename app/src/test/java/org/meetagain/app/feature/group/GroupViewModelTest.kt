package org.meetagain.app.feature.group

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

class GroupViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    @Test
    fun `the group loads with its events`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/groups/my-community" to listOf(json(fixture("group-detail.json"))),
                "/api/v1/events" to listOf(json(fixture("events.json")))
            )
        )
        GroupViewModel(publicRepository(server), "my-community").state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val page = (awaitItem() as Loadable.Loaded).value
            assertEquals("Dragon Descendants", page.details.group.name)
            assertEquals(4, page.upcoming.events.size)
        }
        val paths = List(server.requestCount) { server.takeRequest().url }
        assertEquals("my-community", paths.single { it.encodedPath == "/api/v1/events" }.queryParameter("group"))
    }

    @Test
    fun `a group that is not public is not found`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/groups/movienight" to
                    listOf(json(fixture("group-detail.json").replace("\"public\"", "\"private\""))),
                "/api/v1/events" to listOf(json(fixture("events.json")))
            )
        )
        GroupViewModel(publicRepository(server), "movienight").state.test {
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(Loadable.Failed(ApiError.Http(404)), awaitItem())
        }
    }
}
