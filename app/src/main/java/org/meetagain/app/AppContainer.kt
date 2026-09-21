package org.meetagain.app

import android.content.Context
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.meetagain.app.core.auth.AuthRepository
import org.meetagain.app.core.auth.LockEvent
import org.meetagain.app.core.auth.SessionStore
import org.meetagain.app.core.auth.member
import org.meetagain.app.core.cache.CacheDatabase
import org.meetagain.app.core.data.AnswerCache
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.PublicRepository
import org.meetagain.app.core.i18n.AppLocale
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.SessionInterceptor
import org.meetagain.app.core.push.LockedPushWork
import org.meetagain.app.core.push.NotificationSignal
import org.meetagain.app.core.push.PushNotifier
import org.meetagain.app.core.push.PushPreferences
import org.meetagain.app.core.push.PushRegistrar
import org.meetagain.app.core.push.PushRun
import org.meetagain.app.core.push.PushTimer
import org.meetagain.app.core.push.PushWork
import org.meetagain.app.core.push.RaisedNotifications
import org.meetagain.app.core.push.SignalStore
import org.meetagain.app.feature.townhall.TownHallRepository

/** The app's object graph, built once in [MeetAgainApp] and handed to ViewModels by their factories. */
class AppContainer(
    private val appContext: Context,
    val appInfo: AppInfo,
    private val cache: CacheDatabase,
    private val sessionStore: SessionStore,
    val signalStore: SignalStore,
    deviceName: () -> String,
    scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC()
) {
    val json = Json { ignoreUnknownKeys = true }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(
            SessionInterceptor(token = {
                auth.token
            }, json = json, onRefused = { auth.onRefusedToken(it) })
        )
        .build()

    val api = ApiClient(appInfo.baseUrl, http, json, languageTag = { AppLocale.current() })

    /** The same client without the member's token, for the one call that brings the signal token instead. */
    private val bareApi = ApiClient(
        appInfo.baseUrl,
        http.newBuilder().apply { interceptors().clear() }.build(),
        json,
        languageTag = { AppLocale.current() }
    )

    val signal = NotificationSignal(api, bareApi, signalStore, clock)

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

    val townHallRepository = TownHallRepository(api, appInfo.baseUrl, answers, memberRepository)

    val auth: AuthRepository = AuthRepository(
        api,
        sessionStore,
        deviceName,
        scope,
        forget = { id ->
            answers.forgetMember(id)
            signal.discard()
        },
        beforeSignOut = {
            PushTimer.stop(appContext)
            pushRegistrar().removeFromServer()
        },
        onLock = ::onLock,
        clock = clock
    )

    /**
     * What moves with the app lock. The signal token exists only while it is on; what push needs to know about the
     * member is copied whenever their own settings can still be read.
     */
    private suspend fun onLock(event: LockEvent) {
        when (event) {
            LockEvent.TurnedOn -> {
                rememberPushSettings()
                signal.ensureToken()
            }

            LockEvent.Unlocked -> {
                rememberPushSettings()
                signal.ensureToken()
                pushRegistrar().registerPending()
            }

            LockEvent.Locking -> rememberPushSettings()

            LockEvent.TurnedOff -> signal.discard()
        }
    }

    private suspend fun rememberPushSettings() = signal.remember(memberRepository.notificationSettings().first()?.value)

    /**
     * What this phone has already announced, under the signed-in member's own prefix, so it goes when they do.
     */
    private val raised = RaisedNotifications(
        cache.answers(),
        json,
        owner = { AnswerCache.ownerOf(auth.state.value.member?.memberId) },
        clock = clock
    )

    /**
     * Built per call rather than held: a push arrives in a service with its own context, and nothing here should
     * keep one alive. Whether the lock is on is read from disk, because a push can start the process before
     * anything else has read the session.
     */
    suspend fun pushWork(context: Context = appContext): PushRun = if (sessionStore.lockOn()) {
        LockedPushWork(context, signal, signalStore, PushNotifier(context), clock)
    } else {
        PushWork(context, memberRepository, raised, PushNotifier(context), clock)
    }

    fun pushRegistrar(context: Context = appContext) =
        PushRegistrar(context, memberRepository, canRegister = { auth.token != null }, preferences = pushPreferences)

    val pushPreferences: PushPreferences by lazy { PushPreferences.open(appContext) }
}
