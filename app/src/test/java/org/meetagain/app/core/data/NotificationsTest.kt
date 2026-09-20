package org.meetagain.app.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.serve

/** The bell and the notification settings, as the repository reads and writes them. */
class NotificationsTest {
    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun repository() = memberRepository(server, answers)

    @Test
    fun `the bell comes back as the server worded it`() = runTest {
        server.serve(mapOf("/api/v1/me/notifications" to listOf(json(fixture("notifications.json")))))
        val repository = repository()
        repository.refreshNotifications()
        val items = checkNotNull(repository.notifications().first()).value
        assertEquals(3, items.size)
        assertEquals("unread_messages", items.first().key)
        assertEquals("3 unread messages", items.first().text)
        assertEquals("https://meetagain.org/en/profile/messages", items.first().webUrl)
    }

    /** The review chain is one ordinary item, not a field of its own: the app must not count it twice. */
    @Test
    fun `the review entry is an item like any other`() = runTest {
        server.serve(mapOf("/api/v1/me/notifications" to listOf(json(fixture("notifications.json")))))
        val repository = repository()
        repository.refreshNotifications()
        val items = checkNotNull(repository.notifications().first()).value
        assertEquals(1, items.count { it.key == "review_pending" })
    }

    @Test
    fun `a member with nothing waiting has an empty bell`() = runTest {
        server.serve(mapOf("/api/v1/me/notifications" to listOf(json(fixture("notifications-empty.json")))))
        val repository = repository()
        repository.refreshNotifications()
        assertEquals(emptyList<Notification>(), checkNotNull(repository.notifications().first()).value)
    }

    /** A key the app has never heard of is still a row: the label is what the member reads. */
    @Test
    fun `an unknown key does not stop the list decoding`() = runTest {
        val body = """{"items":[{"key":"circulation_new_message","label":"A handover message"}],"total":1}"""
        server.serve(mapOf("/api/v1/me/notifications" to listOf(json(body))))
        val repository = repository()
        repository.refreshNotifications()
        val item = checkNotNull(repository.notifications().first()).value.single()
        assertEquals("circulation_new_message", item.key)
        assertNull(item.webUrl)
    }

    @Test
    fun `the settings come back with the switches the screen shows`() = runTest {
        server.serve(mapOf("/api/v1/me/notification-settings" to listOf(json(fixture("notification-settings.json")))))
        val repository = repository()
        repository.refreshNotificationSettings()
        val settings = checkNotNull(repository.notificationSettings().first()).value
        assertTrue(settings.master)
        assertFalse(settings.followingUpdates)
        assertFalse(settings.upcomingEvents)
    }

    /** Push and quiet hours have no screen yet; reading them must not drop them. */
    @Test
    fun `the settings keep what this app has no screen for`() = runTest {
        server.serve(mapOf("/api/v1/me/notification-settings" to listOf(json(fixture("notification-settings.json")))))
        val repository = repository()
        repository.refreshNotificationSettings()
        val other = checkNotNull(repository.notificationSettings().first()).value.other
        assertEquals(
            mapOf("event-changes" to false, "reminders" to false, "messages" to false, "announcements" to false),
            other.push
        )
        assertEquals("Europe/Berlin", checkNotNull(other.quietHours).timeZone)
        assertEquals("22:00", other.quietHours.start)
    }

    /** One switch at a time, and only that key, so nothing the app cannot see is written back. */
    @Test
    fun `turning a switch sends only that key`() = runTest {
        server.serve(mapOf("/api/v1/me/notification-settings" to listOf(json(fixture("notification-settings.json")))))
        val result = repository().setNotificationSetting(NotificationSetting.EventReminder, value = false)
        assertTrue(result is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("""{"eventReminder":false}""", request.body?.utf8())
    }

    @Test
    fun `the answer to a change is what gets stored`() = runTest {
        server.serve(mapOf("/api/v1/me/notification-settings" to listOf(json(fixture("notification-settings.json")))))
        val repository = repository()
        repository.setNotificationSetting(NotificationSetting.Master, value = true)
        assertTrue(checkNotNull(repository.notificationSettings().first()).value.master)
    }

    @Test
    fun `a refused change is reported and stores nothing`() = runTest {
        server.serve(
            mapOf("/api/v1/me/notification-settings" to listOf(json(fixture("error-validation.json"), status = 422)))
        )
        val repository = repository()
        val result = repository.setNotificationSetting(NotificationSetting.Announcements, value = false)
        assertTrue(result is ApiResult.Failure)
        assertNull(repository.notificationSettings().first())
    }
}
