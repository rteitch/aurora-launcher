package app.aurora.launcher.feature.widgets

import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.GridSpec
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.WidgetProviderCandidate
import app.aurora.launcher.core.platform.AppWidgetHostManager
import app.aurora.launcher.core.platform.WidgetSpanCalculator
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
class WidgetPickerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val candidate1 = WidgetProviderCandidate(
        provider = ComponentKey("com.example.clock", "ClockWidgetProvider", 0L),
        appLabel = "Clock",
        widgetLabel = "Analog Clock",
        description = "Displays the current time",
        defaultSpanX = 2,
        defaultSpanY = 2,
        minSpanX = 2,
        minSpanY = 2,
        maxSpanX = 4,
        maxSpanY = 4,
        hasConfigurationActivity = false,
    )

    private class TestAppWidgetHostManager(
        private val list: List<WidgetProviderCandidate> = emptyList(),
    ) : AppWidgetHostManager() {
        override suspend fun listInstalledProviders(gridSpec: GridSpec): List<WidgetProviderCandidate> {
            return list
        }
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
    fun `loadProviders sets loading state and populates candidates`() = runTest(testDispatcher) {
        val hostManager = TestAppWidgetHostManager(listOf(candidate1))
        val workspaceRepo = FakeWorkspaceRepository()
        val settingsRepo = FakeSettingsRepository()

        val viewModel = WidgetPickerViewModel(
            hostManager = hostManager,
            workspaceRepository = workspaceRepo,
            settingsRepository = settingsRepo,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.loadProviders(GridSpec(4, 6, 4))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.candidates.size)
        assertEquals("Analog Clock", state.candidates.first().widgetLabel)
    }

    @Test
    fun `query update and restoreSmartHeader reflect in state`() = runTest(testDispatcher) {
        val hostManager = TestAppWidgetHostManager()
        val workspaceRepo = FakeWorkspaceRepository()
        val settingsRepo = FakeSettingsRepository(LauncherSettings(showSmartHeaderOnPage1 = false))

        val viewModel = WidgetPickerViewModel(
            hostManager = hostManager,
            workspaceRepository = workspaceRepo,
            settingsRepository = settingsRepo,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showSmartGlanceCandidate)

        viewModel.updateQuery("calendar")
        advanceUntilIdle()
        assertEquals("calendar", viewModel.uiState.value.query)

        var restored = false
        viewModel.restoreSmartHeader { restored = true }
        advanceUntilIdle()

        assertTrue(restored)
        assertFalse(viewModel.uiState.value.showSmartGlanceCandidate)
    }

    @Test
    fun `widget span calculator computes appropriate cell spans`() {
        assertEquals(1, WidgetSpanCalculator.dpToCells(40, 4))
        assertEquals(2, WidgetSpanCalculator.dpToCells(110, 4))
        assertEquals(3, WidgetSpanCalculator.dpToCells(180, 4))
        assertEquals(4, WidgetSpanCalculator.dpToCells(250, 4))
        assertEquals(4, WidgetSpanCalculator.dpToCells(350, 4)) // clamped to maxCells
    }
}
