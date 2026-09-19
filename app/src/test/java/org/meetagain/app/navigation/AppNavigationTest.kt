package org.meetagain.app.navigation

import android.app.Application
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meetagain.app.AppContainer
import org.meetagain.app.AppInfo
import org.meetagain.app.R
import org.meetagain.app.core.ui.theme.MeetAgainTheme
import org.meetagain.app.testing.onAllNodesWithTextExists
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class AppNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = MockWebServer()
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        server.start()
        compose.enableAccessibilityChecks()
        val container = AppContainer(AppInfo("0.1.0", testBuild = false, server.url("/").toString().trimEnd('/')))
        compose.setContent { MeetAgainTheme { AppNavigation(container) } }
    }

    @After
    fun tearDown() = server.close()

    private fun text(id: Int) = context.getString(id)

    @Test
    fun `about opens from the start screen and back returns`() {
        server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())
        compose.onNodeWithText(text(R.string.about_title)).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextExists(text(R.string.about_server_reachable))
        }
        compose.onNodeWithContentDescription(text(R.string.navigate_back)).performClick()
        compose.onNodeWithText(text(R.string.start_tagline)).assertExists()
    }

    @Test
    fun `a failed server check can be retried`() {
        server.enqueue(MockResponse.Builder().code(500).build())
        server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())
        compose.onNodeWithText(text(R.string.about_title)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(text(R.string.retry)) }
        compose.onNodeWithText(text(R.string.retry)).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextExists(text(R.string.about_server_reachable))
        }
    }
}
