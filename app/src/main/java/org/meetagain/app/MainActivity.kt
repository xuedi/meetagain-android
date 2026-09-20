package org.meetagain.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation3.runtime.NavKey
import org.meetagain.app.core.ui.theme.MeetAgainTheme
import org.meetagain.app.navigation.AppNavigation
import org.meetagain.app.navigation.destinationOf

class MainActivity : ComponentActivity() {
    /** The screen a tapped meetagain.org link asks for, read from the intent that started or resumed the app. */
    private var opening by mutableStateOf<NavKey?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MeetAgainApp).container
        opening = container.destinationOf(intent)
        setContent {
            MeetAgainTheme {
                AppNavigation(container, opening = opening)
            }
        }
    }

    /** The app is already open and a link arrives: the same handling, so the link does not land on yesterday's screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        opening = (application as MeetAgainApp).container.destinationOf(intent)
    }

    private fun AppContainer.destinationOf(intent: Intent?): NavKey? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString?.let { destinationOf(it, appInfo.linkHost) }
}
