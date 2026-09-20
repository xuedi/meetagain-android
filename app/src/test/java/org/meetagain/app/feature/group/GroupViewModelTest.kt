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
import org.meetagain.app.testing.MemoryAnswers
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
    fun `a group the server does not show is not found`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/groups/movienight" to listOf(json(fixture("error-not-found.json"), 404)),
                "/api/v1/events" to listOf(json(fixture("events.json")))
            )
        )
        GroupViewModel(publicRepository(server), "movienight").state.test {
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(Loadable.Failed(ApiError.Http(404, "not_found")), awaitItem())
        }
    }

    @Test
    fun `a stored group that is gone is not found`() = runTest {
        val answers = MemoryAnswers()
        server.serve(
            mapOf(
                "/api/v1/groups/my-community" to
                    listOf(json(fixture("group-detail.json")), json(fixture("error-not-found.json"), 404)),
                "/api/v1/events" to listOf(json(fixture("events.json")))
            )
        )
        val repository = publicRepository(server, answers)
        repository.refreshGroup("my-community")
        repository.refreshUpcomingEvents(group = "my-community")

        GroupViewModel(repository, "my-community").state.test {
            var state = awaitItem()
            while (state !is Loadable.Failed) state = awaitItem()
            assertEquals(404, (state.error as ApiError.Http).status)
        }
        assertEquals(setOf("events?group=my-community" to "en"), answers.rows.value.keys)
    }
}
