package app.aurora.launcher.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aurora.launcher.core.data.repository.AppCatalogRepository
import app.aurora.launcher.core.data.repository.LauncherSettingsRepository
import app.aurora.launcher.core.data.repository.WorkspaceRepository
import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.AppShortcutItem
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.DropResolution
import app.aurora.launcher.core.model.DropTarget
import app.aurora.launcher.core.model.GridOccupancyEngine
import app.aurora.launcher.core.model.GridSpec
import app.aurora.launcher.core.model.HomeUiMode
import app.aurora.launcher.core.model.ItemId
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.LeafPlacement
import app.aurora.launcher.core.model.PageId
import app.aurora.launcher.core.model.WorkspaceItem
import app.aurora.launcher.core.model.WorkspacePage
import app.aurora.launcher.core.model.WorkspaceSnapshot
import app.aurora.launcher.core.platform.AppWidgetHostManager
import app.aurora.launcher.core.platform.IconCacheAndLoader
import app.aurora.launcher.core.platform.LauncherAppsGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuickActionsTarget(
    val item: WorkspaceItem,
    val appEntry: AppEntry?,
    val shortcuts: List<AppShortcutItem> = emptyList(),
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val workspace: WorkspaceSnapshot = WorkspaceSnapshot(
        pages = listOf(WorkspacePage(PageId(1L), 0, emptyList())),
        dockItems = emptyList(),
        gridSpec = GridSpec(4, 6, 4),
    ),
    val catalogByKey: Map<ComponentKey, AppEntry> = emptyMap(),
    val settings: LauncherSettings = LauncherSettings(),
    val mode: HomeUiMode = HomeUiMode.Normal,
    val quickActionsTarget: QuickActionsTarget? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val appCatalogRepository: AppCatalogRepository,
    private val settingsRepository: LauncherSettingsRepository,
    private val launcherAppsGateway: LauncherAppsGateway,
    private val widgetHostManager: AppWidgetHostManager,
    val iconLoader: IconCacheAndLoader,
) : ViewModel() {

    private val isTabletFlow = MutableStateFlow(false)
    private val modeFlow = MutableStateFlow<HomeUiMode>(HomeUiMode.Normal)
    private val quickActionsFlow = MutableStateFlow<QuickActionsTarget?>(null)

    private val undoStack = ArrayDeque<WorkspaceSnapshot>()
    private val redoStack = ArrayDeque<WorkspaceSnapshot>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private fun updateHistoryFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun pushUndo(snapshot: WorkspaceSnapshot) {
        if (undoStack.size >= 20) {
            undoStack.removeFirst()
        }
        undoStack.addLast(snapshot)
        redoStack.clear()
        updateHistoryFlags()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = uiState.value.workspace
        val target = undoStack.removeLast()
        redoStack.addLast(current)
        updateHistoryFlags()
        viewModelScope.launch {
            workspaceRepository.restoreSnapshot(target)
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = uiState.value.workspace
        val target = redoStack.removeLast()
        undoStack.addLast(current)
        updateHistoryFlags()
        viewModelScope.launch {
            workspaceRepository.restoreSnapshot(target)
        }
    }

    fun removeSmartHeader() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(showSmartHeaderOnPage1 = false) }
        }
    }

    fun addNewPage(onCreated: ((Int) -> Unit)? = null) {
        val currentSnap = uiState.value.workspace
        pushUndo(currentSnap)
        viewModelScope.launch {
            val spec = uiState.value.workspace.gridSpec
            workspaceRepository.addPage(spec)
            val newIdx = uiState.value.workspace.pages.size
            onCreated?.invoke(newIdx)
        }
    }
    private var activeDragLease: AutoCloseable? = null

    private val gridSpecFlow = combine(
        settingsRepository.settings,
        isTabletFlow,
    ) { settings, isTablet ->
        settings.effectiveGridSpec(isTablet)
    }.distinctUntilChanged()

    private val workspaceFlow = gridSpecFlow.flatMapLatest { spec ->
        workspaceRepository.observeWorkspace(MutableStateFlow(spec))
    }

    val uiState: StateFlow<HomeUiState> = combine(
        workspaceFlow,
        appCatalogRepository.allApps,
        settingsRepository.settings,
        modeFlow,
        quickActionsFlow,
    ) { workspace, apps, settings, mode, quickActions ->
        HomeUiState(
            isLoading = false,
            workspace = workspace,
            catalogByKey = apps.associateBy { it.key },
            settings = settings,
            mode = mode,
            quickActionsTarget = quickActions,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun updateViewportTabletFlag(isTablet: Boolean) {
        isTabletFlow.value = isTablet
    }

    fun enterEditMode() {
        quickActionsFlow.value = null
        modeFlow.value = HomeUiMode.EditMode(dragSession = null)
    }

    fun exitEditMode() {
        releaseDragLease()
        modeFlow.value = HomeUiMode.Normal
    }

    fun beginDrag(item: WorkspaceItem, pageIndex: Int) {
        releaseDragLease()
        val comp = (item as? WorkspaceItem.App)?.component
        activeDragLease = workspaceRepository.acquireDragLease(
            draggedKey = comp,
            onDraggedSourceRemoved = {
                cancelDrag()
            },
        )
        modeFlow.value = HomeUiMode.EditMode(
            dragSession = app.aurora.launcher.core.model.DragSessionState(
                draggedItemId = item.id,
                sourceComponent = comp,
                originPageIndex = pageIndex,
            ),
        )
    }

    fun cancelDrag() {
        releaseDragLease()
        if (modeFlow.value is HomeUiMode.EditMode) {
            modeFlow.value = HomeUiMode.EditMode(dragSession = null)
        }
    }

    fun completeDrop(
        draggedItemId: ItemId,
        target: DropTarget,
        defaultFolderTitle: String,
        onRejected: () -> Unit,
    ) {
        val snapshot = uiState.value.workspace
        val resolution = GridOccupancyEngine.resolveDrop(
            snapshot = snapshot,
            draggedItemId = draggedItemId,
            target = target,
            defaultFolderTitle = defaultFolderTitle,
        )
        releaseDragLease()
        modeFlow.value = HomeUiMode.EditMode(dragSession = null)

        if (resolution is DropResolution.Reject) {
            onRejected()
            return
        }

        pushUndo(snapshot)

        viewModelScope.launch {
            workspaceRepository.applyDrop(snapshot, resolution)
            if (uiState.value.settings.autoReflowGrid) {
                workspaceRepository.packWorkspace(snapshot.gridSpec)
            }
        }
    }

    fun moveItemByAccessibility(
        item: WorkspaceItem,
        dx: Int,
        dy: Int,
        defaultFolderTitle: String,
    ) {
        val snapshot = uiState.value.workspace
        val (pageId, curX, curY) = when (item) {
            is WorkspaceItem.App -> {
                val p = item.placement as? LeafPlacement.Desktop ?: return
                Triple(p.pageId, p.cellX, p.cellY)
            }
            is WorkspaceItem.PinnedShortcut -> {
                val p = item.placement as? LeafPlacement.Desktop ?: return
                Triple(p.pageId, p.cellX, p.cellY)
            }
            is WorkspaceItem.Folder -> {
                val p = item.placement as? app.aurora.launcher.core.model.ContainerPlacement.Desktop ?: return
                Triple(p.pageId, p.cellX, p.cellY)
            }
            is WorkspaceItem.Widget -> Triple(item.pageId, item.cellX, item.cellY)
            is WorkspaceItem.WidgetStack -> Triple(item.pageId, item.cellX, item.cellY)
        }
        val targetX = (curX + dx).coerceIn(0, snapshot.gridSpec.columns - 1)
        val targetY = (curY + dy).coerceIn(0, snapshot.gridSpec.rows - 1)
        val target = DropTarget.DesktopCell(pageId = pageId, cellX = targetX, cellY = targetY)
        val resolution = GridOccupancyEngine.resolveDrop(snapshot, item.id, target, defaultFolderTitle)
        if (resolution !is DropResolution.Reject) {
            viewModelScope.launch {
                workspaceRepository.applyDrop(snapshot, resolution)
            }
        }
    }

    fun moveItemToExplicitDestination(
        itemId: ItemId,
        target: DropTarget,
        defaultFolderTitle: String,
    ) {
        val snapshot = uiState.value.workspace
        val resolution = GridOccupancyEngine.resolveDrop(snapshot, itemId, target, defaultFolderTitle)
        if (resolution !is DropResolution.Reject) {
            viewModelScope.launch {
                workspaceRepository.applyDrop(snapshot, resolution)
            }
        }
    }

    fun openQuickActions(item: WorkspaceItem) {
        val appEntry = (item as? WorkspaceItem.App)?.let { uiState.value.catalogByKey[it.component] }
        quickActionsFlow.value = QuickActionsTarget(
            item = item,
            appEntry = appEntry,
            shortcuts = emptyList(),
        )
        modeFlow.value = HomeUiMode.QuickActions(item.id)
        if (item is WorkspaceItem.App) {
            viewModelScope.launch {
                val loadedShortcuts = launcherAppsGateway.queryAppShortcuts(item.component)
                val current = quickActionsFlow.value
                if (current?.item?.id == item.id) {
                    quickActionsFlow.value = current.copy(shortcuts = loadedShortcuts)
                }
            }
        }
    }

    fun dismissQuickActions() {
        quickActionsFlow.value = null
        if (modeFlow.value is HomeUiMode.QuickActions) {
            modeFlow.value = HomeUiMode.Normal
        }
    }

    fun removeItemFromHome(itemId: ItemId) {
        dismissQuickActions()
        pushUndo(uiState.value.workspace)
        viewModelScope.launch {
            val removedWidgetIds = workspaceRepository.removeItemFromHome(itemId)
            removedWidgetIds.forEach { widgetHostManager.deleteWidgetId(it.value) }
            if (uiState.value.settings.autoReflowGrid) {
                workspaceRepository.packWorkspace(uiState.value.workspace.gridSpec)
            }
        }
    }

    fun pinAppShortcut(shortcut: AppShortcutItem, onResult: (Boolean) -> Unit) {
        dismissQuickActions()
        viewModelScope.launch {
            val ok = workspaceRepository.pinShortcut(
                shortcutId = shortcut.id,
                packageName = shortcut.packageName,
                userSerial = shortcut.userSerial,
                label = shortcut.shortLabel,
                gridSpec = uiState.value.workspace.gridSpec,
            )
            onResult(ok)
        }
    }

    fun launchPinnedShortcut(item: WorkspaceItem.PinnedShortcut) {
        launcherAppsGateway.launchShortcut(
            packageName = item.packageName,
            shortcutId = item.shortcutId,
            userSerial = item.userSerial,
        )
    }

    fun launchDynamicShortcut(shortcut: AppShortcutItem) {
        dismissQuickActions()
        launcherAppsGateway.launchShortcut(
            packageName = shortcut.packageName,
            shortcutId = shortcut.id,
            userSerial = shortcut.userSerial,
        )
    }

    fun toggleLockApp(key: ComponentKey, currentlyLocked: Boolean) {
        dismissQuickActions()
        viewModelScope.launch {
            appCatalogRepository.setAppLocked(key, !currentlyLocked)
        }
    }

    fun hideApp(key: ComponentKey) {
        dismissQuickActions()
        viewModelScope.launch {
            appCatalogRepository.setAppHidden(key, true)
        }
    }

    fun openAppInfo(key: ComponentKey) {
        dismissQuickActions()
        launcherAppsGateway.openAppInfo(key)
    }

    fun requestUninstall(key: ComponentKey) {
        dismissQuickActions()
        launcherAppsGateway.requestUninstall(key)
    }

    fun cycleWallpaperStyle() {
        viewModelScope.launch {
            settingsRepository.updateSettings { cur ->
                val next = when (cur.wallpaperStyle) {
                    app.aurora.launcher.core.model.WallpaperStyle.BOREALIS_TWILIGHT ->
                        app.aurora.launcher.core.model.WallpaperStyle.IVORY_MIST
                    app.aurora.launcher.core.model.WallpaperStyle.IVORY_MIST ->
                        app.aurora.launcher.core.model.WallpaperStyle.SLATE_HORIZON
                    app.aurora.launcher.core.model.WallpaperStyle.SLATE_HORIZON ->
                        app.aurora.launcher.core.model.WallpaperStyle.AURA_FLOW
                    app.aurora.launcher.core.model.WallpaperStyle.AURA_FLOW ->
                        app.aurora.launcher.core.model.WallpaperStyle.IRIDESCENT_STRATA
                    else -> app.aurora.launcher.core.model.WallpaperStyle.BOREALIS_TWILIGHT
                }
                cur.copy(wallpaperStyle = next)
            }
        }
    }

    fun packWorkspace() {
        val spec = uiState.value.workspace.gridSpec
        pushUndo(uiState.value.workspace)
        viewModelScope.launch {
            workspaceRepository.packWorkspace(spec)
        }
    }

    fun togglePageVisibility(pageId: PageId) {
        viewModelScope.launch {
            settingsRepository.updateSettings { cur ->
                val hidden = cur.hiddenPageIds.toMutableSet()
                if (pageId.value in hidden) {
                    hidden.remove(pageId.value)
                } else {
                    val allPageIds = uiState.value.workspace.pages.map { it.id.value }.toSet()
                    val remainingVisible = allPageIds - hidden - setOf(pageId.value)
                    if (remainingVisible.isNotEmpty()) {
                        hidden.add(pageId.value)
                    }
                }
                cur.copy(hiddenPageIds = hidden)
            }
        }
    }

    fun deletePage(pageId: PageId, onResult: (Boolean) -> Unit = {}) {
        val spec = uiState.value.workspace.gridSpec
        pushUndo(uiState.value.workspace)
        viewModelScope.launch {
            val ok = workspaceRepository.deletePage(pageId, spec)
            if (ok) {
                settingsRepository.updateSettings { cur ->
                    if (pageId.value in cur.hiddenPageIds) {
                        cur.copy(hiddenPageIds = cur.hiddenPageIds - setOf(pageId.value))
                    } else cur
                }
            }
            onResult(ok)
        }
    }

    private fun releaseDragLease() {
        activeDragLease?.close()
        activeDragLease = null
    }

    override fun onCleared() {
        releaseDragLease()
        super.onCleared()
    }
}
