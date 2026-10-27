package app.aurora.launcher.feature.spotlight

import app.aurora.launcher.core.common.AuroraDispatchers
import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.LibraryGroup
import app.aurora.launcher.core.platform.ContactsAndSettingsSearchGateway
import app.aurora.launcher.core.platform.IconCacheAndLoader
import app.aurora.launcher.core.testing.FakeAppCatalogRepository
import app.aurora.launcher.core.testing.FakeSearchHistoryRepository
import app.aurora.launcher.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class SpotlightViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val auroraDispatchers = AuroraDispatchers(
        main = testDispatcher,
        io = testDispatcher,
        default = testDispatcher,
    )

    private val app1 = AppEntry(
        key = ComponentKey("com.spotify.music", "MainActivity", 0L),
        label = "Spotify",
        sortKey = "spotify",
        libraryGroup = LibraryGroup.ENTERTAINMENT,
        isHidden = false,
    )
    private val app2 = AppEntry(
        key = ComponentKey("com.google.android.youtube", "MainActivity", 0L),
        label = "YouTube",
        sortKey = "youtube",
        libraryGroup = LibraryGroup.ENTERTAINMENT,
        isHidden = false,
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
    fun `searching queries filter apps by debounced query`() = runTest(testDispatcher) {
        val appCatalog = FakeAppCatalogRepository(listOf(app1, app2))
        val settingsRepo = FakeSettingsRepository()
        val searchHistory = FakeSearchHistoryRepository()
        val gateway = ContactsAndSettingsSearchGateway()
        val iconLoader = IconCacheAndLoader()

        val viewModel = SpotlightViewModel(
            appCatalogRepository = appCatalog,
            settingsRepository = settingsRepo,
            searchHistoryRepository = searchHistory,
            contactsAndSettingsGateway = gateway,
            iconLoader = iconLoader,
            dispatchers = auroraDispatchers,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.rawQuery)
        assertTrue(viewModel.uiState.value.results.matchingApps.isEmpty())

        viewModel.updateQuery("Spot")
        advanceTimeBy(100L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Spot", state.rawQuery)
        assertEquals(1, state.results.matchingApps.size)
        assertEquals("Spotify", state.results.matchingApps.first().label)
    }

    @Test
    fun `recent search queries populate into uiState`() = runTest(testDispatcher) {
        val appCatalog = FakeAppCatalogRepository(listOf(app1, app2))
        val settingsRepo = FakeSettingsRepository(LauncherSettings(saveSearchHistory = true))
        val searchHistory = FakeSearchHistoryRepository(listOf("Reddit", "Twitter"))
        val gateway = ContactsAndSettingsSearchGateway()
        val iconLoader = IconCacheAndLoader()

        val viewModel = SpotlightViewModel(
            appCatalogRepository = appCatalog,
            settingsRepository = settingsRepo,
            searchHistoryRepository = searchHistory,
            contactsAndSettingsGateway = gateway,
            iconLoader = iconLoader,
            dispatchers = auroraDispatchers,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.recentQueries.size)
        assertTrue(state.recentQueries.contains("Reddit"))
        assertTrue(state.recentQueries.contains("Twitter"))
    }
}
