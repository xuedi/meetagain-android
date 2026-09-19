package org.meetagain.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.meetagain.app.AppContainer
import org.meetagain.app.feature.about.AboutRoute
import org.meetagain.app.feature.start.StartRoute

@Composable
fun AppNavigation(container: AppContainer) {
    val backStack = rememberNavBackStack(Start)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Start> { StartRoute(onOpenAbout = { backStack.add(About) }) }
            entry<About> { AboutRoute(container, onBack = { backStack.removeLastOrNull() }) }
        }
    )
}
