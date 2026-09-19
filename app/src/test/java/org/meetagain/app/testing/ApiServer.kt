package org.meetagain.app.testing

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

fun fixture(name: String): String =
    checkNotNull(object {}.javaClass.getResource("/api/$name")) { "no fixture $name" }.readText()

fun json(body: String, status: Int = 200): MockResponse =
    MockResponse.Builder().code(status).setHeader("Content-Type", "application/json").body(body).build()

/**
 * Answers by path, so requests a screen sends in parallel get the right body in any order. [routes] maps an encoded
 * path to the answers for it, one per request; the last one repeats. Anything else is a 404.
 */
fun MockWebServer.serve(routes: Map<String, List<MockResponse>>) {
    val remaining = routes.mapValues { it.value.toMutableList() }
    dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val answers = remaining[request.url.encodedPath] ?: return json(fixture("error-not-found.json"), 404)
            return synchronized(answers) { if (answers.size > 1) answers.removeAt(0) else answers.first() }
        }
    }
}
