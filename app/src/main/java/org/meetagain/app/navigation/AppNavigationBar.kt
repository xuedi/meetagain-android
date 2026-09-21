package org.meetagain.app.navigation

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.meetagain.app.R

/**
 * The places a member goes on purpose. Each is a root of its own: the bar switches between them and drops whatever
 * was above the one it leaves, and system back from any of the others lands on Meetings before it leaves the app.
 *
 * Nothing else moves into the bar: `Me` stays behind the person icon in the top app bar, and the public events and
 * groups stay behind the end of the meetings list, so the bar invents no invitation to browse.
 */
enum class Root(val label: Int, val icon: Int) {
    Meetings(R.string.nav_meetings, R.drawable.ic_event),
    Messages(R.string.nav_messages, R.drawable.ic_mail),
    Groups(R.string.nav_groups, R.drawable.ic_group),

    /** Only while at least one of the member's groups opens its Town Hall to them. */
    TownHall(R.string.nav_town_hall, R.drawable.ic_forum)
}

/** The roots every signed-in member has. A group feature's root comes after them, so these three never move. */
val FIXED_ROOTS = listOf(Root.Meetings, Root.Messages, Root.Groups)

/**
 * [unreadMessages] puts a plain dot on Messages and never a number: the count belongs in a conversation row, where
 * the website shows it too.
 */
@Composable
fun AppNavigationBar(
    current: Root,
    unreadMessages: Boolean,
    onSelect: (Root) -> Unit,
    roots: List<Root> = FIXED_ROOTS
) {
    val unread = stringResource(R.string.messages_unread_dot)
    NavigationBar {
        roots.forEach { root ->
            NavigationBarItem(
                selected = root == current,
                onClick = { onSelect(root) },
                icon = {
                    if (root == Root.Messages && unreadMessages) {
                        BadgedBox(badge = { Badge(modifier = Modifier.semantics { contentDescription = unread }) }) {
                            Icon(painterResource(root.icon), contentDescription = null)
                        }
                    } else {
                        Icon(painterResource(root.icon), contentDescription = null)
                    }
                },
                label = { Text(stringResource(root.label)) }
            )
        }
    }
}
