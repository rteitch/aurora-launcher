package app.aurora.launcher.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AuroraDao {
    // Workspace pages
    @Query("SELECT * FROM workspace_page ORDER BY position ASC")
    fun observePages(): Flow<List<WorkspacePageEntity>>

    @Query("SELECT * FROM workspace_page ORDER BY position ASC")
    suspend fun getPages(): List<WorkspacePageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: WorkspacePageEntity): Long

    @Query("DELETE FROM workspace_page")
    suspend fun deleteAllPages()

    @Query("DELETE FROM workspace_page WHERE id = :pageId")
    suspend fun deletePageById(pageId: Long)

    // Workspace items
    @Query("SELECT * FROM workspace_item ORDER BY rank ASC, id ASC")
    fun observeWorkspaceItems(): Flow<List<WorkspaceItemEntity>>

    @Query("SELECT * FROM workspace_item ORDER BY rank ASC, id ASC")
    suspend fun getWorkspaceItems(): List<WorkspaceItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspaceItem(item: WorkspaceItemEntity): Long

    @Update
    suspend fun updateWorkspaceItem(item: WorkspaceItemEntity)

    @Query("DELETE FROM workspace_item WHERE id = :itemId")
    suspend fun deleteWorkspaceItemById(itemId: Long)

    @Query("DELETE FROM workspace_item WHERE container_id = :containerId AND container IN ('FOLDER', 'STACK')")
    suspend fun deleteChildrenOfContainer(containerId: Long)

    @Query("DELETE FROM workspace_item")
    suspend fun deleteAllWorkspaceItems()

    // App entries
    @Query("SELECT * FROM app_entry ORDER BY label_sort_key ASC, package_name ASC")
    fun observeApps(): Flow<List<AppEntryEntity>>

    @Query("SELECT * FROM app_entry")
    suspend fun getApps(): List<AppEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApps(entries: List<AppEntryEntity>)

    @Query("DELETE FROM app_entry WHERE package_name = :pkg AND activity_class = :cls AND user_serial = :userSerial")
    suspend fun deleteAppEntry(pkg: String, cls: String, userSerial: Long)

    @Query("DELETE FROM app_entry WHERE profile_badge = 'PRIVATE'")
    suspend fun deletePrivateSpaceApps()

    @Query("DELETE FROM app_entry")
    suspend fun deleteAllAppEntries()

    @Query(
        """
        UPDATE app_entry
        SET launch_count = launch_count + 1, last_launch_at = :epochMs
        WHERE package_name = :pkg AND activity_class = :cls AND user_serial = :userSerial
        """,
    )
    suspend fun incrementLaunchCount(pkg: String, cls: String, userSerial: Long, epochMs: Long)

    @Query(
        """
        UPDATE app_entry
        SET is_hidden = :hidden
        WHERE package_name = :pkg AND activity_class = :cls AND user_serial = :userSerial
        """,
    )
    suspend fun updateAppHidden(pkg: String, cls: String, userSerial: Long, hidden: Boolean)

    @Query(
        """
        UPDATE app_entry
        SET is_locked = :locked
        WHERE package_name = :pkg AND activity_class = :cls AND user_serial = :userSerial
        """,
    )
    suspend fun updateAppLocked(pkg: String, cls: String, userSerial: Long, locked: Boolean)

    @Query(
        """
        UPDATE app_entry
        SET user_group_override = :overrideGroup
        WHERE package_name = :pkg AND activity_class = :cls AND user_serial = :userSerial
        """,
    )
    suspend fun updateAppCategoryOverride(pkg: String, cls: String, userSerial: Long, overrideGroup: String?)

    // Widget instances
    @Query("SELECT * FROM widget_instance")
    fun observeWidgetInstances(): Flow<List<WidgetInstanceEntity>>

    @Query("SELECT * FROM widget_instance")
    suspend fun getWidgetInstances(): List<WidgetInstanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWidgetInstance(entity: WidgetInstanceEntity)

    @Query("DELETE FROM widget_instance")
    suspend fun deleteAllWidgets()

    // Shortcut pins
    @Query("SELECT * FROM shortcut_pin")
    fun observeShortcutPins(): Flow<List<ShortcutPinEntity>>

    @Query("SELECT * FROM shortcut_pin")
    suspend fun getShortcutPins(): List<ShortcutPinEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShortcutPin(pin: ShortcutPinEntity)

    @Query("DELETE FROM shortcut_pin")
    suspend fun deleteAllShortcuts()

    // Icon overrides
    @Query("SELECT * FROM icon_override")
    fun observeIconOverrides(): Flow<List<IconOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIconOverride(override: IconOverrideEntity)

    @Query("DELETE FROM icon_override")
    suspend fun deleteAllIconOverrides()

    // App usage daily (30-day local retention)
    @Query("SELECT * FROM app_usage_daily WHERE package_name = :pkg AND day = :epochDay LIMIT 1")
    suspend fun getDailyUsage(pkg: String, epochDay: Long): AppUsageDailyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyUsage(entity: AppUsageDailyEntity)

    @Query("DELETE FROM app_usage_daily WHERE day < :cutoffEpochDay")
    suspend fun pruneDailyUsageBefore(cutoffEpochDay: Long)

    @Query("DELETE FROM app_usage_daily")
    suspend fun deleteAllDailyUsage()

    // Search history (max 50 rows)
    @Query("SELECT * FROM search_history ORDER BY ts DESC LIMIT 50")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchQuery(query: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(entry: SearchHistoryEntity)

    @Query(
        """
        DELETE FROM search_history
        WHERE id NOT IN (SELECT id FROM search_history ORDER BY ts DESC LIMIT 50)
        """,
    )
    suspend fun trimSearchHistoryTo50()

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // Layout snapshots (keep latest 5)
    @Query("SELECT * FROM layout_snapshot ORDER BY created_at DESC LIMIT 5")
    fun observeSnapshots(): Flow<List<LayoutSnapshotEntity>>

    @Query("SELECT * FROM layout_snapshot ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestSnapshot(): LayoutSnapshotEntity?

    @Query("SELECT * FROM layout_snapshot WHERE id = :id LIMIT 1")
    suspend fun getSnapshotById(id: Long): LayoutSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: LayoutSnapshotEntity): Long

    @Query(
        """
        DELETE FROM layout_snapshot
        WHERE id NOT IN (SELECT id FROM layout_snapshot ORDER BY created_at DESC LIMIT 5)
        """,
    )
    suspend fun trimSnapshotsTo5()

    @Query("DELETE FROM layout_snapshot")
    suspend fun deleteAllSnapshots()
}
