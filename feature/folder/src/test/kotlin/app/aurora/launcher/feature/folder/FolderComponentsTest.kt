package app.aurora.launcher.feature.folder

import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.ContainerPlacement
import app.aurora.launcher.core.model.ItemId
import app.aurora.launcher.core.model.LeafPlacement
import app.aurora.launcher.core.model.PageId
import app.aurora.launcher.core.model.WorkspaceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderComponentsTest {

    private val pageOne = PageId(1L)
    private val folderId = ItemId(100L)

    private val childApp1 = WorkspaceItem.App(
        id = ItemId(101L),
        placement = LeafPlacement.InFolder(folderId, 0),
        component = ComponentKey("com.example.app1", "MainActivity", 0L),
    )
    private val childApp2 = WorkspaceItem.App(
        id = ItemId(102L),
        placement = LeafPlacement.InFolder(folderId, 1),
        component = ComponentKey("com.example.app2", "MainActivity", 0L),
    )
    private val childApp3 = WorkspaceItem.App(
        id = ItemId(103L),
        placement = LeafPlacement.InFolder(folderId, 2),
        component = ComponentKey("com.example.app3", "MainActivity", 0L),
    )

    private val testFolder = WorkspaceItem.Folder(
        id = folderId,
        placement = ContainerPlacement.Desktop(pageOne, 0, 0),
        title = "Social Hub",
        children = listOf(childApp1, childApp2, childApp3),
    )

    @Test
    fun folderPreviewTakesAtMostNineChildren() {
        val manyChildren = (1..15).map { idx ->
            WorkspaceItem.App(
                id = ItemId(idx.toLong()),
                placement = LeafPlacement.InFolder(folderId, idx),
                component = ComponentKey("pkg.$idx", "Main", 0L),
            )
        }
        val largeFolder = testFolder.copy(children = manyChildren)
        val preview = largeFolder.children.take(9)

        assertEquals(9, preview.size)
        assertEquals(ItemId(1L), preview.first().id)
        assertEquals(ItemId(9L), preview.last().id)
    }

    @Test
    fun folderTitleTrimmingAndFallback() {
        val fallback = "Folder"

        val blankTitle = "    "
        val resolvedBlank = blankTitle.trim().ifBlank { fallback }
        assertEquals("Folder", resolvedBlank)

        val normalTitle = "  Productivity  "
        val resolvedNormal = normalTitle.trim().ifBlank { fallback }
        assertEquals("Productivity", resolvedNormal)

        val overlyLongTitle = "A".repeat(50)
        val clampedTitle = overlyLongTitle.take(36)
        assertEquals(36, clampedTitle.length)
        assertEquals("A".repeat(36), clampedTitle)
    }

    @Test
    fun folderExtractionActionTargetSelection() {
        var selectedChildForAction: WorkspaceItem.App? = null
        assertNull(selectedChildForAction)

        selectedChildForAction = childApp2
        assertEquals(ItemId(102L), selectedChildForAction.id)
        assertEquals("com.example.app2", selectedChildForAction.component.packageName)

        var extractedChildId: ItemId? = null
        val onRemoveChildFromFolder: (ItemId) -> Unit = { id ->
            extractedChildId = id
        }

        val target = selectedChildForAction
        selectedChildForAction = null
        target?.let { onRemoveChildFromFolder(it.id) }

        assertNull(selectedChildForAction)
        assertEquals(ItemId(102L), extractedChildId)
    }

    @Test
    fun folderRenameCallbackInvokedWithTrimmedTitle() {
        var renamedFolderId: ItemId? = null
        var renamedTitle: String? = null

        val onRenameFolder: (ItemId, String) -> Unit = { id, title ->
            renamedFolderId = id
            renamedTitle = title
        }

        val draft = "  Finance Apps  "
        val fallback = "Folder"
        onRenameFolder(testFolder.id, draft.trim().ifBlank { fallback })

        assertEquals(folderId, renamedFolderId)
        assertEquals("Finance Apps", renamedTitle)
    }
}
