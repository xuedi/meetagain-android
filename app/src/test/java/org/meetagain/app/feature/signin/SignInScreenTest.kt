package org.meetagain.app.feature.signin

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meetagain.app.R
import org.meetagain.app.core.ui.theme.MeetAgainTheme
import org.meetagain.app.testing.onAllNodesWithTextExists
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class SignInScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun text(id: Int) = context.getString(id)

    private fun show(
        state: SignInState = SignInState(),
        onEmail: (String) -> Unit = {},
        onPassword: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        onOpenWebsite: (String) -> Unit = {}
    ) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            MeetAgainTheme {
                SignInScreen(
                    state = state,
                    onEmail = onEmail,
                    onPassword = onPassword,
                    onSubmit = onSubmit,
                    onOpenWebsite = onOpenWebsite,
                    onLookAround = {},
                    onOpenAbout = {}
                )
            }
        }
    }

    private fun field(type: ContentType) =
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, type))

    @Test
    fun `the fields tell the password manager what they are`() {
        show()
        field(ContentType.EmailAddress).assertExists()
        field(ContentType.Password).assertExists()
    }

    @Test
    fun `signing in is off until both fields are filled`() {
        show(SignInState(email = "crystal.liu@example.org"))
        compose.onNodeWithText(text(R.string.signin_submit)).assertIsNotEnabled()
    }

    @Test
    fun `what is typed shows in the field and turns signing in on`() {
        compose.setContent {
            MeetAgainTheme {
                var state by remember { mutableStateOf(SignInState()) }
                SignInScreen(
                    state = state,
                    onEmail = { state = state.copy(email = it) },
                    onPassword = { state = state.copy(password = it) },
                    onSubmit = {},
                    onOpenWebsite = {},
                    onLookAround = {},
                    onOpenAbout = {}
                )
            }
        }
        field(ContentType.EmailAddress).performTextInput("crystal.liu@example.org")
        field(ContentType.Password).performTextInput("1234")
        compose.onNodeWithText("crystal.liu@example.org").assertExists()
        compose.onNodeWithText(text(R.string.signin_submit)).assertIsEnabled()
    }

    @Test
    fun `an account that waits for approval is told so, with the way to the website`() {
        var opened: String? = null
        show(SignInState(problem = SignInProblem.PendingApproval), onOpenWebsite = { opened = it })
        compose.onNodeWithText(text(R.string.signin_error_pending)).assertExists()
        compose.onNodeWithText(text(R.string.signin_open_website)).performClick()
        assertEquals("/profile", opened)
    }

    @Test
    fun `a wrong password says so and offers no website`() {
        show(SignInState(problem = SignInProblem.WrongCredentials))
        compose.onNodeWithText(text(R.string.signin_error_credentials)).assertExists()
        assertFalse(compose.onAllNodesWithTextExists(text(R.string.signin_open_website)))
    }

    @Test
    fun `the human check sends the member to the website's own login`() {
        var opened: String? = null
        show(SignInState(problem = SignInProblem.SignInOnTheWebsite), onOpenWebsite = { opened = it })
        compose.onNodeWithText(text(R.string.signin_error_restricted)).assertExists()
        compose.onNodeWithText(text(R.string.signin_open_website)).performClick()
        assertEquals("/login", opened)
    }

    @Test
    fun `creating an account and a forgotten password both lead to the website`() {
        val opened = mutableListOf<String>()
        show(onOpenWebsite = { opened += it })
        val screen = compose.onNode(hasScrollAction())
        screen.performScrollToNode(hasText(text(R.string.signin_forgot)))
        compose.onNodeWithText(text(R.string.signin_forgot)).performClick()
        screen.performScrollToNode(hasText(text(R.string.signin_create)))
        compose.onNodeWithText(text(R.string.signin_create)).performClick()
        assertEquals(listOf("/reset", "/register"), opened)
    }

    @Test
    fun `one sentence says where the password goes`() {
        show()
        compose.onNodeWithText(text(R.string.signin_password_note)).assertExists()
    }
}
