package org.meetagain.app

import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.meetagain.app.core.cache.CacheDatabase
import org.meetagain.app.core.data.PublicRepository
import org.meetagain.app.core.i18n.AppLocale
import org.meetagain.app.core.network.ApiClient

/** The app's object graph, built once in [MeetAgainApp] and handed to ViewModels by their factories. */
class AppContainer(val appInfo: AppInfo, cache: CacheDatabase, clock: Clock = Clock.systemUTC()) {
    val json = Json { ignoreUnknownKeys = true }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    val api = ApiClient(appInfo.baseUrl, http, json, languageTag = { AppLocale.current() })

    val publicRepository =
        PublicRepository(api, appInfo.baseUrl, cache.answers(), json, language = { AppLocale.current() }, clock)
}
