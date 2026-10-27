package app.aurora.launcher.core.data.usecase

import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.AppShortcutItem
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.LeafPlacement
import app.aurora.launcher.core.model.PageId
import app.aurora.launcher.core.model.WorkspaceItem
import app.aurora.launcher.core.testing.FakeAppCatalogRepository
import app.aurora.launcher.core.testing.FakeLauncherAppsGateway
import app.aurora.launcher.core.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchAppUseCaseTest {

    private val sampleKey = ComponentKey("com.example.chat", "MainActivity", 0L)
    private val normalApp = AppEntry(
        key = sampleKey,
        label = "Chat",
        sortKey = "chat",
        isLocked = false,
        isSuspended = false,
    )
    private val lockedKey = ComponentKey("com.example.bank", "BankActivity", 0L)
    private val lockedApp = AppEntry(
        key = lockedKey,
        label = "Bank",
        sortKey = "bank",
        isLocked = true,
        isSuspended = false,
    )
    private val suspendedKey = ComponentKey("com.example.work", "WorkActivity", 0L)
    private val suspendedApp = AppEntry(
        key = suspendedKey,
        label = "Work",
        sortKey = "work",
        isLocked = false,
        isSuspended = true,
    )

    @Test
    fun `prepareLaunch returns Success and records launch for normal app`() = runTest {
        val appCatalog = FakeAppCatalogRepository(listOf(normalApp))
        val settingsRepo = FakeSettingsRepository()
        val gateway = FakeLauncherAppsGateway()
        val useCase = LaunchAppUseCase(appCatalog, settingsRepo, gateway)

        val result = useCase.prepareLaunch(LaunchTarget.Component(sampleKey))

        assertTrue(result is LaunchAppResult.Success)
        assertEquals(listOf(sampleKey), gateway.launchedActivities)
    }

    @Test
    fun `prepareLaunch returns Suspended for suspended app`() = runTest {
        val appCatalog = FakeAppCatalogRepository(listOf(suspendedApp))
        val settingsRepo = FakeSettingsRepository()
        val gateway = FakeLauncherAppsGateway()
        val useCase = LaunchAppUseCase(appCatalog, settingsRepo, gateway)

        val result = useCase.prepareLaunch(LaunchTarget.Component(suspendedKey))

        assertTrue(result is LaunchAppResult.Suspended)
        assertEquals("com.example.work", (result as LaunchAppResult.Suspended).packageName)
        assertTrue(gateway.launchedActivities.isEmpty())
    }

    @Test
    fun `prepareLaunch returns BiometricRequired when app is locked and biometric lock enabled`() = runTest {
        val appCatalog = FakeAppCatalogRepository(listOf(lockedApp))
        val settingsRepo = FakeSettingsRepository(LauncherSettings(biometricAppLockEnabled = true))
        val gateway = FakeLauncherAppsGateway()
        val useCase = LaunchAppUseCase(appCatalog, settingsRepo, gateway)

        val result = useCase.prepareLaunch(LaunchTarget.Component(lockedKey))

        assertTrue(result is LaunchAppResult.BiometricRequired)
        assertEquals("Bank", (result as LaunchAppResult.BiometricRequired).appLabel)
        assertTrue(gateway.launchedActivities.isEmpty())
    }

    @Test
    fun `prepareLaunch returns Success when bypassBiometric is true`() = runTest {
        val appCatalog = FakeAppCatalogRepository(listOf(lockedApp))
        val settingsRepo = FakeSettingsRepository(LauncherSettings(biometricAppLockEnabled = true))
        val gateway = FakeLauncherAppsGateway()
        val useCase = LaunchAppUseCase(appCatalog, settingsRepo, gateway)

        val result = useCase.prepareLaunch(LaunchTarget.Component(lockedKey), bypassBiometric = true)

        assertTrue(result is LaunchAppResult.Success)
        assertEquals(listOf(lockedKey), gateway.launchedActivities)
    }

    @Test
    fun `prepareLaunch closes biometric bypass for pinned shortcuts of locked apps`() = runTest {
        val appCatalog = FakeAppCatalogRepository(listOf(lockedApp))
        val settingsRepo = FakeSettingsRepository(LauncherSettings(biometricAppLockEnabled = true))
        val gateway = FakeLauncherAppsGateway()
        val useCase = LaunchAppUseCase(appCatalog, settingsRepo, gateway)

        val pinnedItem = WorkspaceItem.PinnedShortcut(
            id = app.aurora.launcher.core.model.ItemId(50L),
            placement = LeafPlacement.Desktop(PageId(1L), 0, 0),
            packageName = lockedKey.packageName,
            shortcutId = "send_money",
            userSerial = 0L,
            label = "Send Money",
        )

        val result = useCase.prepareLaunch(LaunchTarget.Pinned(pinnedItem))

        assertTrue(result is LaunchAppResult.BiometricRequired)
        assertTrue(gateway.launchedShortcuts.isEmpty())

        // Once authenticated, execute directly
        val executed = useCase.executeLaunch(LaunchTarget.Pinned(pinnedItem))
        assertTrue(executed is LaunchAppResult.Success)
        assertEquals(listOf(Triple(lockedKey.packageName, "send_money", 0L)), gateway.launchedShortcuts)
    }

    @Test
    fun `prepareLaunch closes biometric bypass for dynamic shortcuts of locked apps`() = runTest {
        val appCatalog = FakeAppCatalogRepository(listOf(lockedApp))
        val settingsRepo = FakeSettingsRepository(LauncherSettings(biometricAppLockEnabled = true))
        val gateway = FakeLauncherAppsGateway()
        val useCase = LaunchAppUseCase(appCatalog, settingsRepo, gateway)

        val dynamicShortcut = AppShortcutItem(
            id = "transfer",
            packageName = lockedKey.packageName,
            userSerial = 0L,
            shortLabel = "Transfer",
            longLabel = "Transfer Money",
            isDynamic = true,
        )

        val result = useCase.prepareLaunch(LaunchTarget.Dynamic(dynamicShortcut))

        assertTrue(result is LaunchAppResult.BiometricRequired)
        assertTrue(gateway.launchedShortcuts.isEmpty())

        val executed = useCase.executeLaunch(LaunchTarget.Dynamic(dynamicShortcut))
        assertTrue(executed is LaunchAppResult.Success)
        assertEquals(listOf(Triple(lockedKey.packageName, "transfer", 0L)), gateway.launchedShortcuts)
    }
}
