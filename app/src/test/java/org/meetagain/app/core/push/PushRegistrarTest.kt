package org.meetagain.app.core.push

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meetagain.app.core.data.PushRegistration
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.serve
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An endpoint the distributor hands over while the app lock keeps the member's token sealed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PushRegistrarTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val server = MockWebServer()
    private val preferences = PushPreferences(
        PreferenceDataStoreFactory.create { File.createTempFile("push", ".preferences_pb", context.cacheDir) }
    )

    @Before
    fun start() {
        server.start()
        server.serve(mapOf("/api/v1/me/push-subscriptions" to listOf(json(fixture("push-registered.json"), 201))))
    }

    @After
    fun stop() = server.close()

    private fun registrar(unlocked: Boolean) =
        PushRegistrar(context, memberRepository(server), canRegister = { unlocked }, preferences = preferences)

    @Test
    fun `a locked app keeps the endpoint for later and sends nothing`() = runTest {
        registrar(unlocked = false).onNewEndpoint("https://push.example/abc", "p256", "auth")
        assertEquals(0, server.requestCount)
        assertEquals(PushRegistration("https://push.example/abc", "p256", "auth"), preferences.pendingEndpoint())
    }

    @Test
    fun `the next unlock registers it and lets it go`() = runTest {
        registrar(unlocked = false).onNewEndpoint("https://push.example/abc", "p256", "auth")
        registrar(unlocked = true).registerPending()
        assertEquals("/api/v1/me/push-subscriptions", server.takeRequest().url.encodedPath)
        assertNull(preferences.pendingEndpoint())
    }

    @Test
    fun `an open app registers straight away`() = runTest {
        registrar(unlocked = true).onNewEndpoint("https://push.example/abc", "p256", "auth")
        assertEquals(1, server.requestCount)
        assertNull(preferences.pendingEndpoint())
    }
}
