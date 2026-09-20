package org.meetagain.app.core.auth

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import java.io.File
import javax.crypto.KeyGenerator
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.network.SessionInterceptor
import org.meetagain.app.core.network.SessionRefusal
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.noContent
import org.meetagain.app.testing.serve
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AuthRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val server = MockWebServer()
    private val file = File.createTempFile("session", ".preferences_pb", context.cacheDir).apply { delete() }
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store = SessionStore(PreferenceDataStoreFactory.create { file }, AesGcmTokenCipher { key })
    private var forgotten: Int? = null

    private lateinit var auth: AuthRepository

    private val http = OkHttpClient.Builder()
        .addInterceptor(
            SessionInterceptor({
                auth.token
            }, Json { ignoreUnknownKeys = true }) { auth.onRefusedToken(it) }
        )
        .build()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun api() = ApiClient(server.url("/").toString(), http, Json { ignoreUnknownKeys = true }, { "en" })

    private fun repository(scope: TestScope): AuthRepository {
        auth = AuthRepository(api(), store, { "Pixel 8 (4f2a)" }, scope, forget = { forgotten = it })
        return auth
    }

    private fun signedIn() = mapOf(
        "/api/v1/auth/login" to listOf(json(fixture("login.json"))),
        "/api/v1/me" to listOf(json(fixture("me.json")))
    )

    @Test
    fun `a start without a stored session ends signed out`() = runTest {
        repository(this).state.test {
            assertEquals(SessionState.Unknown, awaitItem())
            assertEquals(SessionState.SignedOut(), awaitItem())
        }
    }

    @Test
    fun `a stored session is there from the start`() = runTest {
        val stored = Session(4, "Crystal Liu", "mapat_stored", setOf(Session.ME_READ))
        store.write(stored)
        repository(this).state.test {
            assertEquals(SessionState.Unknown, awaitItem())
            assertEquals(SessionState.SignedIn(stored), awaitItem())
        }
    }

    @Test
    fun `signing in keeps the member, the token and the scopes`() = runTest {
        server.serve(signedIn())
        val auth = repository(this)
        assertEquals(ApiResult.Success(Unit), auth.signIn(" crystal.liu@example.org ", "1234"))
        val session = checkNotNull(auth.state.value.member)
        assertEquals(4, session.memberId)
        assertEquals("Crystal Liu", session.name)
        assertTrue(session.may(Session.EVENTS_WRITE))
        assertEquals(session, store.read())
    }

    @Test
    fun `signing in sends the trimmed address and this device`() = runTest {
        server.serve(signedIn())
        repository(this).signIn(" crystal.liu@example.org ", "1234")
        val body = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(body.contains(""""email":"crystal.liu@example.org""""))
        assertTrue(body.contains(""""deviceName":"Pixel 8 (4f2a)""""))
    }

    @Test
    fun `the calls after signing in carry the token`() = runTest {
        server.serve(signedIn())
        repository(this).signIn("crystal.liu@example.org", "1234")
        server.takeRequest()
        assertEquals("Bearer $FIXTURE_TOKEN", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `each refusal comes back with its code and signs nobody in`() = runTest {
        for ((status, code) in REFUSALS) {
            server.serve(mapOf("/api/v1/auth/login" to listOf(json("""{"error":"$code"}""", status))))
            val result = repository(this).signIn("crystal.liu@example.org", "wrong")
            assertEquals(code, ((result as ApiResult.Failure).error as ApiError.Http).code)
            assertNull(auth.state.value.member)
            assertNull(store.read())
        }
    }

    @Test
    fun `a refused sign-in says how long to wait`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .setHeader("Content-Type", "application/json")
                .setHeader("Retry-After", "900")
                .body("""{"error":"too_many_attempts"}""")
                .build()
        )
        val result = repository(this).signIn("crystal.liu@example.org", "wrong")
        assertEquals(900, ((result as ApiResult.Failure).error as ApiError.Http).retryAfter)
    }

    @Test
    fun `a token the server refuses ends the session`() = runTest {
        server.serve(signedIn())
        val auth = repository(this)
        auth.signIn("crystal.liu@example.org", "1234")
        server.serve(mapOf("/api/v1/me" to listOf(json(fixture("error-invalid-token.json"), 401))))
        auth.state.test {
            assertTrue(awaitItem() is SessionState.SignedIn)
            api().me()
            assertEquals(SessionState.SignedOut(SessionRefusal.TokenRefused), awaitItem())
        }
        assertNull(store.read())
        assertEquals(4, forgotten)
    }

    @Test
    fun `signing out tells the server and wipes everything`() = runTest {
        server.serve(signedIn() + ("/api/v1/auth/logout" to listOf(noContent())))
        val auth = repository(this)
        auth.signIn("crystal.liu@example.org", "1234")
        auth.signOut()
        assertEquals(SessionState.SignedOut(), auth.state.value)
        assertNull(auth.token)
        assertNull(store.read())
        assertEquals(4, forgotten)
    }

    @Test
    fun `signing out that the screen walks away from still wipes everything`() = runTest {
        server.serve(signedIn() + ("/api/v1/auth/logout" to listOf(noContent())))
        val auth = repository(this)
        auth.signIn("crystal.liu@example.org", "1234")
        val asking = launch { auth.signOut() }
        advanceUntilIdle()
        asking.cancel()
        auth.state.test {
            var state = awaitItem()
            while (state !is SessionState.SignedOut) state = awaitItem()
        }
        assertNull(store.read())
        assertEquals(4, forgotten)
    }

    @Test
    fun `signing out without a connection still wipes the session`() = runTest {
        server.serve(signedIn())
        val auth = repository(this)
        auth.signIn("crystal.liu@example.org", "1234")
        server.close()
        auth.signOut()
        assertEquals(SessionState.SignedOut(), auth.state.value)
        assertNull(store.read())
    }

    @Test
    fun `a sign-in the screen walks away from is still stored`() = runTest {
        server.serve(signedIn())
        val auth = repository(this)
        // The screen that asked is gone the moment the session becomes true; the work must not go with it.
        val asking = launch { auth.signIn("crystal.liu@example.org", "1234") }
        advanceUntilIdle()
        asking.cancel()
        auth.state.test {
            assertEquals(SessionState.Unknown, awaitItem())
            assertEquals(SessionState.SignedOut(), awaitItem())
            assertTrue(awaitItem() is SessionState.SignedIn)
        }
        assertEquals(4, checkNotNull(store.read()).memberId)
    }

    @Test
    fun `the password is never written down`() = runTest {
        server.serve(signedIn())
        repository(this).signIn("crystal.liu@example.org", "hunter2-secret")
        assertFalse(file.readText(Charsets.ISO_8859_1).contains("hunter2-secret"))
    }

    private companion object {
        const val FIXTURE_TOKEN = "mapat_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        val REFUSALS = listOf(
            401 to LoginError.INVALID_CREDENTIALS,
            403 to LoginError.ACCOUNT_BLOCKED,
            403 to LoginError.EMAIL_NOT_VERIFIED,
            403 to LoginError.PENDING_APPROVAL,
            429 to LoginError.LOGIN_RESTRICTED,
            429 to LoginError.TOO_MANY_ATTEMPTS,
            400 to LoginError.INVALID_DEVICE_NAME
        )
    }
}
