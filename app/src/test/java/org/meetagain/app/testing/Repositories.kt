package org.meetagain.app.testing

import kotlinx.serialization.json.Json
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.meetagain.app.core.data.PublicRepository
import org.meetagain.app.core.network.ApiClient

/** A repository on [server] that counts meetagain.org, where the fixtures' images live, as its own host. */
fun publicRepository(server: MockWebServer): PublicRepository {
    val api = ApiClient(server.url("/").toString(), OkHttpClient(), Json { ignoreUnknownKeys = true }, { "en" })
    return PublicRepository(api, "https://meetagain.org")
}
