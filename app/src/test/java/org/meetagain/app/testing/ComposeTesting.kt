package org.meetagain.app.testing

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import java.util.Locale
import org.meetagain.app.core.ui.theme.MeetAgainTheme

fun ComposeContentTestRule.onAllNodesWithTextExists(text: String): Boolean =
    onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

/** Renders [content] in the app theme as a device set to [locale], dark mode and [fontScale] would. */
@Composable
fun DeviceSettings(locale: Locale, dark: Boolean, fontScale: Float, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val configuration = Configuration(base.resources.configuration).apply {
        setLocale(locale)
        this.fontScale = fontScale
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }
    val context = base.createConfigurationContext(configuration)
    CompositionLocalProvider(
        LocalContext provides context,
        LocalResources provides context.resources,
        LocalConfiguration provides configuration,
        LocalDensity provides Density(LocalDensity.current.density, fontScale)
    ) {
        MeetAgainTheme(darkTheme = dark, content = content)
    }
}
