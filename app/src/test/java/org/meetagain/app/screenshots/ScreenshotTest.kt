package org.meetagain.app.screenshots

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meetagain.app.AppInfo
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.feature.about.AboutScreen
import org.meetagain.app.feature.about.AboutUiState
import org.meetagain.app.feature.about.ServerCheck
import org.meetagain.app.feature.event.EventScreen
import org.meetagain.app.feature.explore.ExploreScreen
import org.meetagain.app.feature.explore.ExploreTab
import org.meetagain.app.feature.explore.ExploreUiState
import org.meetagain.app.feature.group.GroupPage
import org.meetagain.app.feature.group.GroupScreen
import org.meetagain.app.feature.start.StartScreen
import org.meetagain.app.testing.DeviceSettings
import org.meetagain.app.testing.Samples
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Each screen in `en` and `zh`, light and dark, at font scale 1.0 and 2.0. Record with `just screenshots`. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class ScreenshotTest(private val language: String, private val dark: Boolean, private val fontScale: Float) {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(screen: String, content: @Composable () -> Unit) {
        compose.setContent {
            DeviceSettings(Locale.forLanguageTag(language), dark, fontScale, content)
        }
        val theme = if (dark) "dark" else "light"
        compose.onRoot().captureRoboImage("src/test/screenshots/${screen}_${language}_${theme}_$fontScale.png")
    }

    @Test
    fun start() = capture("start") { StartScreen(onLookAround = {}, onOpenAbout = {}) }

    @Test
    fun exploreEvents() = capture("explore_events") { Explore(ExploreUiState(Loadable.Loaded(Samples.upcoming))) }

    @Test
    fun exploreEventsIncomplete() = capture("explore_events_incomplete") {
        Explore(ExploreUiState(Loadable.Loaded(Samples.upcoming.copy(complete = false))))
    }

    @Test
    fun exploreGroups() = capture("explore_groups") {
        Explore(ExploreUiState(groups = Loadable.Loaded(Samples.groups)), ExploreTab.Groups)
    }

    @Test
    fun exploreEventsStale() = capture("explore_events_stale") {
        Explore(ExploreUiState(Loadable.Loaded(Samples.upcoming, stale = Samples.offlineSince)))
    }

    @Test
    fun exploreOffline() = capture("explore_offline") { Explore(ExploreUiState(Loadable.Failed(ApiError.Offline))) }

    @Test
    fun event() = capture("event") {
        EventScreen(
            Loadable.Loaded(Samples.eventDetails),
            onBack = {},
            onRetry = {},
            onAddToCalendar = {},
            onOpenMap = {},
            onOpenWebsite = {}
        )
    }

    @Test
    fun eventStale() = capture("event_stale") {
        EventScreen(
            Loadable.Loaded(Samples.eventDetails, stale = Samples.offlineSince),
            onBack = {},
            onRetry = {},
            onAddToCalendar = {},
            onOpenMap = {},
            onOpenWebsite = {}
        )
    }

    @Test
    fun group() = capture("group") { Group() }

    /** The sheet is a window of its own, so this captures the whole screen rather than the root. */
    @Test
    fun groupSubscribe() {
        compose.setContent {
            DeviceSettings(Locale.forLanguageTag(language), dark, fontScale) {
                Group(manualFeedUrl = "https://dragon-descendants.de/$language/events.ics")
            }
        }
        val theme = if (dark) "dark" else "light"
        captureScreenRoboImage("src/test/screenshots/group_subscribe_${language}_${theme}_$fontScale.png")
    }

    @Composable
    private fun Group(manualFeedUrl: String? = null) = GroupScreen(
        Loadable.Loaded(GroupPage(Samples.groupDetails, Samples.upcoming)),
        onBack = {},
        onRetry = {},
        onOpenEvent = {},
        onOpenWebsite = {},
        onSubscribe = {},
        manualFeedUrl = manualFeedUrl
    )

    @Composable
    private fun Explore(state: ExploreUiState, tab: ExploreTab = ExploreTab.Events) = ExploreScreen(
        state = state,
        onBack = {},
        onRefreshEvents = {},
        onRefreshGroups = {},
        onOpenEvent = {},
        onOpenGroup = {},
        onOpenAllEvents = {},
        initialTab = tab
    )

    @Test
    fun about() = capture("about") {
        AboutScreen(
            state = AboutUiState(
                AppInfo("0.1.0", testBuild = false, baseUrl = "https://meetagain.org"),
                ServerCheck.Reachable
            ),
            languageName = if (language == "zh") "中文" else "English",
            onBack = {},
            onRetry = {},
            onOpenLanguageSettings = {},
            onOpenLegalPage = {}
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_dark={1}_font={2}")
        fun parameters() = listOf("en", "zh").flatMap { language ->
            listOf(false, true).flatMap { dark -> listOf(1f, 2f).map { arrayOf<Any>(language, dark, it) } }
        }
    }
}
