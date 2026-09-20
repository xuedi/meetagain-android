package org.meetagain.app

import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.meetagain.app.core.auth.AuthRepository
import org.meetagain.app.core.auth.SessionStore
import org.meetagain.app.core.auth.member
import org.meetagain.app.core.cache.CacheDatabase
import org.meetagain.app.core.data.AnswerCache
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.PublicRepository
import org.meetagain.app.core.i18n.AppLocale
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.SessionInterceptor

/** The app's object graph, built once in [MeetAgainApp] and handed to ViewModels by their factories. */
class AppContainer(
    val appInfo: AppInfo,
    cache: CacheDatabase,
    sessionStore: SessionStore,
    deviceName: () -> String,
    scope: CoroutineScope,
    clock: Clock = Clock.systemUTC()
) {
    val json = Json { ignoreUnknownKeys = true }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(SessionInterceptor(token = { auth.token }, onInvalidToken = { auth.onInvalidToken() }))
        .build()

    val api = ApiClient(appInfo.baseUrl, http, json, languageTag = { AppLocale.current() })

    /** Answers depend on who asks, so they are stored under whoever is signed in at the time. */
    private val answers = AnswerCache(
        cache.answers(),
        json,
        language = { AppLocale.current() },
        owner = { AnswerCache.ownerOf(auth.state.value.member?.memberId) },
        clock = clock
    )

    val publicRepository = PublicRepository(api, appInfo.baseUrl, answers, clock)

    val memberRepository = MemberRepository(api, appInfo.baseUrl, answers, clock)

    val auth: AuthRepository =
        AuthRepository(api, sessionStore, deviceName, scope, forget = { id -> answers.forgetMember(id) })
}
