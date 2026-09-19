package org.meetagain.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Start : NavKey

@Serializable
data object About : NavKey
