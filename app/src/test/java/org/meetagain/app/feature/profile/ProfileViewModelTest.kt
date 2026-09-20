package org.meetagain.app.feature.profile

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import java.io.ByteArrayInputStream
import java.io.File
import javax.crypto.KeyGenerator
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meetagain.app.core.auth.AesGcmTokenCipher
import org.meetagain.app.core.auth.AuthRepository
import org.meetagain.app.core.auth.SessionStore
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.Upload
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.testing.MainDispatcherRule
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.serve
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProfileViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun viewModel(): ProfileViewModel {
        val file = File.createTempFile("session", ".preferences_pb", context.cacheDir).apply { delete() }
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = SessionStore(PreferenceDataStoreFactory.create { file }, AesGcmTokenCipher { key })
        val api = ApiClient(server.url("/").toString(), OkHttpClient(), Json { ignoreUnknownKeys = true }, { "en" })
        val auth = AuthRepository(api, store, { "Pixel 8 (4f2a)" }, kotlinx.coroutines.MainScope())
        return mainDispatcher.keep(ProfileViewModel(memberRepository(server, answers), auth))
    }

    @Test
    fun `the profile is shown as the member can change it`() = runTest {
        server.serve(mapOf("/api/v1/me" to listOf(json(fixture("me.json")))))
        viewModel().state.test {
            assertEquals(Loadable.Loading, awaitItem())
            val profile = (awaitItem() as Loadable.Loaded).value
            assertEquals("Crystal Liu", profile.name)
            assertEquals("zh", profile.language)
            assertTrue(profile.public)
        }
    }

    @Test
    fun `nothing is saved until something is changed`() = runTest {
        server.serve(mapOf("/api/v1/me" to listOf(json(fixture("me.json")))))
        val viewModel = viewModel()
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(viewModel.edit.value)
        viewModel.name("Crystal L")
        assertEquals("Crystal L", viewModel.edit.value?.name)
    }

    @Test
    fun `saving sends only the fields the form holds`() = runTest {
        server.serve(mapOf("/api/v1/me" to listOf(json(fixture("me.json")))))
        val viewModel = viewModel()
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.name("Crystal L")
        viewModel.message.test {
            assertNull(awaitItem())
            viewModel.save()
            assertEquals(ProfileMessage.Saved, awaitItem())
        }
        val patch = List(server.requestCount) { server.takeRequest() }.first { it.method == "PATCH" }
        val body = patch.body?.utf8().orEmpty()
        assertTrue(body.contains(""""name":"Crystal L""""))
        assertTrue(body.contains(""""locale":"zh""""))
        assertNull(viewModel.edit.value)
    }

    @Test
    fun `a rejected name is named, not just refused`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/me" to listOf(
                    json(fixture("me.json")),
                    json("""{"error":"validation_failed","errors":["name_too_long"]}""", 422),
                    json(fixture("me.json"))
                )
            )
        )
        val viewModel = viewModel()
        viewModel.state.test {
            assertEquals(Loadable.Loading, awaitItem())
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.name("A very long name indeed, far past what the server takes from anyone")
        viewModel.message.test {
            assertNull(awaitItem())
            viewModel.save()
            assertEquals(ProfileMessage.NameTooLong, awaitItem())
        }
    }

    @Test
    fun `an avatar goes up as a file`() = runTest {
        server.serve(
            mapOf(
                "/api/v1/me" to listOf(json(fixture("me.json"))),
                "/api/v1/me/avatar" to listOf(json(fixture("me.json")))
            )
        )
        val viewModel = viewModel()
        val bytes = ByteArray(8) { it.toByte() }
        viewModel.uploadAvatar(Upload("me.jpg", "image/jpeg", bytes.size.toLong()) { ByteArrayInputStream(bytes) })
        advanceUntilIdle()
        val posted = generateSequence { server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS) }
            .firstOrNull { it.url.encodedPath == "/api/v1/me/avatar" }
        assertEquals("POST", posted?.method)
        assertTrue(posted?.headers?.get("Content-Type").orEmpty().startsWith("multipart/form-data"))
    }
}
