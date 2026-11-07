package app.aurora.launcher.core.data.repository

import app.aurora.launcher.core.common.SystemResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class EncryptedBackupEnvelope(
    val auroraBackupEncrypted: Boolean = true,
    val salt: String,
    val iv: String,
    val ciphertext: String,
    val iterations: Int = BackupCryptoEngine.PBKDF2_ITERATIONS,
    val version: Int = 1,
)

object BackupCryptoEngine {
    const val PBKDF2_ITERATIONS = 120_000
    const val KEY_LENGTH_BITS = 256
    const val SALT_LENGTH_BYTES = 16
    const val IV_LENGTH_BYTES = 12
    const val TAG_LENGTH_BITS = 128

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val secureRandom = SecureRandom()

    fun isEncrypted(rawJson: String): Boolean {
        if (!rawJson.contains("\"auroraBackupEncrypted\"")) return false
        return try {
            val envelope = json.decodeFromString<EncryptedBackupEnvelope>(rawJson)
            envelope.auroraBackupEncrypted
        } catch (_: Exception) {
            false
        }
    }

    fun encrypt(plainText: String, passphrase: CharArray): String {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)

        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val keySpec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKeyBytes = keyFactory.generateSecret(keySpec).encoded
        keySpec.clearPassword()
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")
        java.util.Arrays.fill(secretKeyBytes, 0.toByte())

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val envelope = EncryptedBackupEnvelope(
            auroraBackupEncrypted = true,
            salt = Base64.getEncoder().encodeToString(salt),
            iv = Base64.getEncoder().encodeToString(iv),
            ciphertext = Base64.getEncoder().encodeToString(cipherBytes),
            iterations = PBKDF2_ITERATIONS,
            version = 1,
        )
        return json.encodeToString(envelope)
    }

    fun decrypt(encryptedJson: String, passphrase: CharArray): SystemResult<String> {
        return try {
            val envelope = json.decodeFromString<EncryptedBackupEnvelope>(encryptedJson)
            if (!envelope.auroraBackupEncrypted) {
                return SystemResult.Failure("Payload is not an encrypted backup envelope")
            }
            if (envelope.version != 1) {
                return SystemResult.Failure("Unsupported encrypted backup envelope version: ${envelope.version}")
            }
            if (envelope.iterations !in 10_000..500_000) {
                return SystemResult.Failure("Unsupported PBKDF2 iteration count: ${envelope.iterations}")
            }

            val salt = Base64.getDecoder().decode(envelope.salt)
            if (salt.size !in 16..64) {
                return SystemResult.Failure("Invalid cryptographic salt length: ${salt.size}")
            }

            val iv = Base64.getDecoder().decode(envelope.iv)
            if (iv.size !in 12..16) {
                return SystemResult.Failure("Invalid cryptographic IV length: ${iv.size}")
            }

            val cipherBytes = Base64.getDecoder().decode(envelope.ciphertext)
            if (cipherBytes.isEmpty() || cipherBytes.size > 5 * 1024 * 1024) {
                return SystemResult.Failure("Invalid ciphertext payload size")
            }

            val keySpec = PBEKeySpec(passphrase, salt, envelope.iterations, KEY_LENGTH_BITS)
            val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKeyBytes = keyFactory.generateSecret(keySpec).encoded
            keySpec.clearPassword()
            val secretKey = SecretKeySpec(secretKeyBytes, "AES")
            java.util.Arrays.fill(secretKeyBytes, 0.toByte())

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(cipherBytes)
            SystemResult.Success(String(decryptedBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            SystemResult.Failure("Decryption failed. Incorrect passphrase or corrupted file.", e)
        }
    }
}
