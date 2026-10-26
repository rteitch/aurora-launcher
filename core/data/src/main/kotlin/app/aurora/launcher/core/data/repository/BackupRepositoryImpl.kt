package app.aurora.launcher.core.data.repository

import androidx.room.withTransaction
import app.aurora.launcher.core.common.SystemResult
import app.aurora.launcher.core.data.backup.AuroraBackupPayload
import app.aurora.launcher.core.data.backup.BackupPageDto
import app.aurora.launcher.core.data.backup.BackupPayloadCodec
import app.aurora.launcher.core.data.db.AuroraDao
import app.aurora.launcher.core.data.db.AuroraDatabase
import app.aurora.launcher.core.model.AppWidgetId
import app.aurora.launcher.core.model.GridOccupancyEngine
import app.aurora.launcher.core.model.GridSpec
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.PageId
import app.aurora.launcher.core.model.WorkspacePage
import app.aurora.launcher.core.model.WorkspaceSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val database: AuroraDatabase,
    private val dao: AuroraDao,
    private val workspaceRepository: WorkspaceRepositoryImpl,
    private val settingsRepository: LauncherSettingsRepository,
) : BackupRepository {

    override suspend fun exportBackupJson(currentSettings: LauncherSettings): String {
        val gridSpec = currentSettings.effectiveGridSpec(isTabletViewport = false)
        val snapshot = workspaceRepository.loadCurrentSnapshot(gridSpec)
        val payload = AuroraBackupPayload(
            exportedAtEpochMs = System.currentTimeMillis(),
            gridColumns = gridSpec.columns,
            gridRows = gridSpec.rows,
            dockSlots = gridSpec.dockSlots,
            pages = snapshot.pages.map { BackupPageDto(it.id.value, it.position, it.items) },
            dockItems = snapshot.dockItems,
            // Exclude biometric lock state from exported settings per privacy rules (prd.md 9.5)
            settings = currentSettings.copy(
                biometricAppLockEnabled = false,
                customWallpaperPath = null,
            ),
        )
        return BackupPayloadCodec.encode(payload)
    }

    override suspend fun importBackupJson(rawJson: String): SystemResult<Int> {
        val validated = when (val res = BackupPayloadCodec.decodeAndValidate(rawJson)) {
            is SystemResult.Success -> res.data
            is SystemResult.Failure -> return res
        }

        val targetSpec = GridSpec(validated.gridColumns, validated.gridRows, validated.dockSlots)
        val installedKeys = dao.getApps().map {
            app.aurora.launcher.core.model.ComponentKey(it.packageName, it.activityClass, it.userSerial)
        }.toSet()
        val now = System.currentTimeMillis()

        fun markMissingAppsAsOrphan(
            items: List<app.aurora.launcher.core.model.WorkspaceItem>,
        ): List<app.aurora.launcher.core.model.WorkspaceItem> = items.map { item ->
            when (item) {
                is app.aurora.launcher.core.model.WorkspaceItem.App -> {
                    if (item.component in installedKeys) {
                        item.copy(orphanSinceEpochMs = null)
                    } else {
                        item.copy(orphanSinceEpochMs = now)
                    }
                }
                is app.aurora.launcher.core.model.WorkspaceItem.Folder -> {
                    item.copy(
                        children = item.children.map { child ->
                            if (child.component in installedKeys) {
                                child.copy(orphanSinceEpochMs = null)
                            } else {
                                child.copy(orphanSinceEpochMs = now)
                            }
                        },
                    )
                }
                else -> item
            }
        }

        val restoredSnapshot = GridOccupancyEngine.normalizeSnapshot(
            WorkspaceSnapshot(
                pages = validated.pages.map {
                    WorkspacePage(PageId(it.id), it.position, markMissingAppsAsOrphan(it.items))
                },
                dockItems = markMissingAppsAsOrphan(validated.dockItems),
                gridSpec = targetSpec,
            ),
        )

        database.withTransaction {
            val current = workspaceRepository.loadCurrentSnapshot(targetSpec)
            workspaceRepository.saveSnapshotInternal(current, reason = "Before backup import")
            workspaceRepository.persistSnapshotInternal(restoredSnapshot)
        }

        validated.settings?.let { importedSettings ->
            settingsRepository.updateSettings { prev ->
                importedSettings.copy(
                    onboardingCompleted = true,
                    biometricAppLockEnabled = prev.biometricAppLockEnabled,
                    customWallpaperPath = prev.customWallpaperPath,
                )
            }
        }

        val itemCount = restoredSnapshot.pages.sumOf { it.items.size } + restoredSnapshot.dockItems.size
        return SystemResult.Success(itemCount)
    }

    override suspend fun wipeAllLocalData(): List<AppWidgetId> {
        val allocatedWidgets = dao.getWidgetInstances().map { AppWidgetId(it.appWidgetId) }
        database.withTransaction {
            dao.deleteAllWidgets()
            dao.deleteAllShortcuts()
            dao.deleteAllWorkspaceItems()
            dao.deleteAllPages()
            dao.deleteAllAppEntries()
            dao.deleteAllIconOverrides()
            dao.deleteAllDailyUsage()
            dao.clearSearchHistory()
            dao.deleteAllSnapshots()
        }
        settingsRepository.resetToDefaults()
        return allocatedWidgets
    }
}
