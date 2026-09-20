package org.meetagain.app

import java.net.URI

/** What the app knows about itself: its version, whether it is a test build, and the server it talks to. */
data class AppInfo(
    val version: String,
    val testBuild: Boolean,
    val baseUrl: String,
    /** The website a tapped link has to be on to open in the app. */
    val linkHost: String = BuildConfig.LINK_HOST
) {
    /** The server as a member would write it: host, plus the port when it is not the default. */
    val serverName: String = URI(baseUrl).authority
}
