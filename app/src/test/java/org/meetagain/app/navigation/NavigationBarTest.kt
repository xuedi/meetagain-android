package org.meetagain.app.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meetagain.app.R
import org.meetagain.app.core.auth.Session
import org.meetagain.app.core.ui.theme.MeetAgainTheme
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.onAllNodesWithTextExists
import org.meetagain.app.testing.serve
import org.meetagain.app.testing.testContainer
import org.meetagain.app.testing.testSessionStore
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The three roots, and what system back does from each of them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class NavigationBarTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val server = MockWebServer()
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        server.start()
        server.serve(
            mapOf(
                "/api/v1/me/events" to listOf(json(fixture("me-events.json"))),
                "/api/v1/me/groups" to listOf(json(fixture("me-groups.json"))),
                "/api/v1/memberships/invitations" to listOf(json(fixture("invitations.json"))),
                "/api/v1/community/conversations" to listOf(json(fixture("conversations.json")))
            )
        )
        val store = testSessionStore(context)
        runBlocking { store.write(Session(4, "Crystal Liu", "mapat_test", setOf(Session.ME_READ))) }
        compose.enableAccessibilityChecks()
        val container = testContainer(context, server, sessionStore = store)
        compose.setContent { MeetAgainTheme { AppNavigation(container) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(text(R.string.home_title)) }
    }

    @After
    fun tearDown() = server.close()

    private fun text(id: Int) = context.getString(id)

    private fun back() = compose.activity.runOnUiThread {
        compose.activity.onBackPressedDispatcher.onBackPressed()
    }

    @Test
    fun `the bar walks the three roots and back lands on meetings`() {
        compose.onNodeWithText(text(R.string.nav_messages)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(text(R.string.messages_title)) }

        compose.onNodeWithText(text(R.string.nav_groups)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(text(R.string.mygroups_memberships)) }

        // Groups is a root now, so it carries no back arrow of its own.
        compose.onNodeWithContentDescription(text(R.string.navigate_back)).assertDoesNotExist()

        back()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.home_title)).assertExists()
    }

    /** What is above a root is dropped when the bar leaves it, so a switch never stacks screens up. */
    @Test
    fun `the person icon opens Me above a root and back returns to that root`() {
        compose.onNodeWithText(text(R.string.nav_messages)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(text(R.string.messages_title)) }

        compose.onNodeWithContentDescription(text(R.string.me_title)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(text(R.string.me_sign_out)) }

        back()
        // The title and the bar's own label read the same, so the inbox is recognised by its list end.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextExists(text(R.string.messages_end), useUnmergedTree = true)
        }
    }
}
