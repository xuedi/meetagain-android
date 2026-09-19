package org.meetagain.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Start : NavKey

@Serializable
data object About : NavKey

@Serializable
data object Explore : NavKey

@Serializable
data class EventDetail(val id: Int) : NavKey

@Serializable
data class GroupPage(val slug: String) : NavKey
