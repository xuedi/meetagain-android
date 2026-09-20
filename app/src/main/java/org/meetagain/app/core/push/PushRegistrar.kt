package org.meetagain.app.core.push

import android.content.Context
import android.util.Log
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.PushRegistration
import org.meetagain.app.core.network.ApiResult
import org.unifiedpush.android.connector.UnifiedPush

/** Why this phone cannot be pinged, when it cannot. */
enum class PushObstacle {
    /** No distributor app is installed, so the timer carries everything. */
    NoDistributor,

    /** The server has no key configured: push is off on this platform. */
    ServerHasNone
}

/**
 * Getting this phone registered for push, and taking it off again.
 *
 * The app never contacts a relay itself: it asks a distributor app the member installed, over Android's own IPC,
 * and that app talks to whichever server it is set up with. MeetAgain only ever learns the endpoint it should
 * encrypt to.
 */
class PushRegistrar(private val context: Context, private val repository: MemberRepository) {
    fun hasDistributor(): Boolean = UnifiedPush.getDistributors(context).isNotEmpty()

    /**
     * Asks the distributor for an endpoint. The endpoint does not come back here but through
     * [MeetAgainPushService], because the distributor answers whenever it is ready.
     */
    suspend fun register(): PushObstacle? {
        val devices = when (val result = repository.pushDevices()) {
            is ApiResult.Failure -> return PushObstacle.ServerHasNone
            is ApiResult.Success -> result.value
        }
        if (!devices.available) return PushObstacle.ServerHasNone
        if (!hasDistributor()) return PushObstacle.NoDistributor

        UnifiedPush.tryUseDefaultDistributor(context) { picked ->
            if (picked) {
                UnifiedPush.register(context, INSTANCE, vapid = devices.vapidPublicKey)
            }
        }
        return null
    }

    /** Tells the distributor to stop, and takes this phone off the server so nothing is sent into the void. */
    suspend fun unregister() {
        runCatching { UnifiedPush.unregister(context, INSTANCE) }
            .onFailure { Log.w(TAG, "the distributor refused to unregister") }
        removeFromServer()
    }

    /**
     * Before signing out. The token this subscription hangs off is revoked a moment later, which stops delivery
     * either way, but the row is only swept some time after that - so it is deleted here, where the answer says
     * whether it worked.
     */
    suspend fun removeFromServer() {
        val devices = (repository.pushDevices() as? ApiResult.Success)?.value ?: return
        devices.devices.forEach { repository.deletePushDevice(it.id) }
    }

    suspend fun onNewEndpoint(endpoint: String, p256dh: String, auth: String) {
        val result = repository.registerPush(PushRegistration(endpoint, p256dh, auth))
        if (result is ApiResult.Failure) Log.w(TAG, "the server would not take this endpoint")
    }

    companion object {
        /** One registration per app, named as UnifiedPush expects. */
        const val INSTANCE = "default"
        private const val TAG = "Push"
    }
}
