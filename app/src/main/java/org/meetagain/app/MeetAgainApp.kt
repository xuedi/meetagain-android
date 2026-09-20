package org.meetagain.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.meetagain.app.core.auth.SessionStore
import org.meetagain.app.core.auth.deviceName
import org.meetagain.app.core.cache.CacheDatabase

class MeetAgainApp :
    Application(),
    SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    private val housekeeping = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            AppInfo(BuildConfig.VERSION_NAME, BuildConfig.DEBUG, BuildConfig.BASE_URL),
            CacheDatabase.open(this),
            SessionStore.open(this),
            deviceName = { deviceName(this) },
            scope = housekeeping
        )
        housekeeping.launch { container.publicRepository.forgetUnused() }
    }

    /** Images load through the app's own HTTP client, with its timeouts. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.http })) }
        .build()
}
