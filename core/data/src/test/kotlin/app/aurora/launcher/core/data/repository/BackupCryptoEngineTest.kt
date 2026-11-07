package app.aurora.launcher.core.data.repository

import app.aurora.launcher.core.common.SystemResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoEngineTest {

    private val sampleJson = """{"schemaVersion":1,"exportedAtEpochMs":1760000000000,"gridColumns":4,"gridRows":6,"dockSlots":4,"pages":[],"dockItems":[]}"""
    private val passphrase = "super-secret-enterprise-passphrase"

    @Test
    fun `isEncrypted returns true for encrypted envelope and false for plaintext`() {
        assertFalse(BackupCryptoEngine.isEncrypted(sampleJson))
        assertFalse(BackupCryptoEngine.isEncrypted("{invalid json"))
        assertFalse(BackupCryptoEngine.isEncrypted(""))

        val encrypted = BackupCryptoEngine.encrypt(sampleJson, passphrase.toCharArray())
        assertTrue(BackupCryptoEngine.isEncrypted(encrypted))
    }

    @Test
    fun `encrypt and decrypt roundtrip restores original plaintext`() {
        val encrypted = BackupCryptoEngine.encrypt(sampleJson, passphrase.toCharArray())
        assertTrue(encrypted.contains("auroraBackupEncrypted"))
        assertTrue(encrypted.contains("salt"))
        assertTrue(encrypted.contains("iv"))
        assertTrue(encrypted.contains("ciphertext"))

        val decryptResult = BackupCryptoEngine.decrypt(encrypted, passphrase.toCharArray())
        assertTrue(decryptResult is SystemResult.Success)
        val decryptedText = (decryptResult as SystemResult.Success).data
        assertEquals(sampleJson, decryptedText)
    }

    @Test
    fun `decrypt with incorrect passphrase returns Failure without crashing`() {
        val encrypted = BackupCryptoEngine.encrypt(sampleJson, passphrase.toCharArray())
        val wrongPassResult = BackupCryptoEngine.decrypt(encrypted, "wrong-password".toCharArray())
        assertTrue(wrongPassResult is SystemResult.Failure)
    }

    @Test
    fun `decrypt with corrupted ciphertext returns Failure`() {
        val encrypted = BackupCryptoEngine.encrypt(sampleJson, passphrase.toCharArray())
        val corrupted = encrypted.replace("ciphertext\":\"", "ciphertext\":\"corrupted_data_")
        val result = BackupCryptoEngine.decrypt(corrupted, passphrase.toCharArray())
        assertTrue(result is SystemResult.Failure)
    }

    @Test
    fun `decrypt rejects malicious or out-of-bounds PBKDF2 iterations and invalid versions`() {
        val encrypted = BackupCryptoEngine.encrypt(sampleJson, passphrase.toCharArray())
        
        // Excessive iterations (DoS attack protection)
        val excessiveIterations = encrypted.replace("\"iterations\":120000", "\"iterations\":1000000")
        val resExcessive = BackupCryptoEngine.decrypt(excessiveIterations, passphrase.toCharArray())
        assertTrue(resExcessive is SystemResult.Failure)

        // Weak iterations (< 10,000)
        val weakIterations = encrypted.replace("\"iterations\":120000", "\"iterations\":500")
        val resWeak = BackupCryptoEngine.decrypt(weakIterations, passphrase.toCharArray())
        assertTrue(resWeak is SystemResult.Failure)

        // Unsupported version
        val badVersion = encrypted.replace("\"version\":1", "\"version\":99")
        val resVersion = BackupCryptoEngine.decrypt(badVersion, passphrase.toCharArray())
        assertTrue(resVersion is SystemResult.Failure)
    }
}
