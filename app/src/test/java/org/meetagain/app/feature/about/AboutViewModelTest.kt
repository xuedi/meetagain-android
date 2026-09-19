package org.meetagain.app.feature.about

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.meetagain.app.AppInfo
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.ApiError

class AboutViewModelTest {
    private val server = MockWebServer()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(): AboutViewModel {
        val baseUrl = server.url("/").toString().trimEnd('/')
        val api = ApiClient(baseUrl, OkHttpClient(), Json { ignoreUnknownKeys = true }, languageTag = { "en" })
        return AboutViewModel(api, AppInfo(version = "0.1.0", testBuild = true, baseUrl = baseUrl))
    }

    @Test
    fun `a reachable server is reported`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())
        viewModel().state.test {
            assertEquals(ServerCheck.Checking, awaitItem().serverCheck)
            assertEquals(ServerCheck.Reachable, awaitItem().serverCheck)
        }
    }

    @Test
    fun `a failed check can be retried`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())
        server.enqueue(MockResponse.Builder().body("""{"status":"OK"}""").build())
        val viewModel = viewModel()
        viewModel.state.test {
            assertEquals(ServerCheck.Checking, awaitItem().serverCheck)
            assertEquals(ServerCheck.Failed(ApiError.Http(500)), awaitItem().serverCheck)
            viewModel.checkServer()
            assertEquals(ServerCheck.Checking, awaitItem().serverCheck)
            assertEquals(ServerCheck.Reachable, awaitItem().serverCheck)
        }
    }
}
