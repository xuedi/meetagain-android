package org.meetagain.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.meetagain.app.core.ui.theme.MeetAgainTheme
import org.meetagain.app.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MeetAgainApp).container
        setContent {
            MeetAgainTheme {
                AppNavigation(container)
            }
        }
    }
}
