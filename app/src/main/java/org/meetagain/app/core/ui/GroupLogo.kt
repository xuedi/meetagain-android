package org.meetagain.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import org.meetagain.app.core.data.Group

/**
 * The group's logo clipped to a circle, or its initial when it has none. Decorative: it always sits next to the
 * group's name, which screen readers read instead.
 */
@Composable
fun GroupLogo(group: Group, size: Dp, modifier: Modifier = Modifier) {
    val shape = Modifier
        .size(size)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    if (group.logoUrl != null) {
        AsyncImage(
            model = group.logoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.then(shape)
        )
    } else {
        Box(modifier.then(shape), contentAlignment = Alignment.Center) {
            Text(
                text = group.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
