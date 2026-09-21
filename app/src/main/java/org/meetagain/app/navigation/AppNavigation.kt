package org.meetagain.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import java.time.Clock
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.meetagain.app.AppContainer
import org.meetagain.app.core.auth.SessionState
import org.meetagain.app.feature.about.AboutRoute
import org.meetagain.app.feature.applock.AppLockRoute
import org.meetagain.app.feature.applock.UnlockRoute
import org.meetagain.app.feature.attendees.AttendeesRoute
import org.meetagain.app.feature.conversation.ConversationRoute
import org.meetagain.app.feature.event.EventRoute
import org.meetagain.app.feature.explore.ExploreRoute
import org.meetagain.app.feature.group.GroupRoute
import org.meetagain.app.feature.home.HomeRoute
import org.meetagain.app.feature.me.MeRoute
import org.meetagain.app.feature.members.BlockedRoute
import org.meetagain.app.feature.members.GroupMembersRoute
import org.meetagain.app.feature.members.MemberRoute
import org.meetagain.app.feature.messages.MessagesRoute
import org.meetagain.app.feature.messages.ThreadRoute
import org.meetagain.app.feature.mygroups.MyGroupsRoute
import org.meetagain.app.feature.notifications.NotificationsRoute
import org.meetagain.app.feature.notificationsettings.NotificationSettingsRoute
import org.meetagain.app.feature.profile.ProfileRoute
import org.meetagain.app.feature.signin.SignInRoute

/**
 * Where the app starts is decided by whoever is signed in: their next meetings, or the sign-in screen. Signing in or
 * out builds a new back stack, so nothing of the one before is left behind it. A locked app shows the unlock screen
 * and nothing behind it.
 */
@Composable
fun AppNavigation(container: AppContainer, clock: Clock = Clock.systemUTC(), opening: NavKey? = null) {
    val session by container.auth.state.collectAsStateWithLifecycle()
    when (session) {
        // Nothing yet: reading the stored session takes a moment, and a spinner that flashes past says nothing.
        SessionState.Unknown -> Unit

        is SessionState.SignedOut -> key(false) { Destinations(container, false, clock, opening) }

        is SessionState.SignedIn -> key(true) { Destinations(container, true, clock, opening) }

        is SessionState.Locked -> UnlockRoute(container)
    }
}

/**
 * [opening] is the screen a tapped link asks for. It is put on top of the stack the app would have started with, so
 * going back from it lands where the app normally starts rather than closing it. A member who is not signed in gets
 * the sign-in screen and nothing behind it: the link is theirs to open again once they are in.
 */
@Composable
private fun Destinations(container: AppContainer, signedIn: Boolean, clock: Clock, opening: NavKey?) {
    val backStack = rememberNavBackStack(if (signedIn) Home else SignIn)
    val back: () -> Unit = { backStack.removeLastOrNull() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(opening) {
        if (opening != null && signedIn && backStack.lastOrNull() != opening) backStack.add(opening)
    }
    // Switching roots drops whatever was above the one being left, and leaves Meetings underneath the other two,
    // so system back from Messages or Groups lands there before it leaves the app.
    val selectRoot: (Root) -> Unit = { root ->
        backStack.clear()
        backStack.add(Home)
        rootKey(root)?.let(backStack::add)
    }
    val unread = unreadMessages(container, signedIn)
    val bar: (Root) -> @Composable () -> Unit = { root ->
        { AppNavigationBar(root, unread, selectRoot) }
    }
    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<SignIn> {
                SignInRoute(
                    container,
                    onLookAround = { backStack.add(Explore) },
                    onOpenAbout = { backStack.add(About) }
                )
            }
            entry<Home> {
                HomeRoute(
                    container,
                    onOpenEvent = { backStack.add(EventDetail(it.id)) },
                    onOpenMe = { backStack.add(Me) },
                    onOpenMyGroups = { selectRoot(Root.Groups) },
                    onLookAround = { backStack.add(Explore) },
                    clock = clock,
                    bottomBar = bar(Root.Meetings)
                )
            }
            entry<Messages> {
                MessagesRoute(
                    container,
                    onOpenThread = { backStack.add(Thread(it.partner.id)) },
                    onOpenMe = { backStack.add(Me) },
                    bottomBar = bar(Root.Messages)
                )
            }
            entry<Me> {
                MeRoute(
                    container,
                    onBack = back,
                    onOpenMyGroups = { selectRoot(Root.Groups) },
                    onOpenProfile = { backStack.add(MyProfile) },
                    onOpenNotifications = { backStack.add(Notifications) },
                    onOpenNotificationSettings = { backStack.add(NotificationSettings) },
                    onOpenAppLock = { backStack.add(AppLock) },
                    onOpenBlocked = { backStack.add(Blocked) },
                    onOpenAbout = { backStack.add(About) },
                    onSignOut = { scope.launch { container.auth.signOut() } }
                )
            }
            entry<Notifications> {
                NotificationsRoute(
                    container,
                    onBack = back,
                    onOpen = { notification ->
                        // The app opens what it has a screen for; the rest belongs on the website.
                        val destination = destinationOf(notification.webUrl, container.appInfo.linkHost)
                        destination?.let(backStack::add)
                        destination != null
                    }
                )
            }
            entry<NotificationSettings> { NotificationSettingsRoute(container, onBack = back) }
            entry<AppLock> { AppLockRoute(container, onBack = back) }
            entry<MyGroups> {
                MyGroupsRoute(
                    container,
                    onOpenGroup = { backStack.add(GroupPage(it)) },
                    onOpenMe = { backStack.add(Me) },
                    bottomBar = bar(Root.Groups)
                )
            }
            entry<MyProfile> { ProfileRoute(container, onBack = back) }
            entry<About> { AboutRoute(container, onBack = back) }
            entry<Explore> {
                ExploreRoute(
                    container,
                    onBack = back,
                    onOpenEvent = { backStack.add(EventDetail(it.id)) },
                    onOpenGroup = { backStack.add(GroupPage(it.slug)) }
                )
            }
            entry<EventDetail> { key ->
                EventRoute(
                    container,
                    key.id,
                    onBack = back,
                    onOpenGroup = { backStack.add(GroupPage(it)) },
                    onOpenAttendees = { backStack.add(Attendees(key.id)) },
                    onOpenConversation = { backStack.add(Conversation(key.id)) },
                    clock = clock
                )
            }
            entry<Attendees> { key ->
                AttendeesRoute(container, key.id, onBack = back, onOpenMember = { backStack.add(MemberPage(it)) })
            }
            entry<Thread> { key ->
                ThreadRoute(
                    container,
                    key.partnerId,
                    onBack = back,
                    onOpenMember = { backStack.add(MemberPage(it)) }
                )
            }
            entry<MemberPage> { key ->
                MemberRoute(
                    container,
                    key.id,
                    onBack = back,
                    onOpenThread = { backStack.add(Thread(it)) }
                )
            }
            entry<GroupMembers> { key ->
                GroupMembersRoute(container, key.slug, onBack = back, onOpenMember = { backStack.add(MemberPage(it)) })
            }
            entry<Blocked> {
                BlockedRoute(container, onBack = back, onOpenMember = { backStack.add(MemberPage(it)) })
            }
            entry<Conversation> { key -> ConversationRoute(container, key.id, onBack = back) }
            entry<GroupPage> { key ->
                GroupRoute(
                    container,
                    key.slug,
                    onBack = back,
                    onOpenEvent = { backStack.add(EventDetail(it.id)) },
                    onOpenMembers = { backStack.add(GroupMembers(key.slug)) }
                )
            }
        }
    )
}

private fun rootKey(root: Root): NavKey? = when (root) {
    Root.Meetings -> null
    Root.Messages -> Messages
    Root.Groups -> MyGroups
}

/**
 * A plain dot on the Messages destination, from the stored inbox alone: the bar never fetches, so opening the app
 * costs no call it would not have made.
 */
@Composable
private fun unreadMessages(container: AppContainer, signedIn: Boolean): Boolean {
    if (!signedIn) return false
    val unread = remember(container) {
        container.memberRepository.inbox().map { cached ->
            cached?.value?.entries.orEmpty().any { it.unread > 0 }
        }
    }
    return unread.collectAsStateWithLifecycle(false).value
}
