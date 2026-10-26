package app.aurora.launcher.core.data.repository

import android.content.Context
import androidx.room.withTransaction
import app.aurora.launcher.core.data.backup.AuroraBackupPayload
import app.aurora.launcher.core.data.backup.BackupPageDto
import app.aurora.launcher.core.data.backup.BackupPayloadCodec
import app.aurora.launcher.core.data.db.AuroraDao
import app.aurora.launcher.core.data.db.AuroraDatabase
import app.aurora.launcher.core.data.db.LayoutSnapshotEntity
import app.aurora.launcher.core.data.db.ShortcutPinEntity
import app.aurora.launcher.core.data.db.WidgetInstanceEntity
import app.aurora.launcher.core.data.db.WorkspaceItemEntity
import app.aurora.launcher.core.data.db.WorkspacePageEntity
import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.AppWidgetId
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.ContainerPlacement
import app.aurora.launcher.core.model.DropResolution
import app.aurora.launcher.core.model.GridOccupancyEngine
import app.aurora.launcher.core.model.GridSpec
import app.aurora.launcher.core.model.ItemId
import app.aurora.launcher.core.model.LeafPlacement
import app.aurora.launcher.core.model.PageId
import app.aurora.launcher.core.model.WidgetProviderCandidate
import app.aurora.launcher.core.model.WidgetStatus
import app.aurora.launcher.core.model.WorkspaceItem
import app.aurora.launcher.core.model.WorkspacePage
import app.aurora.launcher.core.model.WorkspaceSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AuroraDatabase,
    private val dao: AuroraDao,
) : WorkspaceRepository {

    private val cloudBackupFile: File
        get() = File(context.filesDir, "cloud_backup/workspace_layout.json")

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dragMutex = Mutex()
    private var activeDragLease: ActiveDragLease? = null
    private var pendingReconciliation: PendingReconciliation? = null
    private val syntheticIdCounter = AtomicLong(System.currentTimeMillis())

    private data class ActiveDragLease(
        val id: Long,
        val draggedKey: ComponentKey?,
        val onDraggedSourceRemoved: () -> Unit,
    )

    private data class PendingReconciliation(
        val presentKeys: Set<ComponentKey>,
        val uninstalledPackages: Set<String>,
        val nowEpochMs: Long,
    )

    override val snapshots: Flow<List<SnapshotSummary>> = dao.observeSnapshots().map { list ->
        list.map { SnapshotSummary(id = it.id, reason = it.reason, createdAtEpochMs = it.createdAt) }
    }

    override fun observeWorkspace(gridSpecFlow: Flow<GridSpec>): Flow<WorkspaceSnapshot> {
        return combine(
            dao.observePages(),
            dao.observeWorkspaceItems(),
            dao.observeWidgetInstances(),
            dao.observeShortcutPins(),
            gridSpecFlow,
        ) { pages, items, widgets, shortcuts, spec ->
            assembleSnapshot(pages, items, widgets, shortcuts, spec)
        }.onEach { snapshot ->
            if (snapshot.pages.any { it.items.isNotEmpty() } || snapshot.dockItems.isNotEmpty()) {
                repoScope.launch { syncCloudLayoutMirror(snapshot) }
            }
        }
    }

    internal fun syncCloudLayoutMirror(snapshot: WorkspaceSnapshot) {
        runCatching {
            val payload = AuroraBackupPayload(
                exportedAtEpochMs = System.currentTimeMillis(),
                gridColumns = snapshot.gridSpec.columns,
                gridRows = snapshot.gridSpec.rows,
                dockSlots = snapshot.gridSpec.dockSlots,
                pages = snapshot.pages.map { BackupPageDto(it.id.value, it.position, it.items) },
                dockItems = snapshot.dockItems,
                settings = null,
            )
            cloudBackupFile.parentFile?.mkdirs()
            cloudBackupFile.writeText(BackupPayloadCodec.encode(payload), Charsets.UTF_8)
        }
    }

    internal fun clearCloudLayoutMirror() {
        runCatching { cloudBackupFile.delete() }
    }

    override suspend fun seedInitialWorkspaceIfEmpty(
        installedApps: List<AppEntry>,
        gridSpec: GridSpec,
    ) {
        if (dao.getPages().isEmpty() && cloudBackupFile.exists()) {
            val restored = runCatching { restoreFromJsonBlob(cloudBackupFile.readText(Charsets.UTF_8)) }
                .getOrDefault(false)
            if (restored) return
        }
        database.withTransaction {
            if (dao.getPages().isNotEmpty() && dao.getWorkspaceItems().isNotEmpty()) {
                return@withTransaction
            }
            val now = System.currentTimeMillis()
            val visible = installedApps.filter { !it.isHidden }

            // Select up to dockSlots primary daily apps (dialer, messages, browser, camera)
            val dockPriorityKeywords = listOf("dialer", "phone", "messaging", "whatsapp", "chrome", "browser", "camera")
            val dockCandidates = mutableListOf<AppEntry>()
            for (keyword in dockPriorityKeywords) {
                if (dockCandidates.size >= gridSpec.dockSlots) break
                val match = visible.firstOrNull { app ->
                    app !in dockCandidates && app.key.packageName.lowercase().contains(keyword)
                }
                if (match != null) dockCandidates.add(match)
            }
            for (app in visible) {
                if (dockCandidates.size >= gridSpec.dockSlots) break
                if (app !in dockCandidates) dockCandidates.add(app)
            }

            val desktopApps = visible.filter { it !in dockCandidates }
            val perPage = gridSpec.columns * gridSpec.rows
            val chunks = desktopApps.chunked(perPage)
                .take(GridOccupancyEngine.MAX_PAGES)
                .ifEmpty { listOf(emptyList()) }

            dao.deleteAllWorkspaceItems()
            dao.deleteAllPages()

            chunks.forEachIndexed { pageIndex, pageApps ->
                val pageId = dao.insertPage(
                    WorkspacePageEntity(position = pageIndex, createdAt = now),
                )
                pageApps.forEachIndexed { idx, app ->
                    val cellX = idx % gridSpec.columns
                    val cellY = idx / gridSpec.columns
                    dao.insertWorkspaceItem(
                        WorkspaceItemEntity(
                            type = "APP",
                            container = "DESKTOP",
                            containerId = pageId,
                            cellX = cellX,
                            cellY = cellY,
                            rank = idx,
                            packageName = app.key.packageName,
                            activityClass = app.key.activityClass,
                            userSerial = app.key.userSerial,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
            }

            dockCandidates.forEachIndexed { rank, app ->
                dao.insertWorkspaceItem(
                    WorkspaceItemEntity(
                        type = "APP",
                        container = "DOCK",
                        containerId = null,
                        rank = rank,
                        packageName = app.key.packageName,
                        activityClass = app.key.activityClass,
                        userSerial = app.key.userSerial,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    override suspend fun applyDrop(snapshot: WorkspaceSnapshot, resolution: DropResolution) {
        if (resolution is DropResolution.Reject) return
        database.withTransaction {
            saveSnapshotInternal(snapshot, reason = "Before layout edit")
            val updated = applyDropToSnapshot(snapshot, resolution)
            persistSnapshotInternal(GridOccupancyEngine.normalizeSnapshot(updated))
        }
    }

    override suspend fun addAppToHome(component: ComponentKey, gridSpec: GridSpec): Boolean {
        return database.withTransaction {
            val current = loadCurrentSnapshot(gridSpec)
            val alreadyPresent = (current.pages.flatMap { it.items } + current.dockItems).any { item ->
                when (item) {
                    is WorkspaceItem.App -> item.component == component
                    is WorkspaceItem.Folder -> item.children.any { it.component == component }
                    else -> false
                }
            }
            if (alreadyPresent) return@withTransaction true

            for (page in current.pages) {
                val free = GridOccupancyEngine.findFirstFreeSpan(
                    items = page.items,
                    columns = gridSpec.columns,
                    rows = gridSpec.rows,
                    spanX = 1,
                    spanY = 1,
                )
                if (free != null) {
                    val newItem = WorkspaceItem.App(
                        id = nextItemId(),
                        placement = LeafPlacement.Desktop(page.id, free.first, free.second),
                        component = component,
                    )
                    val updatedPages = current.pages.map {
                        if (it.id == page.id) it.copy(items = it.items + newItem) else it
                    }
                    persistSnapshotInternal(current.copy(pages = updatedPages))
                    return@withTransaction true
                }
            }

            if (current.pages.size >= GridOccupancyEngine.MAX_PAGES) {
                return@withTransaction false
            }

            val newPageId = PageId(nextItemId().value)
            val newApp = WorkspaceItem.App(
                id = nextItemId(),
                placement = LeafPlacement.Desktop(newPageId, 0, 0),
                component = component,
            )
            val newPage = WorkspacePage(
                id = newPageId,
                position = current.pages.size,
                items = listOf(newApp),
            )
            persistSnapshotInternal(current.copy(pages = current.pages + newPage))
            true
        }
    }

    override suspend fun removeItemFromHome(itemId: ItemId): List<AppWidgetId> {
        return database.withTransaction {
            val current = loadCurrentSnapshot(GridSpec(4, 6, 4))
            saveSnapshotInternal(current, reason = "Before item removal")
            val removedWidgetIds = mutableListOf<AppWidgetId>()

            fun filterList(items: List<WorkspaceItem>): List<WorkspaceItem> {
                return items.mapNotNull { item ->
                    when {
                        item.id == itemId -> {
                            if (item is WorkspaceItem.Widget) removedWidgetIds.add(item.appWidgetId)
                            if (item is WorkspaceItem.WidgetStack) {
                                removedWidgetIds.addAll(item.widgets.map { it.appWidgetId })
                            }
                            null
                        }
                        item is WorkspaceItem.Folder -> {
                            item.copy(children = item.children.filter { it.id != itemId })
                        }
                        item is WorkspaceItem.WidgetStack -> {
                            val removedChild = item.widgets.firstOrNull { it.id == itemId }
                            if (removedChild != null) removedWidgetIds.add(removedChild.appWidgetId)
                            item.copy(widgets = item.widgets.filter { it.id != itemId })
                        }
                        else -> item
                    }
                }
            }

            val next = WorkspaceSnapshot(
                pages = current.pages.map { it.copy(items = filterList(it.items)) },
                dockItems = filterList(current.dockItems),
                gridSpec = current.gridSpec,
            )
            persistSnapshotInternal(GridOccupancyEngine.normalizeSnapshot(next))
            removedWidgetIds
        }
    }

    override suspend fun renameFolder(folderId: ItemId, newTitle: String) {
        val sanitized = newTitle.trim().take(32).ifEmpty { "Folder" }
        val existing = dao.getWorkspaceItems().firstOrNull { it.id == folderId.value } ?: return
        dao.updateWorkspaceItem(existing.copy(title = sanitized, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun placeWidget(
        candidate: WidgetProviderCandidate,
        appWidgetId: AppWidgetId,
        preferredPageId: PageId?,
        gridSpec: GridSpec,
        needsConfig: Boolean,
    ): ItemId? {
        return database.withTransaction {
            val current = loadCurrentSnapshot(gridSpec)
            saveSnapshotInternal(current, reason = "Before widget placement")
            val sx = candidate.defaultSpanX.coerceIn(1, gridSpec.columns)
            val sy = candidate.defaultSpanY.coerceIn(1, gridSpec.rows)

            val orderedPages = if (preferredPageId != null) {
                current.pages.sortedBy { if (it.id == preferredPageId) 0 else 1 + it.position }
            } else {
                current.pages
            }

            for (page in orderedPages) {
                val free = GridOccupancyEngine.findFirstFreeSpan(page.items, gridSpec.columns, gridSpec.rows, sx, sy)
                if (free != null) {
                    val widgetItemId = nextItemId()
                    val widget = WorkspaceItem.Widget(
                        id = widgetItemId,
                        pageId = page.id,
                        cellX = free.first,
                        cellY = free.second,
                        spanX = sx,
                        spanY = sy,
                        appWidgetId = appWidgetId,
                        provider = candidate.provider,
                        label = candidate.widgetLabel,
                        minSpanX = candidate.minSpanX.coerceAtLeast(1),
                        minSpanY = candidate.minSpanY.coerceAtLeast(1),
                        maxSpanX = candidate.maxSpanX.coerceAtLeast(sx),
                        maxSpanY = candidate.maxSpanY.coerceAtLeast(sy),
                        status = if (needsConfig) WidgetStatus.NEEDS_CONFIG else WidgetStatus.ACTIVE,
                    )
                    val updatedPages = current.pages.map {
                        if (it.id == page.id) it.copy(items = it.items + widget) else it
                    }
                    persistSnapshotInternal(current.copy(pages = updatedPages))
                    return@withTransaction widgetItemId
                }
            }

            if (current.pages.size >= GridOccupancyEngine.MAX_PAGES) {
                return@withTransaction null
            }

            val newPageId = PageId(nextItemId().value)
            val widgetItemId = nextItemId()
            val widget = WorkspaceItem.Widget(
                id = widgetItemId,
                pageId = newPageId,
                cellX = 0,
                cellY = 0,
                spanX = sx,
                spanY = sy,
                appWidgetId = appWidgetId,
                provider = candidate.provider,
                label = candidate.widgetLabel,
                minSpanX = candidate.minSpanX.coerceAtLeast(1),
                minSpanY = candidate.minSpanY.coerceAtLeast(1),
                maxSpanX = candidate.maxSpanX.coerceAtLeast(sx),
                maxSpanY = candidate.maxSpanY.coerceAtLeast(sy),
                status = if (needsConfig) WidgetStatus.NEEDS_CONFIG else WidgetStatus.ACTIVE,
            )
            val newPage = WorkspacePage(
                id = newPageId,
                position = current.pages.size,
                items = listOf(widget),
            )
            persistSnapshotInternal(current.copy(pages = current.pages + newPage))
            widgetItemId
        }
    }

    override suspend fun markWidgetConfigured(appWidgetId: AppWidgetId) {
        val instance = dao.getWidgetInstances().firstOrNull { it.appWidgetId == appWidgetId.value } ?: return
        dao.upsertWidgetInstance(instance.copy(needsConfig = false, status = WidgetStatus.ACTIVE.name))
    }

    override suspend fun resizeWidget(
        itemId: ItemId,
        newSpanX: Int,
        newSpanY: Int,
        gridSpec: GridSpec,
    ): Boolean {
        return database.withTransaction {
            val current = loadCurrentSnapshot(gridSpec)
            val page = current.pages.firstOrNull { p -> p.items.any { it.id == itemId } } ?: return@withTransaction false
            val target = page.items.first { it.id == itemId }
            val (cellX, cellY) = when (target) {
                is WorkspaceItem.Widget -> target.cellX to target.cellY
                is WorkspaceItem.WidgetStack -> target.cellX to target.cellY
                else -> return@withTransaction false
            }
            val sx = newSpanX.coerceIn(1, gridSpec.columns)
            val sy = newSpanY.coerceIn(1, gridSpec.rows)
            if (!GridOccupancyEngine.canFit(page.items, gridSpec.columns, gridSpec.rows, cellX, cellY, sx, sy, itemId)) {
                return@withTransaction false
            }
            val resized = when (target) {
                is WorkspaceItem.Widget -> target.copy(spanX = sx, spanY = sy)
                is WorkspaceItem.WidgetStack -> target.copy(
                    spanX = sx,
                    spanY = sy,
                    widgets = target.widgets.map { it.copy(spanX = sx, spanY = sy) },
                )
                else -> target
            }
            val updatedPages = current.pages.map { p ->
                if (p.id == page.id) p.copy(items = p.items.map { if (it.id == itemId) resized else it }) else p
            }
            persistSnapshotInternal(current.copy(pages = updatedPages))
            true
        }
    }

    override suspend fun setWidgetStackActiveIndex(stackId: ItemId, activeIndex: Int) {
        val existing = dao.getWorkspaceItems().firstOrNull { it.id == stackId.value && it.type == "WIDGET_STACK" } ?: return
        dao.updateWorkspaceItem(existing.copy(rank = activeIndex.coerceAtLeast(0), updatedAt = System.currentTimeMillis()))
    }

    override suspend fun pinShortcut(
        shortcutId: String,
        packageName: String,
        userSerial: Long,
        label: String,
        gridSpec: GridSpec,
    ): Boolean {
        return database.withTransaction {
            val current = loadCurrentSnapshot(gridSpec)
            for (page in current.pages) {
                val free = GridOccupancyEngine.findFirstFreeSpan(page.items, gridSpec.columns, gridSpec.rows, 1, 1)
                if (free != null) {
                    val item = WorkspaceItem.PinnedShortcut(
                        id = nextItemId(),
                        placement = LeafPlacement.Desktop(page.id, free.first, free.second),
                        shortcutId = shortcutId,
                        packageName = packageName,
                        userSerial = userSerial,
                        label = label,
                    )
                    val updatedPages = current.pages.map {
                        if (it.id == page.id) it.copy(items = it.items + item) else it
                    }
                    persistSnapshotInternal(current.copy(pages = updatedPages))
                    return@withTransaction true
                }
            }
            if (current.pages.size >= GridOccupancyEngine.MAX_PAGES) return@withTransaction false
            val newPageId = PageId(nextItemId().value)
            val item = WorkspaceItem.PinnedShortcut(
                id = nextItemId(),
                placement = LeafPlacement.Desktop(newPageId, 0, 0),
                shortcutId = shortcutId,
                packageName = packageName,
                userSerial = userSerial,
                label = label,
            )
            val newPage = WorkspacePage(newPageId, current.pages.size, listOf(item))
            persistSnapshotInternal(current.copy(pages = current.pages + newPage))
            true
        }
    }

    override suspend fun reflowToGrid(currentSpec: GridSpec, targetSpec: GridSpec) {
        if (currentSpec == targetSpec) return
        database.withTransaction {
            val current = loadCurrentSnapshot(currentSpec)
            saveSnapshotInternal(current, reason = "Grid reflow ${currentSpec.columns}x${currentSpec.rows} to ${targetSpec.columns}x${targetSpec.rows}")
            val reflowed = GridOccupancyEngine.reflowWorkspace(
                snapshot = current,
                targetSpec = targetSpec,
                nextPageIdGenerator = { PageId(nextItemId().value) },
            )
            persistSnapshotInternal(reflowed)
        }
    }

    override suspend fun restoreLatestSnapshot(): Boolean {
        val latest = dao.getLatestSnapshot() ?: return false
        return restoreFromJsonBlob(latest.jsonBlob)
    }

    override suspend fun restoreSnapshotById(snapshotId: Long): Boolean {
        val target = dao.getSnapshotById(snapshotId) ?: return false
        return restoreFromJsonBlob(target.jsonBlob)
    }

    override fun acquireDragLease(
        draggedKey: ComponentKey?,
        onDraggedSourceRemoved: () -> Unit,
    ): AutoCloseable {
        val leaseId = syntheticIdCounter.incrementAndGet()
        repoScope.launch {
            dragMutex.withLock {
                activeDragLease = ActiveDragLease(leaseId, draggedKey, onDraggedSourceRemoved)
            }
        }
        return AutoCloseable {
            repoScope.launch {
                val pending = dragMutex.withLock {
                    if (activeDragLease?.id == leaseId) {
                        activeDragLease = null
                    }
                    val p = pendingReconciliation
                    pendingReconciliation = null
                    p
                }
                if (pending != null) {
                    executeReconciliationNow(pending.presentKeys, pending.uninstalledPackages, pending.nowEpochMs)
                }
            }
        }
    }

    override suspend fun reconcileInstalledPackages(
        presentKeys: Set<ComponentKey>,
        uninstalledPackages: Set<String>,
        nowEpochMs: Long,
    ): List<AppWidgetId> {
        val shouldDefer = dragMutex.withLock {
            val lease = activeDragLease
            if (lease != null) {
                val draggedPkg = lease.draggedKey?.packageName
                if ((draggedPkg != null && draggedPkg in uninstalledPackages) ||
                    (lease.draggedKey != null && lease.draggedKey !in presentKeys)
                ) {
                    lease.onDraggedSourceRemoved()
                    activeDragLease = null
                    false
                } else {
                    pendingReconciliation = PendingReconciliation(
                        presentKeys = presentKeys,
                        uninstalledPackages = (pendingReconciliation?.uninstalledPackages ?: emptySet()) + uninstalledPackages,
                        nowEpochMs = nowEpochMs,
                    )
                    true
                }
            } else {
                false
            }
        }

        if (shouldDefer) return emptyList()
        return executeReconciliationNow(presentKeys, uninstalledPackages, nowEpochMs)
    }

    private suspend fun executeReconciliationNow(
        presentKeys: Set<ComponentKey>,
        uninstalledPackages: Set<String>,
        nowEpochMs: Long,
    ): List<AppWidgetId> {
        return database.withTransaction {
            val current = loadCurrentSnapshot(GridSpec(4, 6, 4))
            val presentPackages = presentKeys.map { it.packageName }.toSet()
            val orphanedWidgetIds = mutableListOf<AppWidgetId>()

            fun reconcileApp(app: WorkspaceItem.App): WorkspaceItem.App? {
                if (app.component.packageName in uninstalledPackages) return null
                if (app.component in presentKeys) {
                    return if (app.orphanSinceEpochMs != null) app.copy(orphanSinceEpochMs = null) else app
                }
                val since = app.orphanSinceEpochMs ?: nowEpochMs
                return if (nowEpochMs - since > GridOccupancyEngine.ORPHAN_RETENTION_MS) {
                    null
                } else {
                    app.copy(orphanSinceEpochMs = since)
                }
            }

            fun reconcileItem(item: WorkspaceItem): WorkspaceItem? = when (item) {
                is WorkspaceItem.App -> reconcileApp(item)
                is WorkspaceItem.PinnedShortcut -> {
                    if (item.packageName in uninstalledPackages) null else item
                }
                is WorkspaceItem.Folder -> {
                    item.copy(children = item.children.mapNotNull { reconcileApp(it) })
                }
                is WorkspaceItem.Widget -> {
                    if (item.provider.packageName in uninstalledPackages) {
                        orphanedWidgetIds.add(item.appWidgetId)
                        item.copy(status = WidgetStatus.PROVIDER_MISSING)
                    } else if (item.provider.packageName in presentPackages && item.status == WidgetStatus.PROVIDER_MISSING) {
                        item.copy(status = WidgetStatus.ACTIVE)
                    } else {
                        item
                    }
                }
                is WorkspaceItem.WidgetStack -> {
                    item.copy(
                        widgets = item.widgets.map { w ->
                            if (w.provider.packageName in uninstalledPackages) {
                                orphanedWidgetIds.add(w.appWidgetId)
                                w.copy(status = WidgetStatus.PROVIDER_MISSING)
                            } else {
                                w
                            }
                        },
                    )
                }
            }

            val reconciled = WorkspaceSnapshot(
                pages = current.pages.map { p -> p.copy(items = p.items.mapNotNull { reconcileItem(it) }) },
                dockItems = current.dockItems.mapNotNull { reconcileItem(it) },
                gridSpec = current.gridSpec,
            )
            persistSnapshotInternal(GridOccupancyEngine.normalizeSnapshot(reconciled))
            orphanedWidgetIds
        }
    }

    private suspend fun restoreFromJsonBlob(jsonBlob: String): Boolean {
        val parsed = BackupPayloadCodec.decodeAndValidate(jsonBlob).getOrNull() ?: return false
        database.withTransaction {
            val restored = WorkspaceSnapshot(
                pages = parsed.pages.map { WorkspacePage(PageId(it.id), it.position, it.items) },
                dockItems = parsed.dockItems,
                gridSpec = GridSpec(parsed.gridColumns, parsed.gridRows, parsed.dockSlots),
            )
            persistSnapshotInternal(GridOccupancyEngine.normalizeSnapshot(restored))
        }
        return true
    }

    internal suspend fun saveSnapshotInternal(snapshot: WorkspaceSnapshot, reason: String) {
        val now = System.currentTimeMillis()
        val payload = AuroraBackupPayload(
            exportedAtEpochMs = now,
            gridColumns = snapshot.gridSpec.columns,
            gridRows = snapshot.gridSpec.rows,
            dockSlots = snapshot.gridSpec.dockSlots,
            pages = snapshot.pages.map { BackupPageDto(it.id.value, it.position, it.items) },
            dockItems = snapshot.dockItems,
            settings = null,
        )
        dao.insertSnapshot(
            LayoutSnapshotEntity(
                schemaVersion = AuroraBackupPayload.CURRENT_SCHEMA_VERSION,
                reason = reason,
                createdAt = now,
                jsonBlob = BackupPayloadCodec.encode(payload),
            ),
        )
        dao.trimSnapshotsTo5()
    }

    internal suspend fun loadCurrentSnapshot(gridSpec: GridSpec): WorkspaceSnapshot {
        return assembleSnapshot(
            pages = dao.getPages(),
            items = dao.getWorkspaceItems(),
            widgets = dao.getWidgetInstances(),
            shortcuts = dao.getShortcutPins(),
            gridSpec = gridSpec,
        )
    }

    internal suspend fun persistSnapshotInternal(snapshot: WorkspaceSnapshot) {
        val now = System.currentTimeMillis()
        dao.deleteAllWidgets()
        dao.deleteAllShortcuts()
        dao.deleteAllWorkspaceItems()
        dao.deleteAllPages()

        val pageIdMap = mutableMapOf<PageId, Long>()
        snapshot.pages.sortedBy { it.position }.forEachIndexed { idx, page ->
            val insertedPageId = dao.insertPage(
                WorkspacePageEntity(
                    id = if (page.id.value > 0L) page.id.value else 0L,
                    position = idx,
                    createdAt = now,
                ),
            )
            pageIdMap[page.id] = insertedPageId
        }

        for (page in snapshot.pages) {
            val dbPageId = pageIdMap[page.id] ?: continue
            page.items.forEachIndexed { rank, item ->
                insertDomainItem(item, container = "DESKTOP", containerId = dbPageId, fallbackRank = rank, now = now)
            }
        }

        snapshot.dockItems.forEachIndexed { rank, item ->
            insertDomainItem(item, container = "DOCK", containerId = null, fallbackRank = rank, now = now)
        }
    }

    private suspend fun insertDomainItem(
        item: WorkspaceItem,
        container: String,
        containerId: Long?,
        fallbackRank: Int,
        now: Long,
    ) {
        when (item) {
            is WorkspaceItem.App -> {
                val (cx, cy, r) = when (val p = item.placement) {
                    is LeafPlacement.Desktop -> Triple(p.cellX, p.cellY, fallbackRank)
                    is LeafPlacement.Dock -> Triple(0, 0, p.rank)
                    is LeafPlacement.InFolder -> Triple(0, 0, p.rank)
                }
                dao.insertWorkspaceItem(
                    WorkspaceItemEntity(
                        id = if (item.id.value > 0L) item.id.value else 0L,
                        type = "APP",
                        container = container,
                        containerId = containerId,
                        cellX = cx,
                        cellY = cy,
                        rank = r,
                        packageName = item.component.packageName,
                        activityClass = item.component.activityClass,
                        userSerial = item.component.userSerial,
                        orphanSince = item.orphanSinceEpochMs,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

            is WorkspaceItem.PinnedShortcut -> {
                val (cx, cy, r) = when (val p = item.placement) {
                    is LeafPlacement.Desktop -> Triple(p.cellX, p.cellY, fallbackRank)
                    is LeafPlacement.Dock -> Triple(0, 0, p.rank)
                    is LeafPlacement.InFolder -> Triple(0, 0, p.rank)
                }
                val rowId = dao.insertWorkspaceItem(
                    WorkspaceItemEntity(
                        id = if (item.id.value > 0L) item.id.value else 0L,
                        type = "SHORTCUT",
                        container = container,
                        containerId = containerId,
                        cellX = cx,
                        cellY = cy,
                        rank = r,
                        title = item.label,
                        packageName = item.packageName,
                        userSerial = item.userSerial,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                dao.upsertShortcutPin(
                    ShortcutPinEntity(
                        itemId = rowId,
                        shortcutId = item.shortcutId,
                        packageName = item.packageName,
                        userSerial = item.userSerial,
                        label = item.label,
                    ),
                )
            }

            is WorkspaceItem.Folder -> {
                val (cx, cy, r) = when (val p = item.placement) {
                    is ContainerPlacement.Desktop -> Triple(p.cellX, p.cellY, fallbackRank)
                    is ContainerPlacement.Dock -> Triple(0, 0, p.rank)
                }
                val folderRowId = dao.insertWorkspaceItem(
                    WorkspaceItemEntity(
                        id = if (item.id.value > 0L) item.id.value else 0L,
                        type = "FOLDER",
                        container = container,
                        containerId = containerId,
                        cellX = cx,
                        cellY = cy,
                        rank = r,
                        title = item.title,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                item.children.forEachIndexed { childIdx, child ->
                    dao.insertWorkspaceItem(
                        WorkspaceItemEntity(
                            id = if (child.id.value > 0L) child.id.value else 0L,
                            type = "APP",
                            container = "FOLDER",
                            containerId = folderRowId,
                            rank = childIdx,
                            packageName = child.component.packageName,
                            activityClass = child.component.activityClass,
                            userSerial = child.component.userSerial,
                            orphanSince = child.orphanSinceEpochMs,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
            }

            is WorkspaceItem.Widget -> {
                val rowId = dao.insertWorkspaceItem(
                    WorkspaceItemEntity(
                        id = if (item.id.value > 0L) item.id.value else 0L,
                        type = "WIDGET",
                        container = container,
                        containerId = containerId,
                        cellX = item.cellX,
                        cellY = item.cellY,
                        spanX = item.spanX,
                        spanY = item.spanY,
                        rank = fallbackRank,
                        title = item.label,
                        packageName = item.provider.packageName,
                        activityClass = item.provider.activityClass,
                        userSerial = item.provider.userSerial,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                dao.upsertWidgetInstance(
                    WidgetInstanceEntity(
                        appWidgetId = item.appWidgetId.value,
                        itemId = rowId,
                        providerPackage = item.provider.packageName,
                        providerClass = item.provider.activityClass,
                        userSerial = item.provider.userSerial,
                        label = item.label,
                        minSpanX = item.minSpanX,
                        minSpanY = item.minSpanY,
                        maxSpanX = item.maxSpanX,
                        maxSpanY = item.maxSpanY,
                        needsConfig = item.status == WidgetStatus.NEEDS_CONFIG,
                        status = item.status.name,
                    ),
                )
            }

            is WorkspaceItem.WidgetStack -> {
                val stackRowId = dao.insertWorkspaceItem(
                    WorkspaceItemEntity(
                        id = if (item.id.value > 0L) item.id.value else 0L,
                        type = "WIDGET_STACK",
                        container = container,
                        containerId = containerId,
                        cellX = item.cellX,
                        cellY = item.cellY,
                        spanX = item.spanX,
                        spanY = item.spanY,
                        rank = item.activeIndex,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                item.widgets.forEachIndexed { idx, w ->
                    insertDomainItem(
                        item = w.copy(spanX = item.spanX, spanY = item.spanY),
                        container = "STACK",
                        containerId = stackRowId,
                        fallbackRank = idx,
                        now = now,
                    )
                }
            }
        }
    }

    private fun applyDropToSnapshot(
        snapshot: WorkspaceSnapshot,
        resolution: DropResolution,
    ): WorkspaceSnapshot {
        val extracted = extractItemFromSnapshot(snapshot, resolutionDraggedId(resolution)) ?: return snapshot
        val (draggedItem, withoutDragged) = extracted

        return when (resolution) {
            is DropResolution.MoveToDesktop -> {
                val moved = when (draggedItem) {
                    is WorkspaceItem.App -> draggedItem.copy(
                        placement = LeafPlacement.Desktop(resolution.pageId, resolution.cellX, resolution.cellY),
                    )
                    is WorkspaceItem.PinnedShortcut -> draggedItem.copy(
                        placement = LeafPlacement.Desktop(resolution.pageId, resolution.cellX, resolution.cellY),
                    )
                    is WorkspaceItem.Folder -> draggedItem.copy(
                        placement = ContainerPlacement.Desktop(resolution.pageId, resolution.cellX, resolution.cellY),
                    )
                    is WorkspaceItem.Widget -> draggedItem.copy(
                        pageId = resolution.pageId,
                        cellX = resolution.cellX,
                        cellY = resolution.cellY,
                    )
                    is WorkspaceItem.WidgetStack -> draggedItem.copy(
                        pageId = resolution.pageId,
                        cellX = resolution.cellX,
                        cellY = resolution.cellY,
                    )
                }
                withoutDragged.copy(
                    pages = withoutDragged.pages.map { p ->
                        if (p.id == resolution.pageId) p.copy(items = p.items + moved) else p
                    },
                )
            }

            is DropResolution.MoveToDock -> {
                val dockList = withoutDragged.dockItems.toMutableList()
                val targetIndex = resolution.rank.coerceIn(0, dockList.size)
                val moved = when (draggedItem) {
                    is WorkspaceItem.App -> draggedItem.copy(placement = LeafPlacement.Dock(targetIndex))
                    is WorkspaceItem.PinnedShortcut -> draggedItem.copy(placement = LeafPlacement.Dock(targetIndex))
                    is WorkspaceItem.Folder -> draggedItem.copy(placement = ContainerPlacement.Dock(targetIndex))
                    else -> return snapshot
                }
                dockList.add(targetIndex, moved)
                withoutDragged.copy(dockItems = dockList)
            }

            is DropResolution.CreateFolder -> {
                if (draggedItem !is WorkspaceItem.App) return snapshot
                val newFolderId = nextItemId()
                fun replaceTarget(list: List<WorkspaceItem>): List<WorkspaceItem> = list.map { item ->
                    if (item.id == resolution.targetAppId && item is WorkspaceItem.App) {
                        WorkspaceItem.Folder(
                            id = newFolderId,
                            placement = resolution.placement,
                            title = resolution.defaultTitle,
                            children = listOf(
                                item.copy(placement = LeafPlacement.InFolder(newFolderId, 0)),
                                draggedItem.copy(placement = LeafPlacement.InFolder(newFolderId, 1)),
                            ),
                        )
                    } else {
                        item
                    }
                }
                withoutDragged.copy(
                    pages = withoutDragged.pages.map { it.copy(items = replaceTarget(it.items)) },
                    dockItems = replaceTarget(withoutDragged.dockItems),
                )
            }

            is DropResolution.AddToFolder -> {
                if (draggedItem !is WorkspaceItem.App) return snapshot
                fun appendToFolder(list: List<WorkspaceItem>): List<WorkspaceItem> = list.map { item ->
                    if (item.id == resolution.folderId && item is WorkspaceItem.Folder) {
                        val appended = draggedItem.copy(
                            placement = LeafPlacement.InFolder(item.id, item.children.size),
                        )
                        item.copy(children = item.children + appended)
                    } else {
                        item
                    }
                }
                withoutDragged.copy(
                    pages = withoutDragged.pages.map { it.copy(items = appendToFolder(it.items)) },
                    dockItems = appendToFolder(withoutDragged.dockItems),
                )
            }

            is DropResolution.MergeIntoWidgetStack -> {
                if (draggedItem !is WorkspaceItem.Widget) return snapshot
                val stackId = nextItemId()
                withoutDragged.copy(
                    pages = withoutDragged.pages.map { page ->
                        page.copy(
                            items = page.items.map { item ->
                                when {
                                    item.id == resolution.targetItemId && item is WorkspaceItem.Widget -> {
                                        WorkspaceItem.WidgetStack(
                                            id = stackId,
                                            pageId = item.pageId,
                                            cellX = item.cellX,
                                            cellY = item.cellY,
                                            spanX = item.spanX,
                                            spanY = item.spanY,
                                            activeIndex = 1,
                                            widgets = listOf(item, draggedItem),
                                        )
                                    }
                                    item.id == resolution.targetItemId && item is WorkspaceItem.WidgetStack -> {
                                        item.copy(
                                            activeIndex = item.widgets.size,
                                            widgets = item.widgets + draggedItem,
                                        )
                                    }
                                    else -> item
                                }
                            },
                        )
                    },
                )
            }

            is DropResolution.Reject -> snapshot
        }
    }

    private fun extractItemFromSnapshot(
        snapshot: WorkspaceSnapshot,
        itemId: ItemId,
    ): Pair<WorkspaceItem, WorkspaceSnapshot>? {
        var found: WorkspaceItem? = null

        fun stripFrom(items: List<WorkspaceItem>): List<WorkspaceItem> {
            return items.mapNotNull { item ->
                when {
                    item.id == itemId -> {
                        found = item
                        null
                    }
                    item is WorkspaceItem.Folder -> {
                        val hit = item.children.firstOrNull { it.id == itemId }
                        if (hit != null) {
                            found = hit
                            item.copy(children = item.children.filter { it.id != itemId })
                        } else {
                            item
                        }
                    }
                    else -> item
                }
            }
        }

        val nextPages = snapshot.pages.map { it.copy(items = stripFrom(it.items)) }
        val nextDock = stripFrom(snapshot.dockItems)
        val target = found ?: return null
        return target to snapshot.copy(pages = nextPages, dockItems = nextDock)
    }

    private fun resolutionDraggedId(resolution: DropResolution): ItemId = when (resolution) {
        is DropResolution.MoveToDesktop -> resolution.itemId
        is DropResolution.MoveToDock -> resolution.itemId
        is DropResolution.CreateFolder -> resolution.draggedAppId
        is DropResolution.AddToFolder -> resolution.draggedAppId
        is DropResolution.MergeIntoWidgetStack -> resolution.draggedWidgetId
        is DropResolution.Reject -> ItemId(-1L)
    }

    private fun nextItemId(): ItemId = ItemId(syntheticIdCounter.incrementAndGet())

    companion object {
        internal fun assembleSnapshot(
            pages: List<WorkspacePageEntity>,
            items: List<WorkspaceItemEntity>,
            widgets: List<WidgetInstanceEntity>,
            shortcuts: List<ShortcutPinEntity>,
            gridSpec: GridSpec,
        ): WorkspaceSnapshot {
            val widgetByItemId = widgets.associateBy { it.itemId }
            val shortcutByItemId = shortcuts.associateBy { it.itemId }
            val folderChildrenByFolderId = items
                .filter { it.container == "FOLDER" && it.type == "APP" && it.containerId != null }
                .groupBy { it.containerId!! }
            val stackChildrenByStackId = items
                .filter { it.container == "STACK" && it.type == "WIDGET" && it.containerId != null }
                .groupBy { it.containerId!! }

            fun toDomainItem(entity: WorkspaceItemEntity, pageId: PageId?): WorkspaceItem? {
                return when (entity.type) {
                    "APP" -> {
                        val pkg = entity.packageName ?: return null
                        val cls = entity.activityClass ?: return null
                        val placement = if (entity.container == "DOCK") {
                            LeafPlacement.Dock(entity.rank)
                        } else {
                            LeafPlacement.Desktop(pageId ?: return null, entity.cellX, entity.cellY)
                        }
                        WorkspaceItem.App(
                            id = ItemId(entity.id),
                            placement = placement,
                            component = ComponentKey(pkg, cls, entity.userSerial),
                            orphanSinceEpochMs = entity.orphanSince,
                        )
                    }

                    "SHORTCUT" -> {
                        val pin = shortcutByItemId[entity.id]
                        val pkg = pin?.packageName ?: entity.packageName ?: return null
                        val placement = if (entity.container == "DOCK") {
                            LeafPlacement.Dock(entity.rank)
                        } else {
                            LeafPlacement.Desktop(pageId ?: return null, entity.cellX, entity.cellY)
                        }
                        WorkspaceItem.PinnedShortcut(
                            id = ItemId(entity.id),
                            placement = placement,
                            shortcutId = pin?.shortcutId ?: "",
                            packageName = pkg,
                            userSerial = pin?.userSerial ?: entity.userSerial,
                            label = pin?.label ?: entity.title ?: "Shortcut",
                        )
                    }

                    "FOLDER" -> {
                        val placement = if (entity.container == "DOCK") {
                            ContainerPlacement.Dock(entity.rank)
                        } else {
                            ContainerPlacement.Desktop(pageId ?: return null, entity.cellX, entity.cellY)
                        }
                        val children = folderChildrenByFolderId[entity.id].orEmpty()
                            .sortedBy { it.rank }
                            .mapNotNull { child ->
                                val pkg = child.packageName ?: return@mapNotNull null
                                val cls = child.activityClass ?: return@mapNotNull null
                                WorkspaceItem.App(
                                    id = ItemId(child.id),
                                    placement = LeafPlacement.InFolder(ItemId(entity.id), child.rank),
                                    component = ComponentKey(pkg, cls, child.userSerial),
                                    orphanSinceEpochMs = child.orphanSince,
                                )
                            }
                        WorkspaceItem.Folder(
                            id = ItemId(entity.id),
                            placement = placement,
                            title = entity.title ?: "Folder",
                            children = children,
                        )
                    }

                    "WIDGET" -> {
                        val pId = pageId ?: return null
                        val wInst = widgetByItemId[entity.id] ?: return null
                        val status = runCatching { WidgetStatus.valueOf(wInst.status) }
                            .getOrDefault(WidgetStatus.ACTIVE)
                        WorkspaceItem.Widget(
                            id = ItemId(entity.id),
                            pageId = pId,
                            cellX = entity.cellX,
                            cellY = entity.cellY,
                            spanX = entity.spanX,
                            spanY = entity.spanY,
                            appWidgetId = AppWidgetId(wInst.appWidgetId),
                            provider = ComponentKey(wInst.providerPackage, wInst.providerClass, wInst.userSerial),
                            label = wInst.label,
                            minSpanX = wInst.minSpanX,
                            minSpanY = wInst.minSpanY,
                            maxSpanX = wInst.maxSpanX,
                            maxSpanY = wInst.maxSpanY,
                            status = status,
                        )
                    }

                    "WIDGET_STACK" -> {
                        val pId = pageId ?: return null
                        val childWidgets = stackChildrenByStackId[entity.id].orEmpty()
                            .sortedBy { it.rank }
                            .mapNotNull { child -> toDomainItem(child, pId) as? WorkspaceItem.Widget }
                        WorkspaceItem.WidgetStack(
                            id = ItemId(entity.id),
                            pageId = pId,
                            cellX = entity.cellX,
                            cellY = entity.cellY,
                            spanX = entity.spanX,
                            spanY = entity.spanY,
                            activeIndex = entity.rank.coerceIn(0, (childWidgets.size - 1).coerceAtLeast(0)),
                            widgets = childWidgets,
                        )
                    }

                    else -> null
                }
            }

            val desktopByPage = items.filter { it.container == "DESKTOP" && it.containerId != null }
                .groupBy { it.containerId!! }

            val builtPages = if (pages.isEmpty()) {
                listOf(WorkspacePage(PageId(1L), 0, emptyList()))
            } else {
                pages.sortedBy { it.position }.map { pageEntity ->
                    val pageId = PageId(pageEntity.id)
                    val pageItems = desktopByPage[pageEntity.id].orEmpty()
                        .mapNotNull { toDomainItem(it, pageId) }
                    WorkspacePage(
                        id = pageId,
                        position = pageEntity.position,
                        items = pageItems,
                    )
                }
            }

            val dockDomain = items.filter { it.container == "DOCK" }
                .sortedBy { it.rank }
                .mapNotNull { toDomainItem(it, null) }

            return WorkspaceSnapshot(
                pages = builtPages,
                dockItems = dockDomain,
                gridSpec = gridSpec,
            )
        }
    }
}
