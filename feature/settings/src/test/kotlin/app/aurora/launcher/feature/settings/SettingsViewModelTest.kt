package app.aurora.launcher.feature.settings

import app.aurora.launcher.core.common.AuroraDispatchers
import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.GridPreset
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.LibraryGroup
import app.aurora.launcher.core.model.ThemeMode
import app.aurora.launcher.core.model.WallpaperStyle
import app.aurora.launcher.core.platform.AppWidgetHostManager
import app.aurora.launcher.core.platform.BiometricAuthGateway
import app.aurora.launcher.core.platform.ContactsAndSettingsSearchGateway
import app.aurora.launcher.core.platform.IconCacheAndLoader
import app.aurora.launcher.core.platform.IconPackGateway
import app.aurora.launcher.core.platform.NotificationBadgeGateway
import app.aurora.launcher.core.platform.RoleGateway
import app.aurora.launcher.core.platform.WallpaperGateway
import app.aurora.launcher.core.testing.FakeAppCatalogRepository
import app.aurora.launcher.core.testing.FakeBackupRepository
import app.aurora.launcher.core.testing.FakeSettingsRepository
import app.aurora.launcher.core.testing.FakeWorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val auroraDispatchers = AuroraDispatchers(
        main = testDispatcher,
        io = testDispatcher,
        default = testDispatcher,
    )

    private val hiddenApp = AppEntry(
        key = ComponentKey("app.vault", "MainActivity", 0L),
        label = "Private Vault",
        sortKey = "private vault",
        libraryGroup = LibraryGroup.UTILITIES,
        isHidden = true,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
        appCatalogRepo: FakeAppCatalogRepository = FakeAppCatalogRepository(listOf(hiddenApp)),
        workspaceRepo: FakeWorkspaceRepository = FakeWorkspaceRepository(),
        backupRepo: FakeBackupRepository = FakeBackupRepository(),
    ): SettingsViewModel {
        return SettingsViewModel(
            context = null,
            settingsRepository = settingsRepo,
            appCatalogRepository = appCatalogRepo,
            workspaceRepository = workspaceRepo,
            backupRepository = backupRepo,
            wallpaperGateway = WallpaperGateway(),
            roleGateway = RoleGateway(),
            iconPackGateway = IconPackGateway(),
            iconLoader = IconCacheAndLoader(),
            badgeGateway = NotificationBadgeGateway(),
            contactsAndSettingsGateway = ContactsAndSettingsSearchGateway(),
            biometricGateway = BiometricAuthGateway(),
            widgetHostManager = AppWidgetHostManager(),
            dispatchers = auroraDispatchers,
        )
    }

    @Test
    fun `uiState initializes with settings and hidden apps`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ThemeMode.SYSTEM, state.settings.themeMode)
        assertEquals(1, state.hiddenApps.size)
        assertEquals("Private Vault", state.hiddenApps.first().label)
        assertNull(state.statusBanner)
        assertFalse(state.canUndoLastGridChange)
    }

    @Test
    fun `updateSettings modifies theme mode and wallpaper style`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.updateSettings {
            it.copy(
                themeMode = ThemeMode.DARK,
                wallpaperStyle = WallpaperStyle.SLATE_HORIZON,
            )
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ThemeMode.DARK, state.settings.themeMode)
        assertEquals(WallpaperStyle.SLATE_HORIZON, state.settings.wallpaperStyle)
    }

    @Test
    fun `changeGridPreset updates preset and presents undo banner`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(GridPreset.GRID_4X6, viewModel.uiState.value.settings.gridPreset)

        viewModel.changeGridPreset(GridPreset.GRID_5X5, isTablet = false, noticeMessage = "Grid updated")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(GridPreset.GRID_5X5, state.settings.gridPreset)
        assertEquals("Grid updated", state.statusBanner)
        assertTrue(state.canUndoLastGridChange)

        viewModel.dismissBanner()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.statusBanner)
        assertFalse(viewModel.uiState.value.canUndoLastGridChange)
    }

    @Test
    fun `selectIconPack updates pack in settings`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.settings.selectedIconPackPackage)

        viewModel.selectIconPack("com.test.iconpack")
        advanceUntilIdle()

        assertEquals("com.test.iconpack", viewModel.uiState.value.settings.selectedIconPackPackage)
    }
}
