package app.aurora.launcher.core.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformGatewaysTest {

    @Test
    fun `IconPackXmlParser resolves relative and absolute component names`() {
        val relative = IconPackXmlParser.parseComponentAttr("ComponentInfo{com.example.chat/.MainActivity}")
        assertEquals("com.example.chat" to "com.example.chat.MainActivity", relative)

        val absolute = IconPackXmlParser.parseComponentAttr("ComponentInfo{com.example.maps/com.google.MapsActivity}")
        assertEquals("com.example.maps" to "com.google.MapsActivity", absolute)

        assertNull(IconPackXmlParser.parseComponentAttr("InvalidComponentString"))
    }

    @Test
    fun `WidgetSpanCalculator maps dp bounds into clamped grid cells`() {
        assertEquals(1, WidgetSpanCalculator.dpToCells(40, maxCells = 4))
        assertEquals(2, WidgetSpanCalculator.dpToCells(110, maxCells = 4))
        assertEquals(4, WidgetSpanCalculator.dpToCells(250, maxCells = 4))
        assertEquals(4, WidgetSpanCalculator.dpToCells(500, maxCells = 4))
    }

    @Test
    fun `NotificationBadgeStore excludes ongoing and group summary notifications and clears on revoke`() {
        val store = NotificationBadgeStore()
        store.onConnected(
            listOf(
                RawBadgeEntry(packageName = "com.chat", isOngoing = false, isGroupSummary = false, number = 3),
                RawBadgeEntry(packageName = "com.chat", isOngoing = false, isGroupSummary = true, number = 3),
                RawBadgeEntry(packageName = "com.music", isOngoing = true, isGroupSummary = false, number = 1),
                RawBadgeEntry(packageName = "com.mail", isOngoing = false, isGroupSummary = false, number = 0),
            ),
        )

        assertTrue(store.listenerConnected.value)
        assertEquals(mapOf("com.chat" to 3, "com.mail" to 1), store.countsByPackage.value)

        store.onDisconnected(permissionStillGranted = false)
        assertFalse(store.listenerConnected.value)
        assertTrue(store.countsByPackage.value.isEmpty())
        assertTrue(store.listenerRevokedNoticePending.value)

        store.consumeRevokedNotice()
        assertFalse(store.listenerRevokedNoticePending.value)
    }

    @Test
    fun `BiometricAuthGateway omits BIOMETRIC_WEAK when paired with DEVICE_CREDENTIAL below API 30`() {
        val strongAndDevice = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val weakAndDevice = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

        assertEquals(strongAndDevice, BiometricAuthGateway.resolveAllowedAuthenticators(28))
        assertEquals(strongAndDevice, BiometricAuthGateway.resolveAllowedAuthenticators(29))
        assertEquals(weakAndDevice, BiometricAuthGateway.resolveAllowedAuthenticators(30))
    }
}
