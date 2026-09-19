package org.meetagain.app.core.network

import kotlinx.serialization.Serializable

/** `HealthStatus` in the API description. */
@Serializable
data class Status(val status: String)
