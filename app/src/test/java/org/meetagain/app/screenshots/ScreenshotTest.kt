package org.meetagain.app.screenshots

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meetagain.app.AppInfo
import org.meetagain.app.R
import org.meetagain.app.core.data.MemberProfile
import org.meetagain.app.core.data.Notification
import org.meetagain.app.core.data.NotificationSettings
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.push.PushInterval
import org.meetagain.app.core.push.PushObstacle
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.feature.about.AboutScreen
import org.meetagain.app.feature.about.AboutUiState
import org.meetagain.app.feature.about.ServerCheck
import org.meetagain.app.feature.attendees.AttendeesScreen
import org.meetagain.app.feature.conversation.ConversationScreen
import org.meetagain.app.feature.event.EventScreen
import org.meetagain.app.feature.explore.ExploreScreen
import org.meetagain.app.feature.explore.ExploreTab
import org.meetagain.app.feature.explore.ExploreUiState
import org.meetagain.app.feature.group.GroupPage
import org.meetagain.app.feature.group.GroupScreen
import org.meetagain.app.feature.home.Home
import org.meetagain.app.feature.home.HomeScreen
import org.meetagain.app.feature.me.MeScreen
import org.meetagain.app.feature.members.MemberList
import org.meetagain.app.feature.members.MemberScreen
import org.meetagain.app.feature.members.MembersScreen
import org.meetagain.app.feature.messages.Conversations
import org.meetagain.app.feature.messages.Draft
import org.meetagain.app.feature.messages.MessagesScreen
import org.meetagain.app.feature.messages.Thread as ThreadPage
import org.meetagain.app.feature.messages.ThreadScreen
import org.meetagain.app.feature.mygroups.MyGroupsScreen
import org.meetagain.app.feature.notifications.NotificationsScreen
import org.meetagain.app.feature.notificationsettings.NotificationSettingsScreen
import org.meetagain.app.feature.notificationsettings.PushUiState
import org.meetagain.app.feature.profile.ProfileScreen
import org.meetagain.app.feature.signin.SignInProblem
import org.meetagain.app.feature.signin.SignInScreen
import org.meetagain.app.feature.signin.SignInState
import org.meetagain.app.navigation.AppNavigationBar
import org.meetagain.app.navigation.Root
import org.meetagain.app.testing.DeviceSettings
import org.meetagain.app.testing.Samples
import org.meetagain.app.testing.testClock
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
    fun signIn() = capture("signin") { SignIn(SignInState()) }

    @Test
    fun signInRefused() = capture("signin_refused") {
        SignIn(SignInState(email = "crystal.liu@example.org", problem = SignInProblem.WrongCredentials))
    }

    @Test
    fun signInPending() = capture("signin_pending") {
        SignIn(SignInState(email = "crystal.liu@example.org", problem = SignInProblem.PendingApproval))
    }

    @Test
    fun home() = capture("home") { Home(Loadable.Loaded(Samples.home)) }

    @Test
    fun homeEmpty() = capture("home_empty") {
        Home(Loadable.Loaded(Home(next = null, later = emptyList(), beyondWindow = false)))
    }

    @Test
    fun homeStale() = capture("home_stale") {
        Home(Loadable.Loaded(Samples.home, stale = Samples.offlineSince))
    }

    @Test
    fun me() = capture("me") {
        MeScreen(
            name = "Crystal Liu",
            onBack = {},
            onOpenMyGroups = {},
            onOpenProfile = {},
            onOpenNotifications = {},
            onOpenNotificationSettings = {},
            onOpenBlocked = {},
            onOpenAbout = {},
            onSignOut = {},
            onDeleteAccount = {}
        )
    }

    @Test
    fun attendees() = capture("attendees") {
        AttendeesScreen(Loadable.Loaded(Samples.attendees), onBack = {}, onRetry = {}, onOpenMember = {})
    }

    @Test
    fun conversation() = capture("conversation") {
        ConversationScreen(
            state = Loadable.Loaded(Samples.conversation),
            photos = Samples.photos,
            draft = "",
            busy = false,
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onRetry = {},
            onDraft = {},
            onSend = {},
            onLoadOlder = {},
            onDeleteComment = {},
            onUndoDeleteComment = {},
            onDeletePhoto = {},
            onUndoDeletePhoto = {},
            onPickPhoto = {},
            onTakePhoto = {}
        )
    }

    @Test
    fun myGroups() = capture("mygroups") {
        MyGroupsScreen(
            state = Loadable.Loaded(Samples.myGroups),
            snackbarHostState = SnackbarHostState(),
            onRetry = {},
            onOpenGroup = {},
            onOpenMe = {},
            onAccept = {},
            onDecline = {},
            bottomBar = { AppNavigationBar(Root.Groups, unreadMessages = true, onSelect = {}) }
        )
    }

    // The community

    @Test
    fun messages() = capture("messages") { Messages(Loadable.Loaded(Samples.inbox)) }

    @Test
    fun messagesEmpty() = capture("messages_empty") {
        Messages(Loadable.Loaded(Conversations(entries = emptyList(), total = 0)))
    }

    @Test
    fun messagesStale() = capture("messages_stale") {
        Messages(Loadable.Loaded(Samples.inbox, stale = Samples.offlineSince))
    }

    @Test
    fun messagesOffline() = capture("messages_offline") { Messages(Loadable.Failed(ApiError.Offline)) }

    @Test
    fun thread() = capture("thread") { Thread(Loadable.Loaded(Samples.thread)) }

    @Test
    fun threadEditing() = capture("thread_editing") {
        Thread(Loadable.Loaded(Samples.thread), Draft("Yes, I will be there.", editing = 33))
    }

    /** A block in either direction: the thread reads, and a sentence stands where the composer was. */
    @Test
    fun threadBlocked() = capture("thread_blocked") { Thread(Loadable.Loaded(Samples.blockedThread)) }

    @Test
    fun member() = capture("member") { Member(Loadable.Loaded(Samples.member)) }

    @Test
    fun memberBlockedByMe() = capture("member_blocked_by_me") { Member(Loadable.Loaded(Samples.blockedMember)) }

    /** The other member has blocked the caller: one sentence, and nothing to do. */
    @Test
    fun memberRefused() = capture("member_refused") {
        Member(Loadable.Failed(ApiError.Http(403, code = "forbidden")))
    }

    @Test
    fun groupMembers() = capture("group_members") {
        Members(
            Loadable.Loaded(Samples.groupMembers),
            R.string.group_members_title,
            R.string.group_members_empty,
            R.string.group_members_end
        )
    }

    @Test
    fun blockedMembers() = capture("blocked_members") {
        Members(
            Loadable.Loaded(Samples.blockedMembers),
            R.string.me_blocked,
            R.string.blocked_empty,
            R.string.blocked_end
        )
    }

    @Composable
    private fun Members(state: Loadable<MemberList>, title: Int, empty: Int, end: Int) = MembersScreen(
        state = state,
        title = stringResource(title),
        empty = stringResource(empty),
        end = stringResource(end),
        onBack = {},
        onRetry = {},
        onOpenMember = {},
        onLoadMore = {}
    )

    @Composable
    private fun Messages(state: Loadable<Conversations>) = MessagesScreen(
        state = state,
        snackbarHostState = SnackbarHostState(),
        onRetry = {},
        onOpenThread = {},
        onOpenMe = {},
        onLoadMore = {},
        bottomBar = { AppNavigationBar(Root.Messages, unreadMessages = true, onSelect = {}) }
    )

    @Composable
    private fun Thread(state: Loadable<ThreadPage>, draft: Draft = Draft()) = ThreadScreen(
        state = state,
        draft = draft,
        busy = false,
        snackbarHostState = SnackbarHostState(),
        onBack = {},
        onRetry = {},
        onDraft = {},
        onSend = {},
        onEdit = {},
        onCancelEdit = {},
        onLoadEarlier = {},
        onBlock = {},
        onOpenMember = {}
    )

    @Composable
    private fun Member(state: Loadable<MemberProfile>) = MemberScreen(
        state = state,
        busy = false,
        snackbarHostState = SnackbarHostState(),
        onBack = {},
        onRetry = {},
        onOpenThread = {},
        onToggleFollow = {},
        onToggleBlock = {}
    )

    @Test
    fun notifications() = capture("notifications") {
        Notifications(Loadable.Loaded(Samples.notifications))
    }

    @Test
    fun notificationsEmpty() = capture("notifications_empty") { Notifications(Loadable.Loaded(emptyList())) }

    @Test
    fun notificationsStale() = capture("notifications_stale") {
        Notifications(Loadable.Loaded(Samples.notifications, stale = Samples.offlineSince))
    }

    @Test
    fun notificationSettings() = capture("notification_settings") {
        Settings(Loadable.Loaded(Samples.notificationSettings))
    }

    /** With no push app installed, the screen says why news arrives later rather than hiding it. */
    @Test
    fun notificationSettingsNoDistributor() = capture("notification_settings_no_distributor") {
        Settings(
            Loadable.Loaded(Samples.pushOnSettings),
            PushUiState(obstacle = PushObstacle.NoDistributor, interval = PushInterval.Hourly)
        )
    }

    @Test
    fun notificationSettingsPushOn() = capture("notification_settings_push_on") {
        Settings(Loadable.Loaded(Samples.pushOnSettings))
    }

    /** With the master switch off the six stay visible and say why they cannot be changed. */
    @Test
    fun notificationSettingsOff() = capture("notification_settings_off") {
        Settings(Loadable.Loaded(Samples.notificationSettings.copy(master = false)))
    }

    @Test
    fun profile() = capture("profile") {
        ProfileScreen(
            state = Loadable.Loaded(Samples.profile),
            edit = null,
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onRetry = {},
            onName = {},
            onBio = {},
            onLanguage = {},
            onPublic = {},
            onSave = {},
            onPickAvatar = {},
            onTakeAvatar = {},
            onOpenWebsite = {}
        )
    }

    @Composable
    private fun SignIn(state: SignInState) = SignInScreen(
        state = state,
        onEmail = {},
        onPassword = {},
        onSubmit = {},
        onOpenWebsite = {},
        onLookAround = {},
        onOpenAbout = {}
    )

    @Composable
    private fun Notifications(state: Loadable<List<Notification>>) =
        NotificationsScreen(state = state, onBack = {}, onRetry = {}, onOpen = {})

    @Composable
    private fun Settings(state: Loadable<NotificationSettings>, push: PushUiState = PushUiState()) =
        NotificationSettingsScreen(state = state, push = push, onBack = {}, onRetry = {}, onSet = { _, _ -> })

    @Composable
    private fun Home(state: Loadable<Home>) = HomeScreen(
        state = state,
        joined = setOf("my-community"),
        snackbarHostState = SnackbarHostState(),
        onRetry = {},
        onOpenEvent = {},
        onOpenMe = {},
        onOpenMyGroups = {},
        onLookAround = {},
        onAnswer = { _, _, _ -> },
        clock = testClock,
        bottomBar = { AppNavigationBar(Root.Meetings, unreadMessages = true, onSelect = {}) }
    )

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
