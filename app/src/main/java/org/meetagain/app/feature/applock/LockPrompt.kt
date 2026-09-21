package org.meetagain.app.feature.applock

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/** Whether this phone can lock the app, and what the member would have to set up first when it cannot. */
enum class LockAvailability {
    Available,
    NoScreenLock,

    /** Below Android 11 the lock needs a fingerprint; a screen PIN alone is not enough there. */
    NoFingerprint,
    Unsupported
}

/** Android 11 is the first version whose Keystore key accepts the screen PIN as well as a fingerprint. */
val pinAllowed: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

private fun authenticators(): Int = if (pinAllowed) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_STRONG

fun lockAvailability(context: Context): LockAvailability =
    when (BiometricManager.from(context).canAuthenticate(authenticators())) {
        BiometricManager.BIOMETRIC_SUCCESS -> LockAvailability.Available

        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            if (context.getSystemService<KeyguardManager>()?.isDeviceSecure == true) {
                LockAvailability.NoFingerprint
            } else {
                LockAvailability.NoScreenLock
            }

        else -> LockAvailability.Unsupported
    }

sealed interface PromptResult {
    /** The cipher Android hands back once the member passed; only this one can use the locked key. */
    data class Passed(val cipher: Cipher) : PromptResult

    data object Stopped : PromptResult

    data object Failed : PromptResult
}

/** Android's own prompt for [cipher]. The app never sees the fingerprint: Android checks it and releases the key. */
fun FragmentActivity.showLockPrompt(title: String, cancel: String, cipher: Cipher, onDone: (PromptResult) -> Unit) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onDone(result.cryptoObject?.cipher?.let(PromptResult::Passed) ?: PromptResult.Failed)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onDone(if (errorCode in STOPPED) PromptResult.Stopped else PromptResult.Failed)
        }

        // A finger that was not recognised leaves the prompt open for another try, so there is nothing to do here.
    }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setAllowedAuthenticators(authenticators())
        .apply { if (!pinAllowed) setNegativeButtonText(cancel) }
        .build()
    BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
        .authenticate(info, BiometricPrompt.CryptoObject(cipher))
}

private val STOPPED = setOf(
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_CANCELED
)
