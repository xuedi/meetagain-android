package org.meetagain.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.meetagain.app.R
import org.meetagain.app.core.data.Comment
import org.meetagain.app.core.format.rememberEventTime

/**
 * One comment: who, when, what, and a delete where the server allows it. With [onOpenAuthor], the row opens the
 * author's page, unless the account is gone or it is the member's own.
 */
@Composable
fun CommentRow(comment: Comment, onDelete: () -> Unit, onOpenAuthor: ((Int) -> Unit)? = null) {
    val time = rememberEventTime()
    val author = comment.authorId?.takeIf { !comment.mine }
    val open = if (onOpenAuthor != null && author != null) Modifier.clickable { onOpenAuthor(author) } else Modifier
    ListItem(
        leadingContent = {
            AsyncImage(
                model = comment.authorAvatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_person),
                error = painterResource(R.drawable.ic_person),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        },
        overlineContent = {
            Text(
                listOfNotNull(comment.authorName, comment.writtenAt?.let { time.relativeDay(it) })
                    .joinToString(" · ")
            )
        },
        headlineContent = { Text(comment.text) },
        trailingContent = {
            if (comment.canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.conversation_delete))
                }
            }
        },
        modifier = open
    )
}

/** The field at the very bottom of a comment list, where the keyboard and the navigation bar are. */
@Composable
fun CommentComposer(draft: String, busy: Boolean, onDraft: (String) -> Unit, onSend: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraft,
            label = { Text(stringResource(R.string.conversation_write)) },
            enabled = !busy,
            modifier = Modifier.weight(1f)
        )
        if (busy) {
            CircularProgressIndicator(Modifier.size(24.dp))
        } else {
            IconButton(onClick = onSend, enabled = draft.isNotBlank()) {
                Icon(painterResource(R.drawable.ic_send), stringResource(R.string.conversation_send))
            }
        }
    }
}
