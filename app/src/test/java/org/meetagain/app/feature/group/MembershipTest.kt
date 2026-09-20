package org.meetagain.app.feature.group

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.meetagain.app.core.data.MembershipStatus
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.testing.MainDispatcherRule
import org.meetagain.app.testing.MemoryAnswers
import org.meetagain.app.testing.fixture
import org.meetagain.app.testing.json
import org.meetagain.app.testing.memberRepository
import org.meetagain.app.testing.noContent
import org.meetagain.app.testing.publicRepository
import org.meetagain.app.testing.serve

class MembershipTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val server = MockWebServer()
    private val answers = MemoryAnswers()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun viewModel(slug: String = SLUG) = mainDispatcher.keep(
        GroupViewModel(
            publicRepository(server, answers, memberId = 4),
            memberRepository(server, answers),
            signedIn = true,
            slug = slug
        )
    )

    private fun group(extra: Map<String, List<mockwebserver3.MockResponse>> = emptyMap()) = mapOf(
        "/api/v1/groups/$SLUG" to listOf(json(fixture("group-detail.json"))),
        "/api/v1/events" to listOf(json(fixture("events.json"))),
        "/api/v1/me/groups" to listOf(json(fixture("me-groups.json"))),
        "/api/v1/memberships/invitations" to listOf(json(fixture("invitations.json")))
    ) + extra

    @Test
    fun `a membership the member has shows as their standing`() = runTest {
        server.serve(group())
        viewModel(slug = "berlin-cinephile-club").standing.test {
            var membership = awaitItem().membership
            while (membership == null) membership = awaitItem().membership
            assertEquals(MembershipStatus.Approved, membership.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an invitation for this group shows with it`() = runTest {
        server.serve(group())
        // The membership and the invitations are two reads, so they land one after the other.
        viewModel(slug = "berlin-cinephile-club").standing.test {
            var invitation = awaitItem().invitation
            while (invitation == null) invitation = awaitItem().invitation
            assertEquals(7, invitation.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `joining asks the platform's question only when the server does`() = runTest {
        server.serve(
            group(
                mapOf(
                    "/api/v1/memberships/groups/$SLUG" to listOf(
                        json("""{"error":"platform_crossing_required"}""", 409),
                        json(fixture("membership.json"))
                    ),
                    "/api/v1/me/events" to listOf(json(fixture("me-events.json")))
                )
            )
        )
        val viewModel = viewModel()
        viewModel.needsConsent.test {
            assertEquals(false, awaitItem())
            viewModel.join()
            assertEquals(true, awaitItem())
            viewModel.join(mailConsent = false)
            assertEquals(false, awaitItem())
        }
        val consented = List(server.requestCount) { server.takeRequest() }
            .last { it.url.encodedPath == "/api/v1/memberships/groups/$SLUG" }
        assertEquals("""{"platformMailConsent":false}""", consented.body?.utf8())
    }

    @Test
    fun `each refusal to leave keeps its own code`() = runTest {
        for (code in listOf("last_owner", "blocked_in_group", "may_not_leave_platform")) {
            server.serve(group(mapOf("/api/v1/memberships/groups/$SLUG" to listOf(json("""{"error":"$code"}""", 409)))))
            val viewModel = viewModel()
            viewModel.problem.test {
                assertNull(awaitItem())
                viewModel.leave()
                assertEquals(membershipProblemOf(ApiError.Http(409, code)), awaitItem())
            }
        }
    }

    @Test
    fun `leaving that works reports nothing to say`() = runTest {
        server.serve(
            group(
                mapOf(
                    "/api/v1/memberships/groups/$SLUG" to listOf(noContent()),
                    "/api/v1/me/events" to listOf(json(fixture("me-events.json")))
                )
            )
        )
        val viewModel = viewModel()
        viewModel.busy.test {
            assertEquals(false, awaitItem())
            viewModel.leave()
            assertEquals(true, awaitItem())
            assertEquals(false, awaitItem())
        }
        assertNull(viewModel.problem.value)
    }

    @Test
    fun `every code the server can send has its own sentence`() {
        val codes = listOf(
            "group_not_joinable",
            "blocked_in_group",
            "last_owner",
            "may_not_leave_platform",
            "membership_rejected",
            "invitation_not_found",
            "platform_crossing_required",
            "not_found"
        )
        val problems = codes.map { membershipProblemOf(ApiError.Http(409, it)) }
        assertEquals(codes.size, problems.toSet().size)
        assertTrue(problems.none { it == MembershipProblem.Failed })
    }

    private companion object {
        const val SLUG = "my-community"
    }
}
