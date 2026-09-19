package org.meetagain.app.screenshots

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meetagain.app.AppInfo
import org.meetagain.app.feature.about.AboutScreen
import org.meetagain.app.feature.about.AboutUiState
import org.meetagain.app.feature.about.ServerCheck
import org.meetagain.app.feature.start.StartScreen
import org.meetagain.app.testing.DeviceSettings
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
    fun start() = capture("start") { StartScreen(onOpenAbout = {}) }

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
