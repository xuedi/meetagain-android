package org.meetagain.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.meetagain.app.AppContainer
import org.meetagain.app.feature.about.AboutRoute
import org.meetagain.app.feature.event.EventRoute
import org.meetagain.app.feature.explore.ExploreRoute
import org.meetagain.app.feature.group.GroupRoute
import org.meetagain.app.feature.start.StartScreen

@Composable
fun AppNavigation(container: AppContainer) {
    val backStack = rememberNavBackStack(Start)
    val back: () -> Unit = { backStack.removeLastOrNull() }
    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Start> {
                StartScreen(onLookAround = { backStack.add(Explore) }, onOpenAbout = { backStack.add(About) })
            }
            entry<About> { AboutRoute(container, onBack = back) }
            entry<Explore> {
                ExploreRoute(
                    container,
                    onBack = back,
                    onOpenEvent = { backStack.add(EventDetail(it.id)) },
                    onOpenGroup = { backStack.add(GroupPage(it.slug)) }
                )
            }
            entry<EventDetail> { key -> EventRoute(container, key.id, onBack = back) }
            entry<GroupPage> { key ->
                GroupRoute(container, key.slug, onBack = back, onOpenEvent = { backStack.add(EventDetail(it.id)) })
            }
        }
    )
}
