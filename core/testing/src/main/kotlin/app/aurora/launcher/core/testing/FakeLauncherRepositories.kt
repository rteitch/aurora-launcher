package app.aurora.launcher.core.testing

import app.aurora.launcher.core.common.SystemResult
import app.aurora.launcher.core.data.repository.AppCatalogRepository
import app.aurora.launcher.core.data.repository.BackupRepository
import app.aurora.launcher.core.data.repository.LauncherSettingsRepository
import app.aurora.launcher.core.data.repository.SearchHistoryRepository
import app.aurora.launcher.core.data.repository.SnapshotSummary
import app.aurora.launcher.core.data.repository.WorkspaceRepository
import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.AppWidgetId
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.DropResolution
import app.aurora.launcher.core.model.GridOccupancyEngine
import app.aurora.launcher.core.model.GridSpec
import app.aurora.launcher.core.model.ItemId
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.LeafPlacement
import app.aurora.launcher.core.model.LibraryGroup
import app.aurora.launcher.core.model.PageId
import app.aurora.launcher.core.model.WidgetProviderCandidate
import app.aurora.launcher.core.model.WorkspaceItem
import app.aurora.launcher.core.model.WorkspacePage
import app.aurora.launcher.core.model.WorkspaceSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(
    initial: LauncherSettings = LauncherSettings(),
) : LauncherSettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<LauncherSettings> = state
    override suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        state.update(transform)
    }
    override suspend fun resetToDefaults() {
        state.value = LauncherSettings()
    }
}

class FakeAppCatalogRepository(
    initialApps: List<AppEntry> = emptyList(),
) : AppCatalogRepository {
    private val state = MutableStateFlow(initialApps)
    override val allApps: Flow<List<AppEntry>> = state
    override val recentlyAddedApps: Flow<List<AppEntry>> = state.map { it.filter { a -> !a.isHidden }.take(8) }
    override val frequentlyUsedApps: Flow<List<AppEntry>> = state.map { it.filter { a -> !a.isHidden && a.launchCount > 0 }.take(8) }

    override suspend fun syncInstalledApps(installed: List<AppEntry>) {
        state.value = installed
    }
    override suspend fun purgePrivateSpaceApps() {
        state.update { list -> list.filter { it.profileBadge.name != "PRIVATE" } }
    }
    override suspend fun recordAppLaunch(key: ComponentKey, epochMs: Long) {
        state.update { list ->
            list.map { if (it.key == key) it.copy(launchCount = it.launchCount + 1, lastLaunchEpochMs = epochMs) else it }
        }
    }
    override suspend fun setAppHidden(key: ComponentKey, hidden: Boolean) {
        state.update { list -> list.map { if (it.key == key) it.copy(isHidden = hidden) else it } }
    }
    override suspend fun setAppLocked(key: ComponentKey, locked: Boolean) {
        state.update { list -> list.map { if (it.key == key) it.copy(isLocked = locked) else it } }
    }
    override suspend fun setCategoryOverride(key: ComponentKey, group: LibraryGroup?) {
        state.update { list -> list.map { if (it.key == key) it.copy(userGroupOverride = group) else it } }
    }
}

class FakeSearchHistoryRepository : SearchHistoryRepository {
    private val queries = MutableStateFlow<List<String>>(emptyList())
    override val recentQueries: Flow<List<String>> = queries
    override suspend fun recordQuery(query: String) {
        val t = query.trim()
        if (t.isNotEmpty()) queries.update { (listOf(t) + it.filter { q -> q != t }).take(50) }
    }
    override suspend fun clearHistory() {
        queries.value = emptyList()
    }
}

class FakeWorkspaceRepository(
    initialSnapshot: WorkspaceSnapshot = WorkspaceSnapshot(
        pages = listOf(WorkspacePage(PageId(1L), 0, emptyList())),
        dockItems = emptyList(),
        gridSpec = GridSpec(4, 6, 4),
    ),
) : WorkspaceRepository {
    val current = MutableStateFlow(initialSnapshot)
    override val snapshots: Flow<List<SnapshotSummary>> = MutableStateFlow(emptyList())

    override fun observeWorkspace(gridSpecFlow: Flow<GridSpec>): Flow<WorkspaceSnapshot> =
        combine(current, gridSpecFlow) { snap, spec -> snap.copy(gridSpec = spec) }

    override suspend fun seedInitialWorkspaceIfEmpty(installedApps: List<AppEntry>, gridSpec: GridSpec) {
        if (current.value.pages.all { it.items.isEmpty() } && current.value.dockItems.isEmpty()) {
            val dock = installedApps.take(gridSpec.dockSlots).mapIndexed { i, app ->
                WorkspaceItem.App(ItemId(100L + i), LeafPlacement.Dock(i), app.key)
            }
            val desk = installedApps.drop(gridSpec.dockSlots).take(gridSpec.columns * gridSpec.rows).mapIndexed { i, app ->
                WorkspaceItem.App(
                    ItemId(200L + i),
                    LeafPlacement.Desktop(PageId(1L), i % gridSpec.columns, i / gridSpec.columns),
                    app.key,
                )
            }
            current.value = WorkspaceSnapshot(
                pages = listOf(WorkspacePage(PageId(1L), 0, desk)),
                dockItems = dock,
                gridSpec = gridSpec,
            )
        }
    }

    override suspend fun applyDrop(snapshot: WorkspaceSnapshot, resolution: DropResolution) {}
    override suspend fun addAppToHome(component: ComponentKey, gridSpec: GridSpec): Boolean = true
    override suspend fun removeItemFromHome(itemId: ItemId): List<AppWidgetId> = emptyList()
    override suspend fun renameFolder(folderId: ItemId, newTitle: String) {}
    override suspend fun placeWidget(
        candidate: WidgetProviderCandidate,
        appWidgetId: AppWidgetId,
        preferredPageId: PageId?,
        gridSpec: GridSpec,
        needsConfig: Boolean,
    ): ItemId = ItemId(900L)
    override suspend fun markWidgetConfigured(appWidgetId: AppWidgetId) {}
    override suspend fun resizeWidget(itemId: ItemId, newSpanX: Int, newSpanY: Int, gridSpec: GridSpec): Boolean = true
    override suspend fun setWidgetStackActiveIndex(stackId: ItemId, activeIndex: Int) {}
    override suspend fun pinShortcut(shortcutId: String, packageName: String, userSerial: Long, label: String, gridSpec: GridSpec): Boolean = true
    override suspend fun packWorkspace(gridSpec: GridSpec) {
        current.update { GridOccupancyEngine.packWorkspace(it) }
    }
    override suspend fun addPage(gridSpec: GridSpec): PageId {
        val newId = PageId(current.value.pages.size + 10L)
        current.update { snap ->
            snap.copy(pages = snap.pages + WorkspacePage(newId, snap.pages.size, emptyList()))
        }
        return newId
    }
    override suspend fun deletePage(pageId: PageId, gridSpec: GridSpec): Boolean {
        current.update { snap ->
            val remaining = snap.pages.filter { it.id != pageId }
            if (remaining.isEmpty()) snap else snap.copy(pages = remaining.mapIndexed { idx, p -> p.copy(position = idx) })
        }
        return true
    }
    override suspend fun addAppToSpecificPage(component: ComponentKey, pageId: PageId, gridSpec: GridSpec): Boolean = true
    override suspend fun restoreSnapshot(snapshot: WorkspaceSnapshot): Boolean {
        current.value = snapshot
        return true
    }
    override suspend fun reflowToGrid(currentSpec: GridSpec, targetSpec: GridSpec) {
        current.update { GridOccupancyEngine.reflowWorkspace(it, targetSpec) { pos -> PageId(50L + pos) } }
    }
    override suspend fun restoreLatestSnapshot(): Boolean = true
    override suspend fun restoreSnapshotById(snapshotId: Long): Boolean = true
    override fun acquireDragLease(draggedKey: ComponentKey?, onDraggedSourceRemoved: () -> Unit): AutoCloseable = AutoCloseable {}
    override suspend fun reconcileInstalledPackages(
        presentKeys: Set<ComponentKey>,
        uninstalledPackages: Set<String>,
        nowEpochMs: Long,
    ): List<AppWidgetId> = emptyList()
}

class FakeBackupRepository : BackupRepository {
    override suspend fun exportBackupJson(currentSettings: LauncherSettings): String = "{}"
    override suspend fun importBackupJson(rawJson: String): SystemResult<Int> = SystemResult.Success(0)
    override suspend fun wipeAllLocalData(): List<AppWidgetId> = emptyList()
}
