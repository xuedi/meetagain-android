package org.meetagain.app.feature.start

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.meetagain.app.R

@Composable
fun StartRoute(onOpenAbout: () -> Unit) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Image(painterResource(R.drawable.brand_mark), contentDescription = null)
            Text(stringResource(R.string.app_name))
            TextButton(onClick = onOpenAbout) { Text(stringResource(R.string.about_title)) }
        }
    }
}
