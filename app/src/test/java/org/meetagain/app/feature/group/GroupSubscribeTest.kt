package org.meetagain.app.feature.group

import android.app.Application
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class GroupSubscribeTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = MockWebServer()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val feed = "https://dragon-descendants.de/en/events.ics"

    @Before
    fun setUp() {
        server.start()
        server.serve(
            mapOf(
                "/api/v1/groups/my-community" to listOf(json(fixture("group-detail.json"))),
                "/api/v1/events" to listOf(json(fixture("events.json")))
            )
        )
        shadowOf(context).checkActivities(true)
        compose.enableAccessibilityChecks()
        val container = AppContainer(AppInfo("0.1.0", testBuild = false, server.url("/").toString().trimEnd('/')))
        compose.setContent {
            MeetAgainTheme { GroupRoute(container, "my-community", onBack = {}, onOpenEvent = {}) }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(text(R.string.calendar_subscribe)) }
    }

    @After
    fun tearDown() = server.close()

    private fun text(id: Int) = context.getString(id)

    @Test
    fun `without a subscription app the address is offered to copy`() {
        compose.onNodeWithText(text(R.string.calendar_subscribe)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.calendar_subscribe_no_app)).assertExists()
        compose.onNodeWithText(feed).assertExists()

        compose.onNodeWithText(text(R.string.calendar_copy_address)).performClick()
        compose.waitForIdle()
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        assertEquals(feed, clipboard.primaryClip?.getItemAt(0)?.text)
        assertFalse(compose.onAllNodesWithTextExists(text(R.string.calendar_subscribe_no_app)))
    }

    @Test
    fun `a subscription app gets the webcal link and no sheet opens`() {
        val subscriber = ComponentName("org.example.subscriber", "org.example.subscriber.Subscribe")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(subscriber)
            addIntentFilterForActivity(
                subscriber,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("webcal")
                }
            )
        }
        compose.onNodeWithText(text(R.string.calendar_subscribe)).performScrollTo().performClick()
        compose.waitForIdle()

        val started = shadowOf(context).nextStartedActivity
        assertEquals("webcal://dragon-descendants.de/en/events.ics", started?.dataString)
        assertFalse(compose.onAllNodesWithTextExists(text(R.string.calendar_subscribe_no_app)))
    }
}
