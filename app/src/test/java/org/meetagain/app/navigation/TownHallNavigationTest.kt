package org.meetagain.app.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
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

/** Town Hall is in the bar only while one of the member's groups opens it, and goes where there is one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class TownHallNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val server = MockWebServer()
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun text(id: Int) = context.getString(id)

    private fun start(vararg memberships: String, topics: MockResponse = json(fixture("town-hall-topics.json"))) {
        server.serve(
            mapOf(
                "/api/v1/me/events" to listOf(json(fixture("me-events.json"))),
                "/api/v1/me/groups" to memberships.map { json(fixture(it)) },
                "/api/v1/memberships/invitations" to listOf(json(fixture("invitations.json"))),
                "/api/v1/community/conversations" to listOf(json(fixture("conversations.json"))),
                "$WEIQI/topics" to listOf(topics),
                "$WEIQI/gallery" to listOf(json(fixture("town-hall-gallery.json")))
            )
        )
        val store = testSessionStore(context)
        runBlocking { store.write(Session(4, "Crystal Liu", "mapat_test", setOf(Session.ME_READ))) }
        compose.enableAccessibilityChecks()
        val container = testContainer(context, server, sessionStore = store)
        compose.setContent { MeetAgainTheme { AppNavigation(container) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(text(R.string.home_title)) }
    }

    @Test
    fun `a member without a Town Hall has no such destination`() {
        start("me-groups.json")
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(text(R.string.nav_groups)) }
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.nav_town_hall)).assertDoesNotExist()
    }

    @Test
    fun `one group's Town Hall opens straight from the bar`() {
        start("me-groups-town-hall.json")
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(text(R.string.nav_town_hall)) }
        compose.onNodeWithText(text(R.string.nav_town_hall)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Game nights") }
        compose.onNodeWithText("Weiqi Club").assertExists()
    }

    @Test
    fun `several Town Halls are listed first`() {
        start("me-groups-town-halls.json")
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(text(R.string.nav_town_hall)) }
        compose.onNodeWithText(text(R.string.nav_town_hall)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Dragon Descendants") }
        compose.onNodeWithText("Weiqi Club").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Game nights") }
    }

    /** The Town Hall closed while the member was away: its 404 asks the memberships again, and the bar follows. */
    @Test
    fun `a Town Hall that closes takes its destination with it`() {
        start(
            "me-groups-town-hall.json",
            "me-groups.json",
            topics = json(fixture("error-not-found.json"), 404)
        )
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(text(R.string.nav_town_hall)) }
        compose.onNodeWithText(text(R.string.nav_town_hall)).performClick()
        compose.waitUntil(5_000) { !compose.onAllNodesWithTextExists(text(R.string.nav_town_hall)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(text(R.string.home_title)) }
    }

    private companion object {
        const val WEIQI = "/api/v1/community/groups/weiqi-club/town-hall"
    }
}
