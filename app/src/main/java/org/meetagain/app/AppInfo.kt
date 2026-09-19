package org.meetagain.app

import java.net.URI

/** What the app knows about itself: its version, whether it is a test build, and the server it talks to. */
data class AppInfo(val version: String, val testBuild: Boolean, val baseUrl: String) {
    /** The server as a member would write it: host, plus the port when it is not the default. */
    val serverName: String = URI(baseUrl).authority
}
