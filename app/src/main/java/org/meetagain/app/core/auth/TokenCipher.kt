package org.meetagain.app.core.auth

import android.security.keystore.KeyGenParameterSpec
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

/**
 * AES-GCM with a key the app never sees the bytes of. The stored text is the initialisation vector, which is new
 * for every value, and the ciphertext, both base64 and separated by a colon.
 */
class AesGcmTokenCipher(private val key: () -> SecretKey) : TokenCipher {
    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray())
        return encode(cipher.iv) + SEPARATOR + encode(encrypted)
    }

    override fun decrypt(stored: String): String? {
        val (iv, encrypted) = stored.split(SEPARATOR).takeIf { it.size == 2 } ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, decode(iv)))
            String(cipher.doFinal(decode(encrypted)))
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ProviderException) {
            // A stored value that was cut short reaches the provider as a buffer of the wrong size.
            null
        }
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val SEPARATOR = ":"
    }
}

/**
 * The key lives in the Android Keystore, which generates it on first use and never hands it out: the app can ask for
 * encryption and decryption, but the key itself cannot leave the device, not even to a backup.
 */
fun keystoreCipher(alias: String = "session-token"): TokenCipher = AesGcmTokenCipher { keystoreKey(alias) }

private fun keystoreKey(alias: String): SecretKey {
    val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
    (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
    generator.init(
        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .build()
    )
    return generator.generateKey()
}

private const val PROVIDER = "AndroidKeyStore"
private const val KEY_BITS = 256
