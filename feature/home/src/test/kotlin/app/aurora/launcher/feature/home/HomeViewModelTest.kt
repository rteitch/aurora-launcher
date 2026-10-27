package app.aurora.launcher.feature.home

import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.GridSpec
import app.aurora.launcher.core.model.HomeUiMode
import app.aurora.launcher.core.model.ItemId
import app.aurora.launcher.core.model.LeafPlacement
import app.aurora.launcher.core.model.LibraryGroup
import app.aurora.launcher.core.model.PageId
import app.aurora.launcher.core.model.WorkspaceItem
import app.aurora.launcher.core.model.WorkspacePage
import app.aurora.launcher.core.model.WorkspaceSnapshot
import app.aurora.launcher.core.platform.AppWidgetHostManager
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val keyA = ComponentKey("app.test.a", "MainActivity", 0L)
    private val keyB = ComponentKey("app.test.b", "MainActivity", 0L)

    private val appA = AppEntry(
        key = keyA,
        label = "Test App A",
        sortKey = "test app a",
        libraryGroup = LibraryGroup.PRODUCTIVITY_FINANCE,
        isHidden = false,
    )
    private val appB = AppEntry(
        key = keyB,
        label = "Test App B",
        sortKey = "test app b",
        libraryGroup = LibraryGroup.UTILITIES,
        isHidden = false,
    )

    private val sampleSnapshot = WorkspaceSnapshot(
        pages = listOf(
            WorkspacePage(
                id = PageId(1L),
                position = 0,
                items = listOf(
                    WorkspaceItem.App(ItemId(10L), LeafPlacement.Desktop(PageId(1L), 0, 0), keyA),
                ),
            ),
        ),
        dockItems = listOf(
            WorkspaceItem.App(ItemId(20L), LeafPlacement.Dock(0), keyB),
        ),
        gridSpec = GridSpec(4, 6, 4),
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
        workspaceRepo: FakeWorkspaceRepository = FakeWorkspaceRepository(sampleSnapshot),
        appCatalogRepo: FakeAppCatalogRepository = FakeAppCatalogRepository(listOf(appA, appB)),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
        gateway: FakeLauncherAppsGateway = FakeLauncherAppsGateway(),
    ): HomeViewModel {
        return HomeViewModel(
            workspaceRepository = workspaceRepo,
            appCatalogRepository = appCatalogRepo,
            settingsRepository = settingsRepo,
            launcherAppsGateway = gateway,
            widgetHostManager = AppWidgetHostManager(),
            iconLoader = IconCacheAndLoader(),
        )
    }

    @Test
    fun `uiState initializes with snapshot and catalog mapping`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.workspace.pages.size)
        assertEquals(1, state.workspace.dockItems.size)
        assertTrue(state.catalogByKey.containsKey(keyA))
        assertTrue(state.catalogByKey.containsKey(keyB))
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test
    fun `edit mode transitions correctly`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.enterEditMode()
        advanceUntilIdle()
        assertEquals(HomeUiMode.EditMode(dragSession = null), viewModel.uiState.value.mode)

        viewModel.exitEditMode()
        advanceUntilIdle()
        assertEquals(HomeUiMode.Normal, viewModel.uiState.value.mode)
    }

    @Test
    fun `undo and redo stack updates upon packing workspace`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canUndo)
        assertFalse(viewModel.uiState.value.canRedo)

        viewModel.packWorkspace()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canUndo)
        assertFalse(viewModel.uiState.value.canRedo)

        viewModel.undo()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canUndo)
        assertTrue(viewModel.uiState.value.canRedo)

        viewModel.redo()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canUndo)
        assertFalse(viewModel.uiState.value.canRedo)
    }

    @Test
    fun `quick actions can be opened and dismissed`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val item = sampleSnapshot.pages[0].items[0]
        viewModel.openQuickActions(item)
        advanceUntilIdle()

        val target = viewModel.uiState.value.quickActionsTarget
        assertNotNull(target)
        assertEquals(item.id, target?.item?.id)

        viewModel.dismissQuickActions()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.quickActionsTarget)
    }

    @Test
    fun `addNewPage and deletePage update workspace pages`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.workspace.pages.size)

        viewModel.addNewPage()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.workspace.pages.size)

        val newPageId = viewModel.uiState.value.workspace.pages.last().id
        viewModel.deletePage(newPageId)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.workspace.pages.size)
    }
}
