package org.meetagain.app.testing

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.meetagain.app.core.format.LocalTimeContext
import org.meetagain.app.core.format.TimeContext
import org.meetagain.app.core.ui.theme.MeetAgainTheme

fun ComposeContentTestRule.onAllNodesWithTextExists(text: String): Boolean =
    onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

/**
 * Renders [content] in the app theme as a device set to [locale], dark mode and [fontScale] would, in Berlin on
 * 19 September 2026, with a grey block for every image.
 */
@OptIn(ExperimentalCoilApi::class)
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
        LocalDensity provides Density(LocalDensity.current.density, fontScale),
        LocalTimeContext provides TimeContext(ZoneId.of("Europe/Berlin"), LocalDate.of(2026, 9, 19)),
        LocalInspectionMode provides true,
        LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { ColorImage(Color(0xFF8D909F).toArgb()) }
    ) {
        MeetAgainTheme(darkTheme = dark, content = content)
    }
}
