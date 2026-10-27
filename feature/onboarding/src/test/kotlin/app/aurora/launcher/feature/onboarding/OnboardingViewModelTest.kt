package app.aurora.launcher.feature.onboarding

import app.aurora.launcher.core.model.GridPreset
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.ThemeMode
import app.aurora.launcher.core.platform.RoleGateway
import app.aurora.launcher.core.testing.FakeSettingsRepository
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
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class TestRoleGateway(var defaultLauncher: Boolean = false) : RoleGateway() {
        override fun isDefaultHomeLauncher(): Boolean = defaultLauncher
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `theme and grid selection update settings repository`() = runTest(testDispatcher) {
        val settingsRepo = FakeSettingsRepository()
        val roleGateway = TestRoleGateway(defaultLauncher = false)
        val viewModel = OnboardingViewModel(settingsRepo, roleGateway)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.selectTheme(ThemeMode.DARK)
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.settings.themeMode)

        viewModel.selectGridPreset(GridPreset.GRID_5X6)
        advanceUntilIdle()
        assertEquals(GridPreset.GRID_5X6, viewModel.uiState.value.settings.gridPreset)
    }

    @Test
    fun `completeOnboarding marks settings completed and calls onDone callback`() = runTest(testDispatcher) {
        val settingsRepo = FakeSettingsRepository()
        val roleGateway = TestRoleGateway(defaultLauncher = true)
        val viewModel = OnboardingViewModel(settingsRepo, roleGateway)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.settings.onboardingCompleted)

        var completed = false
        viewModel.completeOnboarding { completed = true }
        advanceUntilIdle()

        assertTrue(completed)
        assertTrue(viewModel.uiState.value.settings.onboardingCompleted)
        assertTrue(viewModel.uiState.value.isDefaultHome)
    }

    @Test
    fun `refreshDefaultRoleState updates state when launcher role changes`() = runTest(testDispatcher) {
        val settingsRepo = FakeSettingsRepository()
        val roleGateway = TestRoleGateway(defaultLauncher = false)
        val viewModel = OnboardingViewModel(settingsRepo, roleGateway)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDefaultHome)

        roleGateway.defaultLauncher = true
        viewModel.refreshDefaultRoleState()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDefaultHome)
    }
}
