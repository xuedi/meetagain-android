package org.meetagain.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object SignIn : NavKey

@Serializable
data object Home : NavKey

@Serializable
data object Me : NavKey

@Serializable
data object MyGroups : NavKey

@Serializable
data object MyProfile : NavKey

@Serializable
data object About : NavKey

@Serializable
data object Explore : NavKey

@Serializable
data class EventDetail(val id: Int) : NavKey

@Serializable
data class Attendees(val id: Int) : NavKey

@Serializable
data class Conversation(val id: Int) : NavKey

@Serializable
data class GroupPage(val slug: String) : NavKey
