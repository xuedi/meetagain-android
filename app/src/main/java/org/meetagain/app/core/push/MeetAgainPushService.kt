package org.meetagain.app.core.push

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.meetagain.app.MeetAgainApp
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Where a push arrives. The distributor has already decrypted it; the content is a fixed-size `{"v":1}` that says
 * nothing, so it is dropped unread and the app goes and looks for itself.
 */
class MeetAgainPushService : PushService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessage(message: PushMessage, instance: String) {
        val container = (application as MeetAgainApp).container
        scope.launch { container.pushWork().run(fromTimer = false) }
    }

    /** The distributor has an address for this phone: the server needs it before it can encrypt anything. */
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val keys = endpoint.pubKeySet ?: return
        val container = (application as MeetAgainApp).container
        scope.launch {
            container.pushRegistrar(this@MeetAgainPushService).onNewEndpoint(endpoint.url, keys.pubKey, keys.auth)
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        // Nothing to tell the member here: the timer keeps working, and the settings screen says what it can do.
        Log.w(TAG, "the distributor would not register this app: $reason")
    }

    override fun onUnregistered(instance: String) {
        val container = (application as MeetAgainApp).container
        scope.launch { container.pushRegistrar(this@MeetAgainPushService).removeFromServer() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Push"
    }
}
