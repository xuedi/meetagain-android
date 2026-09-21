package org.meetagain.app.core.auth

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.SessionRefusal
import org.meetagain.app.testing.MovableClock
import org.meetagain.app.testing.ReversingCipher
import org.meetagain.app.testing.SoftwareLockedCipher
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app lock as the session sees it. The prompt itself is Android's; here its place is taken by handing over the
 * cipher it would have released.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppLockTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val file = File.createTempFile("session", ".preferences_pb", context.cacheDir).apply { delete() }
    private val store = SessionStore(PreferenceDataStoreFactory.create { file }, ReversingCipher())
    private val locked = SoftwareLockedCipher()
    private val clock = MovableClock()
    private val events = mutableListOf<LockEvent>()
    private var forgotten: Int? = null
    private val session = Session(4, "Crystal Liu", "mapat_secret-token", setOf(Session.ME_READ))

    // Nothing here reaches the network; the client only has to exist.
    private val api = ApiClient("http://localhost/", OkHttpClient(), Json, { "en" })

    private suspend fun TestScope.repository(): AuthRepository {
        val auth = AuthRepository(
            api,
            store,
            { "Pixel 8 (4f2a)" },
            this,
            forget = { forgotten = it },
            lockedCipher = locked,
            onLock = { events += it },
            clock = clock
        )
        // The stored session is read on DataStore's own thread, which advancing the test's time does not wait for.
        auth.state.first { it != SessionState.Unknown }
        return auth
    }

    private suspend fun storeLocked() = store.writeLocked(session, locked.seal(locked.forEncryption(), session.token))

    @Test
    fun `a locked session starts locked, with no token in memory`() = runTest {
        storeLocked()
        val auth = repository()
        assertEquals(SessionState.Locked(4, "Crystal Liu"), auth.state.value)
        assertTrue(auth.lockOn.value)
        assertNull(auth.token)
    }

    @Test
    fun `the cipher the prompt releases opens the session`() = runTest {
        storeLocked()
        val auth = repository()
        assertTrue(auth.unlock(checkNotNull(auth.unlockCipher())))
        assertEquals(SessionState.SignedIn(session), auth.state.value)
        assertEquals("mapat_secret-token", auth.token)
        assertEquals(listOf(LockEvent.Unlocked), events)
    }

    @Test
    fun `turning the lock on seals the stored token`() = runTest {
        store.write(session)
        val auth = repository()
        assertTrue(auth.turnLockOn(checkNotNull(auth.lockCipher())))
        val stored = store.read()
        assertTrue(stored is StoredSession.Locked)
        assertFalse((stored as StoredSession.Locked).sealed.contains("secret"))
        assertTrue(auth.lockOn.value)
        // Turning it on does not lock the member out of the app they are using.
        assertEquals(SessionState.SignedIn(session), auth.state.value)
        assertEquals(listOf(LockEvent.TurnedOn), events)
    }

    @Test
    fun `turning the lock off writes the token back and drops the key`() = runTest {
        storeLocked()
        val auth = repository()
        auth.unlock(checkNotNull(auth.unlockCipher()))
        assertTrue(auth.turnLockOff(checkNotNull(auth.unlockCipherForTurningOff())))
        assertEquals(StoredSession.Open(session), store.read())
        assertFalse(auth.lockOn.value)
        assertTrue(locked.deleted)
        assertEquals(listOf(LockEvent.Unlocked, LockEvent.TurnedOff), events)
    }

    @Test
    fun `five minutes away locks the app again`() = runTest {
        storeLocked()
        val auth = repository()
        auth.unlock(checkNotNull(auth.unlockCipher()))
        auth.leftApp()
        clock.now = clock.now.plus(Duration.ofMinutes(5))
        auth.cameBack()
        advanceUntilIdle()
        assertEquals(SessionState.Locked(4, "Crystal Liu"), auth.state.value)
        assertNull(auth.token)
        assertEquals(listOf(LockEvent.Unlocked, LockEvent.Locking), events)
    }

    @Test
    fun `a short look away leaves it open`() = runTest {
        storeLocked()
        val auth = repository()
        auth.unlock(checkNotNull(auth.unlockCipher()))
        auth.leftApp()
        clock.now = clock.now.plus(Duration.ofMinutes(4))
        auth.cameBack()
        advanceUntilIdle()
        assertEquals(SessionState.SignedIn(session), auth.state.value)
    }

    @Test
    fun `without the lock, time away changes nothing`() = runTest {
        store.write(session)
        val auth = repository()
        auth.leftApp()
        clock.now = clock.now.plus(Duration.ofHours(3))
        auth.cameBack()
        advanceUntilIdle()
        assertEquals(SessionState.SignedIn(session), auth.state.value)
    }

    @Test
    fun `a key Android threw away ends the session and says why`() = runTest {
        storeLocked()
        val auth = repository()
        locked.gone = true
        assertNull(auth.unlockCipher())
        assertEquals(SessionState.SignedOut(SessionRefusal.LockReset), auth.state.value)
        assertNull(store.read())
        assertEquals(4, forgotten)
        assertFalse(auth.lockOn.value)
    }

    @Test
    fun `signing in with the password instead leaves nothing locked behind`() = runTest {
        storeLocked()
        val auth = repository()
        auth.leaveLocked()
        assertEquals(SessionState.SignedOut(), auth.state.value)
        assertNull(store.read())
        assertEquals(4, forgotten)
        assertTrue(locked.deleted)
    }

    @Test
    fun `a cipher from another key does not unlock`() = runTest {
        storeLocked()
        val auth = repository()
        val sealed = (store.read() as StoredSession.Locked).sealed
        assertFalse(auth.unlock(checkNotNull(SoftwareLockedCipher().forDecryption(sealed))))
        assertEquals(SessionState.Locked(4, "Crystal Liu"), auth.state.value)
    }
}
