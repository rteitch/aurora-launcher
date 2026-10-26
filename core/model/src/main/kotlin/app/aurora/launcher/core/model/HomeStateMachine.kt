package app.aurora.launcher.core.model

data class DragSessionState(
    val draggedItemId: ItemId,
    val sourceComponent: ComponentKey?,
    val originPageIndex: Int,
    val pointerX: Float = 0f,
    val pointerY: Float = 0f,
    val hoveredTarget: DropTarget? = null,
)

sealed interface HomeUiMode {
    data object Normal : HomeUiMode

    data class EditMode(
        val dragSession: DragSessionState? = null,
    ) : HomeUiMode

    data class QuickActions(
        val targetItemId: ItemId,
    ) : HomeUiMode
}

sealed interface ShellOverlay {
    data object None : ShellOverlay
    data class Folder(val folderId: ItemId, val isRenaming: Boolean = false) : ShellOverlay
    data object Spotlight : ShellOverlay
    data object WidgetPicker : ShellOverlay
    data object ControlPanel : ShellOverlay
    data object Settings : ShellOverlay
}
