package org.meetagain.app.feature.townhall

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
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.testing.MainDispatcherRule
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.serve
import org.meetagain.app.testing.townHallRepository

class GalleryViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun viewModel() = mainDispatcher.keep(GalleryViewModel(townHallRepository(server, answers), SLUG))

    @Test
    fun `the gallery shows the group's photos newest first, each with its meeting`() = runTest {
        server.serve(mapOf(GALLERY to listOf(json(fixture("town-hall-gallery.json")))))
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val page = (awaitItem() as Loadable.Loaded).value
            assertEquals(listOf(52, 51, 44), page.photos.map { it.id })
            assertEquals("Weekly Go Study Group", page.photos.first().eventTitle)
            assertEquals(131, page.photos.first().eventId)
            assertTrue(page.photos.first().thumbnailUrl.endsWith("_350x263.webp"))
            assertFalse(page.hasMore)
        }
    }

    @Test
    fun `more photos are asked for from where the loaded ones end`() = runTest {
        val first = fixture("town-hall-gallery.json").replace("\"total\": 3", "\"total\": 4")
        val second = """{"items":[{"id":30,"url":"https://meetagain.org/images/thumbnails/f_1024x768.webp",""" +
            """"urls":{},"mine":false,"uploadedAt":null,"event":{"id":120,"title":"Picnic"}}],""" +
            """"total":4,"limit":30,"offset":3}"""
        server.serve(mapOf(GALLERY to listOf(json(first), json(second))))
        val viewModel = viewModel()
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            assertTrue((awaitItem() as Loadable.Loaded).value.hasMore)
            viewModel.loadMore()
            var page = (awaitItem() as Loadable.Loaded).value
            while (page.loadingMore || page.photos.size < 4) page = (awaitItem() as Loadable.Loaded).value
            assertEquals(listOf(52, 51, 44, 30), page.photos.map { it.id })
            assertFalse(page.hasMore)
        }
        val asked = List(server.requestCount) { server.takeRequest() }.last()
        assertEquals("3", asked.url.queryParameter("offset"))
    }

    private companion object {
        const val SLUG = "weiqi-club"
        const val GALLERY = "/api/v1/community/groups/$SLUG/town-hall/gallery"
    }
}
