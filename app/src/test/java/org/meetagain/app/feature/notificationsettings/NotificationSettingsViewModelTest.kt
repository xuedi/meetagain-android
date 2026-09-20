package org.meetagain.app.feature.notificationsettings

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
import org.meetagain.app.core.data.NotificationSetting
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.testing.MainDispatcherRule
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.serve

class NotificationSettingsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun viewModel() = mainDispatcher.keep(NotificationSettingsViewModel(memberRepository(server, answers)))

    @Test
    fun `the switches come back as the member left them`() = runTest {
        server.serve(mapOf("/api/v1/me/notification-settings" to listOf(json(fixture("notification-settings.json")))))
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val settings = (awaitItem() as Loadable.Loaded).value
            assertTrue(settings.master)
            assertFalse(settings.upcomingEvents)
        }
    }

    /** Only the key that changed is sent, so the push settings this screen cannot show are never written back. */
    @Test
    fun `turning a switch sends that key alone`() = runTest {
        server.serve(mapOf("/api/v1/me/notification-settings" to listOf(json(fixture("notification-settings.json")))))
        val viewModel = viewModel()
        viewModel.state.test { skipItems(2) }
        viewModel.set(NotificationSetting.UpcomingEvents, value = true)
        viewModel.pending.test { if (awaitItem().isNotEmpty()) assertTrue(awaitItem().isEmpty()) }
        val bodies = generateSequence { server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS) }
            .take(2)
            .mapNotNull { it.body?.utf8() }
            .toList()
        assertTrue(bodies.contains("""{"upcomingEvents":true}"""))
    }

    /** The stored answer never moved, so the switch is back where it was and the member is told. */
    @Test
    fun `a refused change is reported and the switch stays put`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/me/notification-settings" to listOf(
                    json(fixture("notification-settings.json")),
                    json(fixture("error-validation.json"), status = 422)
                )
            )
        )
        val viewModel = viewModel()
        viewModel.state.test { skipItems(2) }
        viewModel.set(NotificationSetting.Announcements, value = false)
        viewModel.failed.test {
            if (!awaitItem()) assertTrue(awaitItem())
        }
        assertTrue((viewModel.state.value as Loadable.Loaded).value.announcements)
    }
}
