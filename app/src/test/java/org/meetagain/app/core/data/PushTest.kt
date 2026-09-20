package org.meetagain.app.core.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.noContent
import org.meetagain.app.testing.serve

/** Registering this phone for push, and the settings that decide what it is pinged about. */
class PushTest {
    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun repository() = memberRepository(server, answers)

    @Test
    fun `the devices come back with the key needed to register`() = runTest {
        server.serve(mapOf("/api/v1/me/push-subscriptions" to listOf(json(fixture("push-subscriptions.json")))))
        val devices = (repository().pushDevices() as ApiResult.Success).value
        assertTrue(devices.available)
        assertTrue(devices.vapidPublicKey.startsWith("BEl62"))
        assertEquals(1, devices.devices.size)
        assertEquals(3, devices.devices.single().id)
    }

    /** No key on the server means push is off there, which the app has to tell apart from a fault. */
    @Test
    fun `a platform without a key says push is unavailable`() = runTest {
        server.serve(
            mapOf("/api/v1/me/push-subscriptions" to listOf(json(fixture("push-unavailable.json"), status = 503)))
        )
        val result = repository().pushDevices()
        assertTrue(result is ApiResult.Failure)
        assertEquals("push_unavailable", ((result as ApiResult.Failure).error as ApiError.Http).code)
    }

    @Test
    fun `registering sends the endpoint and both keys`() = runTest {
        server.serve(mapOf("/api/v1/me/push-subscriptions" to listOf(json(fixture("push-registered.json"), 201))))
        val result = repository().registerPush(PushRegistration("https://push.example/abc", "p256", "auth"))
        assertEquals(7, (result as ApiResult.Success).value)
        val body = server.takeRequest().body?.utf8()
        assertEquals(
            """{"endpoint":"https://push.example/abc","p256dh":"p256","auth":"auth","transport":"unifiedpush"}""",
            body
        )
    }

    /** A re-registered endpoint answers 200 rather than 201, and is just as good. */
    @Test
    fun `a known endpoint is accepted again`() = runTest {
        server.serve(mapOf("/api/v1/me/push-subscriptions" to listOf(json(fixture("push-registered.json"), 200))))
        val result = repository().registerPush(PushRegistration("https://push.example/abc", "p256", "auth"))
        assertEquals(7, (result as ApiResult.Success).value)
    }

    @Test
    fun `a refused endpoint is reported`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/me/push-subscriptions" to listOf(
                    json("""{"error":"validation_failed","errors":["endpoint_rejected"]}""", status = 422)
                )
            )
        )
        val result = repository().registerPush(PushRegistration("http://10.0.0.1/x", "p", "a"))
        assertEquals("validation_failed", ((result as ApiResult.Failure).error as ApiError.Http).code)
    }

    @Test
    fun `a device can be taken off the server`() = runTest {
        server.serve(mapOf("/api/v1/me/push-subscriptions/3" to listOf(noContent())))
        assertTrue(repository().deletePushDevice(3) is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
    }

    @Test
    fun `turning a category on sends that category alone`() = runTest {
        server.serve(mapOf("/api/v1/me/notification-settings" to listOf(json(fixture("notification-settings.json")))))
        repository().setPushCategory(PushCategory.EventChanges, value = true)
        assertEquals("""{"push":{"event-changes":true}}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun `quiet hours are sent as one block`() = runTest {
        server.serve(mapOf("/api/v1/me/notification-settings" to listOf(json(fixture("notification-settings.json")))))
        repository().setQuietHours(QuietHours(true, "23:00", "06:30", "Europe/Berlin", allowUrgent = true))
        assertEquals(
            """{"quietHours":{"enabled":true,"start":"23:00","end":"06:30","timeZone":"Europe/Berlin",""" +
                """"allowUrgent":true}}""",
            server.takeRequest().body?.utf8()
        )
    }

    /** Copying the email settings is one explicit act, and writes all four categories at once. */
    @Test
    fun `matching the email settings copies all four categories`() = runTest {
        server.serve(mapOf("/api/v1/me/notification-settings" to listOf(json(fixture("notification-settings.json")))))
        val repository = repository()
        repository.refreshNotificationSettings()
        server.takeRequest(1, TimeUnit.SECONDS)
        val settings = checkNotNull(repository.notificationSettings().first()).value
        repository.matchPushToEmail(settings)
        val body = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS)?.body?.utf8())
        assertTrue(body.contains(""""event-changes":true"""))
        assertTrue(body.contains(""""reminders":true"""))
        assertTrue(body.contains(""""messages":true"""))
        assertTrue(body.contains(""""announcements":true"""))
        assertFalse(body.contains("enabled"))
    }
}
