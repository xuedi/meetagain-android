package org.meetagain.app

import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.meetagain.app.core.network.ApiClient

/** The app's object graph, built once in [MeetAgainApp] and handed to ViewModels by their factories. */
class AppContainer(val baseUrl: String) {
    val json = Json { ignoreUnknownKeys = true }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    val api = ApiClient(baseUrl, http, json, languageTag = { Locale.getDefault().toLanguageTag() })
}
