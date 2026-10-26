package app.aurora.launcher.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkspacePageEntity::class,
        WorkspaceItemEntity::class,
        AppEntryEntity::class,
        WidgetInstanceEntity::class,
        ShortcutPinEntity::class,
        IconOverrideEntity::class,
        AppUsageDailyEntity::class,
        SearchHistoryEntity::class,
        LayoutSnapshotEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AuroraDatabase : RoomDatabase() {
    abstract fun auroraDao(): AuroraDao

    companion object {
        val PartialIndexCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                ensurePartialCellIndex(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                ensurePartialCellIndex(db)
            }

            private fun ensurePartialCellIndex(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_item_cell
                    ON workspace_item(container, container_id, cell_x, cell_y)
                    WHERE span_x = 1 AND span_y = 1 AND container = 'DESKTOP'
                    """.trimIndent(),
                )
            }
        }
    }
}
