package app.aurora.launcher.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workspace_page",
    indices = [Index(value = ["position"], unique = true)],
)
data class WorkspacePageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "position")
    val position: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

@Entity(
    tableName = "workspace_item",
    indices = [
        Index(value = ["container", "container_id"], name = "idx_item_container"),
    ],
)
data class WorkspaceItemEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "type")
    val type: String, // APP, FOLDER, WIDGET, WIDGET_STACK, SHORTCUT
    @ColumnInfo(name = "container")
    val container: String, // DESKTOP, DOCK, FOLDER, STACK
    @ColumnInfo(name = "container_id")
    val containerId: Long? = null,
    @ColumnInfo(name = "cell_x")
    val cellX: Int = 0,
    @ColumnInfo(name = "cell_y")
    val cellY: Int = 0,
    @ColumnInfo(name = "span_x")
    val spanX: Int = 1,
    @ColumnInfo(name = "span_y")
    val spanY: Int = 1,
    @ColumnInfo(name = "rank")
    val rank: Int = 0,
    @ColumnInfo(name = "title")
    val title: String? = null,
    @ColumnInfo(name = "package_name")
    val packageName: String? = null,
    @ColumnInfo(name = "activity_class")
    val activityClass: String? = null,
    @ColumnInfo(name = "user_serial")
    val userSerial: Long = 0L,
    @ColumnInfo(name = "orphan_since")
    val orphanSince: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

@Entity(
    tableName = "app_entry",
    primaryKeys = ["package_name", "activity_class", "user_serial"],
    indices = [Index(value = ["label_sort_key"], name = "idx_app_label")],
)
data class AppEntryEntity(
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "activity_class")
    val activityClass: String,
    @ColumnInfo(name = "user_serial")
    val userSerial: Long,
    @ColumnInfo(name = "label")
    val label: String,
    @ColumnInfo(name = "label_sort_key")
    val labelSortKey: String,
    @ColumnInfo(name = "category")
    val category: Int? = null,
    @ColumnInfo(name = "library_group")
    val libraryGroup: String,
    @ColumnInfo(name = "user_group_override")
    val userGroupOverride: String? = null,
    @ColumnInfo(name = "is_hidden")
    val isHidden: Boolean = false,
    @ColumnInfo(name = "is_locked")
    val isLocked: Boolean = false,
    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false,
    @ColumnInfo(name = "is_suspended")
    val isSuspended: Boolean = false,
    @ColumnInfo(name = "is_quiet_paused")
    val isQuietPaused: Boolean = false,
    @ColumnInfo(name = "profile_badge")
    val profileBadge: String = "PERSONAL",
    @ColumnInfo(name = "install_time")
    val installTime: Long = 0L,
    @ColumnInfo(name = "update_time")
    val updateTime: Long = 0L,
    @ColumnInfo(name = "launch_count")
    val launchCount: Int = 0,
    @ColumnInfo(name = "last_launch_at")
    val lastLaunchAt: Long? = null,
)

@Entity(
    tableName = "widget_instance",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["item_id"])],
)
data class WidgetInstanceEntity(
    @PrimaryKey
    @ColumnInfo(name = "app_widget_id")
    val appWidgetId: Int,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "provider_package")
    val providerPackage: String,
    @ColumnInfo(name = "provider_class")
    val providerClass: String,
    @ColumnInfo(name = "user_serial")
    val userSerial: Long = 0L,
    @ColumnInfo(name = "label")
    val label: String = "",
    @ColumnInfo(name = "min_span_x")
    val minSpanX: Int = 1,
    @ColumnInfo(name = "min_span_y")
    val minSpanY: Int = 1,
    @ColumnInfo(name = "max_span_x")
    val maxSpanX: Int = 4,
    @ColumnInfo(name = "max_span_y")
    val maxSpanY: Int = 4,
    @ColumnInfo(name = "needs_config")
    val needsConfig: Boolean = false,
    @ColumnInfo(name = "status")
    val status: String = "ACTIVE",
)

@Entity(
    tableName = "shortcut_pin",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ShortcutPinEntity(
    @PrimaryKey
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "shortcut_id")
    val shortcutId: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "user_serial")
    val userSerial: Long = 0L,
    @ColumnInfo(name = "label")
    val label: String,
)

@Entity(
    tableName = "icon_override",
    primaryKeys = ["package_name", "activity_class"],
)
data class IconOverrideEntity(
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "activity_class")
    val activityClass: String,
    @ColumnInfo(name = "pack_id")
    val packId: String,
    @ColumnInfo(name = "drawable_name")
    val drawableName: String,
)

@Entity(
    tableName = "app_usage_daily",
    primaryKeys = ["package_name", "day"],
)
data class AppUsageDailyEntity(
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "day")
    val day: Long,
    @ColumnInfo(name = "launches")
    val launches: Int = 0,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "query")
    val query: String,
    @ColumnInfo(name = "ts")
    val ts: Long,
)

@Entity(tableName = "layout_snapshot")
data class LayoutSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int,
    @ColumnInfo(name = "reason")
    val reason: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "json_blob")
    val jsonBlob: String,
)
