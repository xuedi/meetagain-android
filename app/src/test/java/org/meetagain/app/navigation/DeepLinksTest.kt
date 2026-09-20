package org.meetagain.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which meetagain.org addresses the app takes, and which it leaves to the browser. */
class DeepLinksTest {
    private fun destination(url: String?) = destinationOf(url, HOST)

    @Test
    fun `an event opens the event screen`() {
        assertEquals(EventDetail(117), destination("https://meetagain.org/en/event/117"))
    }

    @Test
    fun `the language in front of the path is not part of the address`() {
        assertEquals(EventDetail(117), destination("https://meetagain.org/zh/event/117"))
        assertEquals(EventDetail(117), destination("https://meetagain.org/event/117"))
    }

    @Test
    fun `a query and a fragment do not change where a link goes`() {
        assertEquals(EventDetail(117), destination("https://meetagain.org/en/event/117?from=mail#comments"))
    }

    @Test
    fun `the public lists open where the app shows them`() {
        assertEquals(Explore, destination("https://meetagain.org/en/events"))
        assertEquals(Explore, destination("https://meetagain.org/de/groups"))
    }

    @Test
    fun `the profile pages the app has open in the app`() {
        assertEquals(MyProfile, destination("https://meetagain.org/en/profile/"))
        assertEquals(MyGroups, destination("https://meetagain.org/en/profile/my-groups/"))
        assertEquals(Notifications, destination("https://meetagain.org/en/profile/notifications"))
        assertEquals(NotificationSettings, destination("https://meetagain.org/en/profile/config"))
        assertEquals(Blocked, destination("https://meetagain.org/en/profile/blocked"))
    }

    /** What makes the bell's unread-messages item and the message push open the app instead of the browser. */
    @Test
    fun `the messages pages open the inbox and that thread`() {
        assertEquals(Messages, destination("https://meetagain.org/en/profile/messages"))
        assertEquals(Messages, destination("https://meetagain.org/de/profile/messages/"))
        assertEquals(Thread(12), destination("https://meetagain.org/en/profile/messages/12"))
        assertEquals(Thread(12), destination("https://meetagain.org/zh/profile/messages/12?from=mail#last"))
    }

    @Test
    fun `a partner that is not a number is not a screen`() {
        assertNull(destination("https://meetagain.org/en/profile/messages/not-a-number"))
        assertNull(destination("https://meetagain.org/en/profile/messages/12/extra"))
    }

    @Test
    fun `the front page opens where the app starts`() {
        assertEquals(Home, destination("https://meetagain.org/en"))
        assertEquals(Home, destination("https://meetagain.org/"))
    }

    /** A profile page the app does not have belongs on the website, not on a guessed screen. */
    @Test
    fun `a profile page the app does not have stays on the website`() {
        assertNull(destination("https://meetagain.org/en/profile/review"))
        assertNull(destination("https://meetagain.org/en/profile/access-tokens"))
    }

    /** A group's own domain can never be a verified App Links host, so it is never taken. */
    @Test
    fun `a group domain is left to the browser`() {
        assertNull(destination("https://weiqi.berlin/en/event/117"))
        assertNull(destination("https://weiqi.berlin/en/events"))
    }

    @Test
    fun `an address that names no screen is not taken`() {
        assertNull(destination("https://meetagain.org/en/imprint"))
        assertNull(destination("https://meetagain.org/en/event/not-a-number"))
        assertNull(destination("https://meetagain.org/en/event"))
    }

    @Test
    fun `nothing at all is not a destination`() {
        assertNull(destination(null))
        assertNull(destination(""))
        assertNull(destination("not a url"))
        assertNull(destination("mailto:hello@meetagain.org"))
    }

    @Test
    fun `the host is matched whatever its case`() {
        assertEquals(EventDetail(1), destination("https://MeetAgain.org/en/event/1"))
    }

    private companion object {
        const val HOST = "meetagain.org"
    }
}
