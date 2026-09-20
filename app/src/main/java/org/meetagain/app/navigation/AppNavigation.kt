package org.meetagain.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import java.time.Clock
import kotlinx.coroutines.launch
import org.meetagain.app.AppContainer
import org.meetagain.app.core.auth.SessionState
import org.meetagain.app.feature.about.AboutRoute
import org.meetagain.app.feature.attendees.AttendeesRoute
import org.meetagain.app.feature.conversation.ConversationRoute
import org.meetagain.app.feature.event.EventRoute
import org.meetagain.app.feature.explore.ExploreRoute
import org.meetagain.app.feature.group.GroupRoute
import org.meetagain.app.feature.home.HomeRoute
import org.meetagain.app.feature.me.MeRoute
import org.meetagain.app.feature.mygroups.MyGroupsRoute
import org.meetagain.app.feature.profile.ProfileRoute
import org.meetagain.app.feature.signin.SignInRoute

/**
 * Where the app starts is decided by whoever is signed in: their next meetings, or the sign-in screen. Signing in or
 * out builds a new back stack, so nothing of the one before is left behind it.
 */
@Composable
fun AppNavigation(container: AppContainer, clock: Clock = Clock.systemUTC()) {
    val session by container.auth.state.collectAsStateWithLifecycle()
    when (session) {
        // Nothing yet: reading the stored session takes a moment, and a spinner that flashes past says nothing.
        SessionState.Unknown -> Unit

        SessionState.SignedOut -> key(false) { Destinations(container, signedIn = false, clock = clock) }

        is SessionState.SignedIn -> key(true) { Destinations(container, signedIn = true, clock = clock) }
    }
}

@Composable
private fun Destinations(container: AppContainer, signedIn: Boolean, clock: Clock) {
    val backStack = rememberNavBackStack(if (signedIn) Home else SignIn)
    val back: () -> Unit = { backStack.removeLastOrNull() }
    val scope = rememberCoroutineScope()
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
                    onOpenMyGroups = { backStack.add(MyGroups) },
                    onLookAround = { backStack.add(Explore) },
                    clock = clock
                )
            }
            entry<Me> {
                MeRoute(
                    container,
                    onBack = back,
                    onOpenMyGroups = { backStack.add(MyGroups) },
                    onOpenProfile = { backStack.add(MyProfile) },
                    onOpenAbout = { backStack.add(About) },
                    onSignOut = { scope.launch { container.auth.signOut() } }
                )
            }
            entry<MyGroups> {
                MyGroupsRoute(container, onBack = back, onOpenGroup = { backStack.add(GroupPage(it)) })
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
            entry<Attendees> { key -> AttendeesRoute(container, key.id, onBack = back) }
            entry<Conversation> { key -> ConversationRoute(container, key.id, onBack = back) }
            entry<GroupPage> { key ->
                GroupRoute(
                    container,
                    key.slug,
                    onBack = back,
                    onOpenEvent = { backStack.add(EventDetail(it.id)) }
                )
            }
        }
    )
}
