package org.meetagain.app.feature.event

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meetagain.app.AppContainer
import org.meetagain.app.AppInfo
import org.meetagain.app.R
import org.meetagain.app.core.ui.theme.MeetAgainTheme
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.onAllNodesWithTextExists
import org.meetagain.app.testing.serve
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class EventCalendarTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = MockWebServer()
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        server.start()
        server.serve(mapOf("/api/v1/events/117" to listOf(json(fixture("event-detail.json")))))
        compose.enableAccessibilityChecks()
        val container = AppContainer(AppInfo("0.1.0", testBuild = false, server.url("/").toString().trimEnd('/')))
        compose.setContent { MeetAgainTheme { EventRoute(container, 117, onBack = {}) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(context.getString(R.string.event_open_map)) }
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `the calendar app gets the event`() {
        compose.onNodeWithContentDescription(context.getString(R.string.calendar_add)).performClick()
        val started = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_INSERT, started?.action)
    }

    @Test
    fun `without a calendar app the member is told so`() {
        shadowOf(context).checkActivities(true)
        compose.onNodeWithContentDescription(context.getString(R.string.calendar_add)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(context.getString(R.string.calendar_no_app)) }
    }
}
