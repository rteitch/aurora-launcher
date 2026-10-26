package app.aurora.launcher.core.data.backup

import app.aurora.launcher.core.common.SystemResult
import app.aurora.launcher.core.data.db.AppEntryEntity
import app.aurora.launcher.core.data.db.ShortcutPinEntity
import app.aurora.launcher.core.data.db.WidgetInstanceEntity
import app.aurora.launcher.core.data.db.WorkspaceItemEntity
import app.aurora.launcher.core.data.db.WorkspacePageEntity
import app.aurora.launcher.core.data.repository.WorkspaceRepositoryImpl
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.ContainerPlacement
import app.aurora.launcher.core.model.GridSpec
import app.aurora.launcher.core.model.ItemId
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.LeafPlacement
import app.aurora.launcher.core.model.PageId
import app.aurora.launcher.core.model.ThemeMode
import app.aurora.launcher.core.model.WorkspaceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupAndSnapshotAssemblyTest {

    @Test
    fun `BackupPayloadCodec round-trips valid payload and strips unknown JSON fields`() {
        val pageId = PageId(1L)
        val app = WorkspaceItem.App(
            id = ItemId(10L),
            placement = LeafPlacement.Desktop(pageId, 2, 3),
            component = ComponentKey("com.whatsapp", "com.whatsapp.Main", 0L),
        )
        val original = AuroraBackupPayload(
            exportedAtEpochMs = 1_760_000_000_000L,
            gridColumns = 4,
            gridRows = 6,
            dockSlots = 4,
            pages = listOf(BackupPageDto(1L, 0, listOf(app))),
            dockItems = emptyList(),
            settings = LauncherSettings(themeMode = ThemeMode.DARK),
        )
        val raw = BackupPayloadCodec.encode(original)
        val withUnknownField = raw.dropLast(1) + ",\"unexpectedFutureField\":\"ignored\"}"

        val result = BackupPayloadCodec.decodeAndValidate(withUnknownField)
        assertTrue(result is SystemResult.Success)
        val decoded = (result as SystemResult.Success).data
        assertEquals(original, decoded)
    }

    @Test
    fun `BackupPayloadCodec rejects corrupt JSON, unsupported schema, and oversized payloads`() {
        assertTrue(BackupPayloadCodec.decodeAndValidate("") is SystemResult.Failure)
        assertTrue(BackupPayloadCodec.decodeAndValidate("{not valid json") is SystemResult.Failure)
        assertTrue(
            BackupPayloadCodec.decodeAndValidate(
                """{"schemaVersion":99,"exportedAtEpochMs":1,"gridColumns":4,"gridRows":6,"dockSlots":4,"pages":[],"dockItems":[]}""",
            ) is SystemResult.Failure,
        )
        val over5Mb = "a".repeat(AuroraBackupPayload.MAX_BACKUP_BYTES + 32)
        assertTrue(BackupPayloadCodec.decodeAndValidate(over5Mb) is SystemResult.Failure)
    }

    @Test
    fun `assembleSnapshot reconstructs desktop folders, widgets, widget stacks, and dock items in order`() {
        val pages = listOf(WorkspacePageEntity(id = 1L, position = 0, createdAt = 100L))
        val items = listOf(
            WorkspaceItemEntity(
                id = 10L,
                type = "FOLDER",
                container = "DESKTOP",
                containerId = 1L,
                cellX = 1,
                cellY = 2,
                title = "Kerja",
                createdAt = 100L,
                updatedAt = 100L,
            ),
            WorkspaceItemEntity(
                id = 11L,
                type = "APP",
                container = "FOLDER",
                containerId = 10L,
                rank = 0,
                packageName = "com.slack",
                activityClass = "Main",
                createdAt = 100L,
                updatedAt = 100L,
            ),
            WorkspaceItemEntity(
                id = 20L,
                type = "WIDGET_STACK",
                container = "DESKTOP",
                containerId = 1L,
                cellX = 0,
                cellY = 0,
                spanX = 2,
                spanY = 2,
                rank = 1,
                createdAt = 100L,
                updatedAt = 100L,
            ),
            WorkspaceItemEntity(
                id = 21L,
                type = "WIDGET",
                container = "STACK",
                containerId = 20L,
                spanX = 2,
                spanY = 2,
                rank = 0,
                createdAt = 100L,
                updatedAt = 100L,
            ),
            WorkspaceItemEntity(
                id = 22L,
                type = "WIDGET",
                container = "STACK",
                containerId = 20L,
                spanX = 2,
                spanY = 2,
                rank = 1,
                createdAt = 100L,
                updatedAt = 100L,
            ),
            WorkspaceItemEntity(
                id = 30L,
                type = "APP",
                container = "DOCK",
                rank = 0,
                packageName = "com.android.dialer",
                activityClass = "DialtactsActivity",
                createdAt = 100L,
                updatedAt = 100L,
            ),
        )
        val widgets = listOf(
            WidgetInstanceEntity(appWidgetId = 501, itemId = 21L, providerPackage = "pkg.clock", providerClass = "Clock"),
            WidgetInstanceEntity(appWidgetId = 502, itemId = 22L, providerPackage = "pkg.weather", providerClass = "Weather"),
        )

        val snapshot = WorkspaceRepositoryImpl.assembleSnapshot(
            pages = pages,
            items = items,
            widgets = widgets,
            shortcuts = emptyList<ShortcutPinEntity>(),
            gridSpec = GridSpec(4, 6, 4),
        )

        assertEquals(1, snapshot.pages.size)
        val folder = snapshot.pages[0].items.filterIsInstance<WorkspaceItem.Folder>().single()
        assertEquals("Kerja", folder.title)
        assertEquals(ContainerPlacement.Desktop(PageId(1L), 1, 2), folder.placement)
        assertEquals(1, folder.children.size)

        val stack = snapshot.pages[0].items.filterIsInstance<WorkspaceItem.WidgetStack>().single()
        assertEquals(1, stack.activeIndex)
        assertEquals(listOf(501, 502), stack.widgets.map { it.appWidgetId.value })

        assertEquals(1, snapshot.dockItems.size)
    }
}
