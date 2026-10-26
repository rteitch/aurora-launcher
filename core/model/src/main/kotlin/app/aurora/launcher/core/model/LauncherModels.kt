package app.aurora.launcher.core.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class PageId(val value: Long)

@Serializable
@JvmInline
value class ItemId(val value: Long)

@Serializable
@JvmInline
value class AppWidgetId(val value: Int)

@Serializable
data class ComponentKey(
    val packageName: String,
    val activityClass: String,
    val userSerial: Long = 0L,
) {
    fun asCompactKey(): String = "$packageName/$activityClass@$userSerial"
}

@Serializable
enum class ProfileBadge {
    PERSONAL,
    WORK,
    CLONE,
    PRIVATE,
}

@Serializable
enum class LibraryGroup {
    SOCIAL,
    PRODUCTIVITY_FINANCE,
    ENTERTAINMENT,
    PHOTO_VIDEO,
    UTILITIES,
    GAMES,
    SHOPPING,
    TRAVEL,
    OTHER,
}

@Serializable
data class AppEntry(
    val key: ComponentKey,
    val label: String,
    val sortKey: String,
    val systemCategory: Int? = null,
    val libraryGroup: LibraryGroup = LibraryGroup.OTHER,
    val userGroupOverride: LibraryGroup? = null,
    val isHidden: Boolean = false,
    val isLocked: Boolean = false,
    val isSystemApp: Boolean = false,
    val isSuspended: Boolean = false,
    val isQuietModePaused: Boolean = false,
    val profileBadge: ProfileBadge = ProfileBadge.PERSONAL,
    val installTimeEpochMs: Long = 0L,
    val updateTimeEpochMs: Long = 0L,
    val launchCount: Int = 0,
    val lastLaunchEpochMs: Long? = null,
) {
    val effectiveGroup: LibraryGroup
        get() = userGroupOverride ?: libraryGroup
}

@Serializable
sealed interface ContainerPlacement {
    @Serializable
    data class Desktop(
        val pageId: PageId,
        val cellX: Int,
        val cellY: Int,
    ) : ContainerPlacement

    @Serializable
    data class Dock(
        val rank: Int,
    ) : ContainerPlacement
}

@Serializable
sealed interface LeafPlacement {
    @Serializable
    data class Desktop(
        val pageId: PageId,
        val cellX: Int,
        val cellY: Int,
    ) : LeafPlacement

    @Serializable
    data class Dock(
        val rank: Int,
    ) : LeafPlacement

    @Serializable
    data class InFolder(
        val folderId: ItemId,
        val rank: Int,
    ) : LeafPlacement
}

@Serializable
enum class WidgetStatus {
    ACTIVE,
    NEEDS_CONFIG,
    PROVIDER_MISSING,
    ERROR,
}

@Serializable
sealed interface WorkspaceItem {
    val id: ItemId

    @Serializable
    data class App(
        override val id: ItemId,
        val placement: LeafPlacement,
        val component: ComponentKey,
        val orphanSinceEpochMs: Long? = null,
    ) : WorkspaceItem

    @Serializable
    data class PinnedShortcut(
        override val id: ItemId,
        val placement: LeafPlacement,
        val shortcutId: String,
        val packageName: String,
        val userSerial: Long,
        val label: String,
    ) : WorkspaceItem

    @Serializable
    data class Folder(
        override val id: ItemId,
        val placement: ContainerPlacement,
        val title: String,
        val children: List<App>,
    ) : WorkspaceItem

    @Serializable
    data class Widget(
        override val id: ItemId,
        val pageId: PageId,
        val cellX: Int,
        val cellY: Int,
        val spanX: Int,
        val spanY: Int,
        val appWidgetId: AppWidgetId,
        val provider: ComponentKey,
        val label: String = "",
        val minSpanX: Int = 1,
        val minSpanY: Int = 1,
        val maxSpanX: Int = 4,
        val maxSpanY: Int = 4,
        val status: WidgetStatus = WidgetStatus.ACTIVE,
    ) : WorkspaceItem

    @Serializable
    data class WidgetStack(
        override val id: ItemId,
        val pageId: PageId,
        val cellX: Int,
        val cellY: Int,
        val spanX: Int,
        val spanY: Int,
        val activeIndex: Int = 0,
        val widgets: List<Widget>,
    ) : WorkspaceItem
}

data class WorkspacePage(
    val id: PageId,
    val position: Int,
    val items: List<WorkspaceItem>,
)

data class GridSpec(
    val columns: Int,
    val rows: Int,
    val dockSlots: Int,
) {
    init {
        require(columns in 3..8) { "Columns out of bounds: $columns" }
        require(rows in 3..8) { "Rows out of bounds: $rows" }
        require(dockSlots in 3..7) { "Dock slots out of bounds: $dockSlots" }
    }
}

data class WorkspaceSnapshot(
    val pages: List<WorkspacePage>,
    val dockItems: List<WorkspaceItem>,
    val gridSpec: GridSpec,
)

data class AppShortcutItem(
    val id: String,
    val packageName: String,
    val userSerial: Long,
    val shortLabel: String,
    val longLabel: String,
    val isDynamic: Boolean,
)

data class WidgetProviderCandidate(
    val provider: ComponentKey,
    val appLabel: String,
    val widgetLabel: String,
    val description: String,
    val defaultSpanX: Int,
    val defaultSpanY: Int,
    val minSpanX: Int,
    val minSpanY: Int,
    val maxSpanX: Int,
    val maxSpanY: Int,
    val hasConfigurationActivity: Boolean,
)
