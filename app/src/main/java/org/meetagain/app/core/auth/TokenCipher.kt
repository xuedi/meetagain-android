package org.meetagain.app.core.auth

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Turns the access token into something safe to keep on disk, and back. */
interface TokenCipher {
    fun encrypt(value: String): String

    /** Null when the stored text cannot be read any more, for instance after the key was replaced. */
    fun decrypt(stored: String): String?
}

/** AES-GCM with a key the app never sees the bytes of, and a new initialisation vector for every value. */
class AesGcmTokenCipher(private val key: () -> SecretKey) : TokenCipher {
    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return sealText(cipher, value)
    }

    override fun decrypt(stored: String): String? {
        val (iv, _) = sealedParts(stored) ?: return null
        return tolerant {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            openText(cipher, stored)
        }
    }
}

/**
 * The token under a key that only works once the member has passed Android's own check - fingerprint, or the screen
 * PIN from Android 11 on. Every use goes through `BiometricPrompt`: this hands out a [Cipher] set up for the prompt,
 * and finishes with the one the prompt gives back.
 */
interface LockedCipher {
    /** Ready to encrypt after the prompt. A key that went stale is replaced first, so turning the lock on works. */
    fun forEncryption(): Cipher

    /** Null when the key is gone: a fingerprint was added or removed, or the screen lock was taken away. */
    fun forDecryption(stored: String): Cipher?

    fun seal(cipher: Cipher, value: String): String

    /** Null when the stored text cannot be opened with [cipher]. */
    fun open(cipher: Cipher, stored: String): String?

    fun delete()
}

class KeystoreLockedCipher(private val alias: String = "session-token-locked") : LockedCipher {
    override fun forEncryption(): Cipher = try {
        encryptingWith(lockedKey(alias))
    } catch (_: KeyPermanentlyInvalidatedException) {
        delete()
        encryptingWith(lockedKey(alias))
    }

    override fun forDecryption(stored: String): Cipher? {
        val (iv, _) = sealedParts(stored) ?: return null
        val key = existingKey(alias) ?: return null
        return try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            null
        }
    }

    override fun seal(cipher: Cipher, value: String): String = sealText(cipher, value)

    override fun open(cipher: Cipher, stored: String): String? = tolerant { openText(cipher, stored) }

    override fun delete() {
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun encryptingWith(key: SecretKey) = Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.ENCRYPT_MODE, key)
    }
}

/** The stored text is the initialisation vector and the ciphertext, both base64 and separated by a colon. */
internal fun sealText(cipher: Cipher, value: String): String =
    encode(cipher.iv) + SEPARATOR + encode(cipher.doFinal(value.toByteArray()))

internal fun openText(cipher: Cipher, stored: String): String? {
    val (_, encrypted) = sealedParts(stored) ?: return null
    return String(cipher.doFinal(encrypted))
}

internal fun sealedParts(stored: String): Pair<ByteArray, ByteArray>? {
    val (iv, encrypted) = stored.split(SEPARATOR).takeIf { it.size == 2 } ?: return null
    return runCatching { decode(iv) to decode(encrypted) }.getOrNull()
}

private inline fun <T> tolerant(block: () -> T?): T? = try {
    block()
} catch (_: GeneralSecurityException) {
    null
} catch (_: IllegalArgumentException) {
    null
} catch (_: ProviderException) {
    // A stored value that was cut short reaches the provider as a buffer of the wrong size.
    null
}

private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val TAG_BITS = 128
private const val SEPARATOR = ":"

/**
 * The key lives in the Android Keystore, which generates it on first use and never hands it out: the app can ask for
 * encryption and decryption, but the key itself cannot leave the device, not even to a backup.
 */
fun keystoreCipher(alias: String = "session-token"): TokenCipher = AesGcmTokenCipher { keystoreKey(alias) }

private fun keystoreKey(alias: String): SecretKey = existingKey(alias) ?: generate(spec(alias).build())

/**
 * Per use, never for a period after an unlock: the key opens for exactly one prompt. Android 11 is the first version
 * that lets one key accept the screen PIN as well as a fingerprint; before it, the fingerprint alone.
 */
private fun lockedKey(alias: String): SecretKey = existingKey(alias) ?: generate(
    spec(alias)
        .setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
            }
        }
        .build()
)

private fun spec(alias: String) =
    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(KEY_BITS)

private fun generate(spec: KeyGenParameterSpec): SecretKey =
    KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }.generateKey()

private fun existingKey(alias: String): SecretKey? =
    (keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

private const val PROVIDER = "AndroidKeyStore"
private const val KEY_BITS = 256
