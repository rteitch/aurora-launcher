package app.aurora.launcher.core.data.backup

import app.aurora.launcher.core.common.SystemResult
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.WorkspaceItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupPageDto(
    val id: Long,
    val position: Int,
    val items: List<WorkspaceItem>,
)

@Serializable
data class AuroraBackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAtEpochMs: Long,
    val gridColumns: Int,
    val gridRows: Int,
    val dockSlots: Int,
    val pages: List<BackupPageDto>,
    val dockItems: List<WorkspaceItem>,
    val settings: LauncherSettings? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
        const val MAX_BACKUP_BYTES: Int = 5 * 1024 * 1024 // 5 MB (prd.md 9.5)
        const val MAX_ITEMS: Int = 1000
    }
}

object BackupPayloadCodec {
    // Explicit non-polymorphic JSON configuration with unknown field ignoring (prd.md 9.5, 11.3, E32)
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        allowStructuredMapKeys = false
    }

    fun encode(payload: AuroraBackupPayload): String = json.encodeToString(payload)

    fun decodeAndValidate(rawJson: String): SystemResult<AuroraBackupPayload> {
        val byteLen = rawJson.toByteArray(Charsets.UTF_8).size
        if (byteLen == 0) {
            return SystemResult.Failure("Backup file is empty")
        }
        if (byteLen > AuroraBackupPayload.MAX_BACKUP_BYTES) {
            return SystemResult.Failure("Backup exceeds 5 MB security limit")
        }

        val decoded = try {
            json.decodeFromString<AuroraBackupPayload>(rawJson)
        } catch (e: Exception) {
            return SystemResult.Failure("Invalid or corrupted backup JSON format", e)
        }

        if (decoded.schemaVersion !in 1..AuroraBackupPayload.CURRENT_SCHEMA_VERSION) {
            return SystemResult.Failure("Unsupported backup schema version: ${decoded.schemaVersion}")
        }
        if (decoded.gridColumns !in 3..8 || decoded.gridRows !in 3..8 || decoded.dockSlots !in 3..7) {
            return SystemResult.Failure("Invalid grid dimensions in backup")
        }
        if (decoded.pages.size > 15) {
            return SystemResult.Failure("Backup exceeds maximum of 15 pages")
        }

        val totalItems = decoded.pages.sumOf { page ->
            page.items.sumOf { countNestedItems(it) }
        } + decoded.dockItems.sumOf { countNestedItems(it) }

        if (totalItems > AuroraBackupPayload.MAX_ITEMS) {
            return SystemResult.Failure("Backup item count ($totalItems) exceeds limit (${AuroraBackupPayload.MAX_ITEMS})")
        }

        return SystemResult.Success(decoded)
    }

    private fun countNestedItems(item: WorkspaceItem): Int = when (item) {
        is WorkspaceItem.Folder -> 1 + item.children.size
        is WorkspaceItem.WidgetStack -> 1 + item.widgets.size
        else -> 1
    }
}
