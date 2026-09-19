package org.meetagain.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.meetagain.app.AppContainer
import org.meetagain.app.feature.about.AboutRoute
import org.meetagain.app.feature.start.StartScreen

@Composable
fun AppNavigation(container: AppContainer) {
    val backStack = rememberNavBackStack(Start)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Start> { StartScreen(onOpenAbout = { backStack.add(About) }) }
            entry<About> { AboutRoute(container, onBack = { backStack.removeLastOrNull() }) }
        }
    )
}
