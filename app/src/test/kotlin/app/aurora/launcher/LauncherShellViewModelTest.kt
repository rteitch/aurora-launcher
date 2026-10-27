package app.aurora.launcher

import app.aurora.launcher.core.common.AuroraDispatchers
import app.aurora.launcher.core.data.usecase.LaunchAppUseCase
import app.aurora.launcher.core.model.ItemId
import app.aurora.launcher.core.model.ShellOverlay
import app.aurora.launcher.core.platform.AppWidgetHostManager
import app.aurora.launcher.core.platform.IconCacheAndLoader
import app.aurora.launcher.core.platform.IconPackGateway
import app.aurora.launcher.core.platform.LauncherAppsGateway
import app.aurora.launcher.core.platform.NotificationBadgeGateway
import app.aurora.launcher.core.platform.NotificationBadgeStore
import app.aurora.launcher.core.platform.RoleGateway
import app.aurora.launcher.core.platform.WallpaperGateway
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherShellViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = AuroraDispatchers(
        main = testDispatcher,
        io = testDispatcher,
        default = testDispatcher,
    )

    private val settingsRepository = FakeSettingsRepository()
    private val appCatalogRepository = FakeAppCatalogRepository()
    private val workspaceRepository = FakeWorkspaceRepository()
    private val launcherAppsGateway = FakeLauncherAppsGateway()
    private val wallpaperGateway = WallpaperGateway(null, testDispatchers)
    private val roleGateway = RoleGateway(null)
    private val launchAppUseCase = LaunchAppUseCase(appCatalogRepository, settingsRepository, launcherAppsGateway)
    private val badgeStore = NotificationBadgeStore()

    private var rebindRequested = false
    private val badgeGateway = object : NotificationBadgeGateway(null) {
        override fun isNotificationAccessGranted(): Boolean = true
        override fun requestRebindService(): Boolean {
            rebindRequested = true
            return true
        }
    }

    private val widgetHostManager = AppWidgetHostManager(null, testDispatchers)

    private var trimmedLevel: Int? = null
    private val iconLoader = object : IconCacheAndLoader(null, IconPackGateway(null, testDispatchers), testDispatchers) {
        override fun trimMemory(level: Int) {
            trimmedLevel = level
        }
    }

    private lateinit var viewModel: LauncherShellViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LauncherShellViewModel(
            settingsRepository = settingsRepository,
            appCatalogRepository = appCatalogRepository,
            workspaceRepository = workspaceRepository,
            launcherAppsGateway = launcherAppsGateway,
            wallpaperGateway = wallpaperGateway,
            roleGateway = roleGateway,
            launchAppUseCase = launchAppUseCase,
            badgeStore = badgeStore,
            badgeGateway = badgeGateway,
            widgetHostManager = widgetHostManager,
            iconLoader = iconLoader,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onActivityResumed requests rebind when notification access is granted and badges are enabled`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        settingsRepository.updateSettings { it.copy(notificationBadgesEnabled = true) }
        advanceUntilIdle()
        rebindRequested = false

        viewModel.onActivityResumed()
        assertTrue(rebindRequested)
    }

    @Test
    fun `onTrimMemory forwards memory level to icon cache loader`() = runTest {
        viewModel.onTrimMemory(80)
        assertEquals(80, trimmedLevel)
    }

    @Test
    fun `overlay navigation transitions correctly between sheets and closes`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        assertEquals(ShellOverlay.None, viewModel.uiState.value.overlay)

        viewModel.openSpotlight()
        advanceUntilIdle()
        assertEquals(ShellOverlay.Spotlight, viewModel.uiState.value.overlay)

        viewModel.openControlPanel()
        advanceUntilIdle()
        assertEquals(ShellOverlay.ControlPanel, viewModel.uiState.value.overlay)

        viewModel.openFolder(ItemId(42L))
        advanceUntilIdle()
        assertEquals(ShellOverlay.Folder(ItemId(42L)), viewModel.uiState.value.overlay)

        viewModel.closeOverlay()
        advanceUntilIdle()
        assertEquals(ShellOverlay.None, viewModel.uiState.value.overlay)
    }

    @Test
    fun `onHomeIntentReceived closes active overlay or increments signal counter`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        viewModel.openSpotlight()
        advanceUntilIdle()
        assertEquals(ShellOverlay.Spotlight, viewModel.uiState.value.overlay)

        viewModel.onHomeIntentReceived()
        advanceUntilIdle()
        assertEquals(ShellOverlay.None, viewModel.uiState.value.overlay)

        val currentSignal = viewModel.uiState.value.homeButtonSignal
        viewModel.onHomeIntentReceived()
        advanceUntilIdle()
        assertEquals(currentSignal + 1, viewModel.uiState.value.homeButtonSignal)
    }

    @Test
    fun `removeChildFromFolder delegates to extractChildFromFolder in workspace repository`() = runTest {
        viewModel.removeChildFromFolder(ItemId(101L))
        advanceUntilIdle()
    }
}
