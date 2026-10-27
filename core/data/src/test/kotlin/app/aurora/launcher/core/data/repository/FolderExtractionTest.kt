package app.aurora.launcher.core.data.repository

import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.ContainerPlacement
import app.aurora.launcher.core.model.GridSpec
import app.aurora.launcher.core.model.ItemId
import app.aurora.launcher.core.model.LeafPlacement
import app.aurora.launcher.core.model.PageId
import app.aurora.launcher.core.model.WorkspaceItem
import app.aurora.launcher.core.model.WorkspacePage
import app.aurora.launcher.core.model.WorkspaceSnapshot
import app.aurora.launcher.core.testing.FakeWorkspaceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderExtractionTest {

    private val pageId = PageId(1L)
    private val app1 = WorkspaceItem.App(
        id = ItemId(101L),
        placement = LeafPlacement.InFolder(ItemId(10L), 0),
        component = ComponentKey("app.one", "app.one.Main", 0L),
    )
    private val app2 = WorkspaceItem.App(
        id = ItemId(102L),
        placement = LeafPlacement.InFolder(ItemId(10L), 1),
        component = ComponentKey("app.two", "app.two.Main", 0L),
    )
    private val app3 = WorkspaceItem.App(
        id = ItemId(103L),
        placement = LeafPlacement.InFolder(ItemId(10L), 2),
        component = ComponentKey("app.three", "app.three.Main", 0L),
    )

    @Test
    fun `extracting item from 3-item folder keeps folder with 2 remaining children and places extracted app on desktop`() = runTest {
        val folder = WorkspaceItem.Folder(
            id = ItemId(10L),
            placement = ContainerPlacement.Desktop(pageId, 0, 0),
            title = "Work",
            children = listOf(app1, app2, app3),
        )
        val initial = WorkspaceSnapshot(
            pages = listOf(WorkspacePage(pageId, 0, listOf(folder))),
            dockItems = emptyList(),
            gridSpec = GridSpec(4, 6, 4),
        )
        val repository = FakeWorkspaceRepository(initial)

        val success = repository.extractChildFromFolder(app3.id)
        assertTrue(success)

        val updated = repository.current.value
        val items = updated.pages.first().items
        assertEquals(2, items.size)

        val remainingFolder = items.filterIsInstance<WorkspaceItem.Folder>().single()
        assertEquals(2, remainingFolder.children.size)
        assertEquals(listOf(app1.id, app2.id), remainingFolder.children.map { it.id })

        val extracted = items.filterIsInstance<WorkspaceItem.App>().single()
        assertEquals(app3.id, extracted.id)
        assertTrue(extracted.placement is LeafPlacement.Desktop)
    }

    @Test
    fun `extracting item from 2-item folder dissolves folder into single remaining item at folder position`() = runTest {
        val folder = WorkspaceItem.Folder(
            id = ItemId(10L),
            placement = ContainerPlacement.Desktop(pageId, 1, 1),
            title = "Social",
            children = listOf(app1, app2),
        )
        val initial = WorkspaceSnapshot(
            pages = listOf(WorkspacePage(pageId, 0, listOf(folder))),
            dockItems = emptyList(),
            gridSpec = GridSpec(4, 6, 4),
        )
        val repository = FakeWorkspaceRepository(initial)

        val success = repository.extractChildFromFolder(app2.id)
        assertTrue(success)

        val updated = repository.current.value
        val items = updated.pages.first().items
        // Folder must be completely dissolved, leaving exactly 2 single Apps on the desktop
        assertTrue(items.none { it is WorkspaceItem.Folder })
        assertEquals(2, items.size)

        val dissolvedChild = items.first { it.id == app1.id }
        val extractedChild = items.first { it.id == app2.id }

        // Dissolved child must inherit the folder's coordinates (1, 1)
        val dissolvedPlacement = (dissolvedChild as WorkspaceItem.App).placement as LeafPlacement.Desktop
        assertEquals(1, dissolvedPlacement.cellX)
        assertEquals(1, dissolvedPlacement.cellY)

        assertTrue((extractedChild as WorkspaceItem.App).placement is LeafPlacement.Desktop)
    }

    @Test
    fun `extracting non-existent child returns false`() = runTest {
        val folder = WorkspaceItem.Folder(
            id = ItemId(10L),
            placement = ContainerPlacement.Desktop(pageId, 0, 0),
            title = "Tools",
            children = listOf(app1, app2),
        )
        val initial = WorkspaceSnapshot(
            pages = listOf(WorkspacePage(pageId, 0, listOf(folder))),
            dockItems = emptyList(),
            gridSpec = GridSpec(4, 6, 4),
        )
        val repository = FakeWorkspaceRepository(initial)

        val success = repository.extractChildFromFolder(ItemId(999L))
        assertFalse(success)
    }
}
