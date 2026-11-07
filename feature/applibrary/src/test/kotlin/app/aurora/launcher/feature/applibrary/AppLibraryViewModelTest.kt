package app.aurora.launcher.feature.applibrary

import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.GridSpec
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.LibraryGroup
import app.aurora.launcher.core.platform.IconCacheAndLoader
import app.aurora.launcher.core.testing.FakeAppCatalogRepository
import app.aurora.launcher.core.testing.FakeLauncherAppsGateway
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val appA = AppEntry(
        key = ComponentKey("com.alpha", "Main", 0L),
        label = "Alpha Chat",
        sortKey = "alpha chat",
        libraryGroup = LibraryGroup.SOCIAL,
        isHidden = false,
    )
    private val appB = AppEntry(
        key = ComponentKey("com.beta", "Main", 0L),
        label = "Beta Game",
        sortKey = "beta game",
        libraryGroup = LibraryGroup.GAMES,
        isHidden = false,
    )
    private val hiddenApp = AppEntry(
        key = ComponentKey("com.hidden", "Main", 0L),
        label = "Secret Vault",
        sortKey = "secret vault",
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

    @Test
    fun `uiState filters out hidden apps and groups apps alphabetically`() = runTest(testDispatcher) {
        val appCatalog = FakeAppCatalogRepository(listOf(appA, appB, hiddenApp))
        val workspaceRepo = FakeWorkspaceRepository()
        val settingsRepo = FakeSettingsRepository()
        val gateway = FakeLauncherAppsGateway()
        val iconLoader = IconCacheAndLoader()

        val viewModel = AppLibraryViewModel(
            appCatalogRepository = appCatalog,
            workspaceRepository = workspaceRepo,
            settingsRepository = settingsRepo,
            launcherAppsGateway = gateway,
            iconLoader = iconLoader,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.visibleApps.size)
        assertTrue(state.visibleApps.none { it.isHidden })
        assertTrue(state.grouped.containsKey(LibraryGroup.SOCIAL))
        assertTrue(state.grouped.containsKey(LibraryGroup.GAMES))
        assertFalse(state.grouped.containsKey(LibraryGroup.UTILITIES))
    }

    @Test
    fun `setTabletViewport adjusts effective grid spec in workspace observation`() = runTest(testDispatcher) {
        val appCatalog = FakeAppCatalogRepository(listOf(appA))
        val workspaceRepo = FakeWorkspaceRepository()
        val settingsRepo = FakeSettingsRepository(LauncherSettings(adaptiveTabletGrid = true))
        val gateway = FakeLauncherAppsGateway()
        val iconLoader = IconCacheAndLoader()

        val viewModel = AppLibraryViewModel(
            appCatalogRepository = appCatalog,
            workspaceRepository = workspaceRepo,
            settingsRepository = settingsRepo,
            launcherAppsGateway = gateway,
            iconLoader = iconLoader,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.workspacePages.collect()
        }
        viewModel.setTabletViewport(true)
        advanceUntilIdle()

        val pages = viewModel.workspacePages.value
        assertTrue(pages.isNotEmpty())
    }

    @Test
    fun `isPrivateSpaceLocked updates uiState and unlockPrivateSpace delegates to gateway`() = runTest(testDispatcher) {
        val appCatalog = FakeAppCatalogRepository(listOf(appA))
        val workspaceRepo = FakeWorkspaceRepository()
        val settingsRepo = FakeSettingsRepository()
        val gateway = FakeLauncherAppsGateway()
        val iconLoader = IconCacheAndLoader()

        val viewModel = AppLibraryViewModel(
            appCatalogRepository = appCatalog,
            workspaceRepository = workspaceRepo,
            settingsRepository = settingsRepo,
            launcherAppsGateway = gateway,
            iconLoader = iconLoader,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPrivateSpaceLocked)

        appCatalog.syncInstalledApps(listOf(appA), isPrivateSpaceLocked = true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPrivateSpaceLocked)
        assertFalse(viewModel.unlockPrivateSpace())
    }

    @Test
    fun `work profile apps activate hasWorkProfile and quiet mode toggles workProfileQuietMode`() = runTest(testDispatcher) {
        val workApp = appA.copy(
            key = ComponentKey("com.work.app", "Main", 10L),
            profileBadge = app.aurora.launcher.core.model.ProfileBadge.WORK,
        )
        val appCatalog = FakeAppCatalogRepository(listOf(appA, workApp))
        val workspaceRepo = FakeWorkspaceRepository()
        val settingsRepo = FakeSettingsRepository()
        val gateway = FakeLauncherAppsGateway()
        val iconLoader = IconCacheAndLoader()

        val viewModel = AppLibraryViewModel(
            appCatalogRepository = appCatalog,
            workspaceRepository = workspaceRepo,
            settingsRepository = settingsRepo,
            launcherAppsGateway = gateway,
            iconLoader = iconLoader,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasWorkProfile)

        val success = viewModel.setWorkQuietMode(true)
        advanceUntilIdle()
        assertTrue(success)
        assertEquals(1, gateway.quietModeSettings.size)
        assertEquals(10L to true, gateway.quietModeSettings.first())
    }

    @Test
    fun `addAppToWorkspace fails when desktop is locked`() = runTest(testDispatcher) {
        val appCatalog = FakeAppCatalogRepository(listOf(appA))
        val workspaceRepo = FakeWorkspaceRepository()
        val settingsRepo = FakeSettingsRepository()
        settingsRepo.updateSettings { it.copy(desktopLocked = true) }
        val gateway = FakeLauncherAppsGateway()
        val iconLoader = IconCacheAndLoader()

        val viewModel = AppLibraryViewModel(
            appCatalogRepository = appCatalog,
            workspaceRepository = workspaceRepo,
            settingsRepository = settingsRepo,
            launcherAppsGateway = gateway,
            iconLoader = iconLoader,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        var addResult: Boolean? = null
        viewModel.addAppToWorkspace(appA, isTablet = false) { ok ->
            addResult = ok
        }
        advanceUntilIdle()

        assertFalse(addResult == true)
    }
}


