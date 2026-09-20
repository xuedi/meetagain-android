package org.meetagain.app.core.auth

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SessionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val file = File.createTempFile("session", ".preferences_pb", context.cacheDir).apply { delete() }

    // The real key lives in the Android Keystore, which a unit test has no provider for; the cipher around it is
    // the same one the app uses.
    private val cipher = AesGcmTokenCipher { key }
    private val store = SessionStore(PreferenceDataStoreFactory.create { file }, cipher)

    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val session = Session(4, "Crystal Liu", "mapat_secret-token", setOf(Session.ME_READ, Session.ME_WRITE))

    @Test
    fun `nothing is stored before the first sign-in`() = runTest {
        assertNull(store.read())
    }

    @Test
    fun `the session survives a restart`() = runTest {
        store.write(session)
        assertEquals(session, store.read())
    }

    @Test
    fun `the token is not on disk in the clear`() = runTest {
        store.write(session)
        assertFalse(file.readText(Charsets.ISO_8859_1).contains("mapat_secret-token"))
    }

    @Test
    fun `the key encrypts differently every time`() {
        assertNotEquals(cipher.encrypt("mapat_secret-token"), cipher.encrypt("mapat_secret-token"))
    }

    @Test
    fun `an unreadable token counts as signed out`() = runTest {
        val other = File.createTempFile("session", ".preferences_pb", context.cacheDir).apply { delete() }
        val store = SessionStore(PreferenceDataStoreFactory.create { other }, ReadsNothing)
        store.write(session)
        assertNull(store.read())
    }

    @Test
    fun `text that is not a stored token reads as nothing`() {
        assertNull(cipher.decrypt("not a token"))
        assertNull(cipher.decrypt("bm90:YSB0b2tlbg=="))
    }

    @Test
    fun `the name changes while the token stays`() = runTest {
        store.write(session)
        store.writeName("Crystal L")
        assertEquals(session.copy(name = "Crystal L"), store.read())
    }

    @Test
    fun `signing out leaves nothing behind`() = runTest {
        store.write(session)
        store.clear()
        assertNull(store.read())
        assertFalse(file.readText(Charsets.ISO_8859_1).contains("Crystal"))
    }

    private object ReadsNothing : TokenCipher {
        override fun encrypt(value: String) = value

        override fun decrypt(stored: String): String? = null
    }
}
