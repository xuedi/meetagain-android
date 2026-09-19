package org.meetagain.app.feature.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.meetagain.app.AppContainer
import org.meetagain.app.R

@Composable
fun AboutRoute(@Suppress("UNUSED_PARAMETER") container: AppContainer, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold { padding ->
        Text(stringResource(R.string.about_title), Modifier.fillMaxSize().padding(padding))
    }
}
