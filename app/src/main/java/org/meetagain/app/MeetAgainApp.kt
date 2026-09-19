package org.meetagain.app

import android.app.Application

class MeetAgainApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(AppInfo(BuildConfig.VERSION_NAME, BuildConfig.DEBUG, BuildConfig.BASE_URL))
    }
}
