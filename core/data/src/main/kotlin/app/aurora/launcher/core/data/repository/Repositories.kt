package app.aurora.launcher.core.data.repository

import app.aurora.launcher.core.common.SystemResult
import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.AppWidgetId
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.DropResolution
import app.aurora.launcher.core.model.GridSpec
import app.aurora.launcher.core.model.ItemId
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.LibraryGroup
import app.aurora.launcher.core.model.PageId
import app.aurora.launcher.core.model.WidgetProviderCandidate
import app.aurora.launcher.core.model.WorkspaceSnapshot
import kotlinx.coroutines.flow.Flow

data class SnapshotSummary(
    val id: Long,
    val reason: String,
    val createdAtEpochMs: Long,
)

interface LauncherSettingsRepository {
    val settings: Flow<LauncherSettings>
    suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings)
    suspend fun resetToDefaults()
}

interface AppCatalogRepository {
    val allApps: Flow<List<AppEntry>>
    val recentlyAddedApps: Flow<List<AppEntry>>
    val frequentlyUsedApps: Flow<List<AppEntry>>
    suspend fun syncInstalledApps(installed: List<AppEntry>)
    suspend fun purgePrivateSpaceApps()
    suspend fun recordAppLaunch(key: ComponentKey, epochMs: Long = System.currentTimeMillis())
    suspend fun setAppHidden(key: ComponentKey, hidden: Boolean)
    suspend fun setAppLocked(key: ComponentKey, locked: Boolean)
    suspend fun setCategoryOverride(key: ComponentKey, group: LibraryGroup?)
}

interface WorkspaceRepository {
    fun observeWorkspace(gridSpecFlow: Flow<GridSpec>): Flow<WorkspaceSnapshot>
    val snapshots: Flow<List<SnapshotSummary>>
    suspend fun seedInitialWorkspaceIfEmpty(installedApps: List<AppEntry>, gridSpec: GridSpec)
    suspend fun applyDrop(snapshot: WorkspaceSnapshot, resolution: DropResolution)
    suspend fun addAppToHome(component: ComponentKey, gridSpec: GridSpec): Boolean
    suspend fun removeItemFromHome(itemId: ItemId): List<AppWidgetId>
    suspend fun renameFolder(folderId: ItemId, newTitle: String)
    suspend fun placeWidget(
        candidate: WidgetProviderCandidate,
        appWidgetId: AppWidgetId,
        preferredPageId: PageId?,
        gridSpec: GridSpec,
        needsConfig: Boolean,
    ): ItemId?
    suspend fun markWidgetConfigured(appWidgetId: AppWidgetId)
    suspend fun resizeWidget(itemId: ItemId, newSpanX: Int, newSpanY: Int, gridSpec: GridSpec): Boolean
    suspend fun setWidgetStackActiveIndex(stackId: ItemId, activeIndex: Int)
    suspend fun pinShortcut(
        shortcutId: String,
        packageName: String,
        userSerial: Long,
        label: String,
        gridSpec: GridSpec,
    ): Boolean
    suspend fun packWorkspace(gridSpec: GridSpec)
    suspend fun reflowToGrid(currentSpec: GridSpec, targetSpec: GridSpec)
    suspend fun restoreLatestSnapshot(): Boolean
    suspend fun restoreSnapshotById(snapshotId: Long): Boolean
    fun acquireDragLease(draggedKey: ComponentKey?, onDraggedSourceRemoved: () -> Unit): AutoCloseable
    suspend fun reconcileInstalledPackages(
        presentKeys: Set<ComponentKey>,
        uninstalledPackages: Set<String> = emptySet(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ): List<AppWidgetId>
}

interface SearchHistoryRepository {
    val recentQueries: Flow<List<String>>
    suspend fun recordQuery(query: String)
    suspend fun clearHistory()
}

interface BackupRepository {
    suspend fun exportBackupJson(currentSettings: LauncherSettings): String
    suspend fun importBackupJson(rawJson: String): SystemResult<Int>
    suspend fun wipeAllLocalData(): List<AppWidgetId>
}
