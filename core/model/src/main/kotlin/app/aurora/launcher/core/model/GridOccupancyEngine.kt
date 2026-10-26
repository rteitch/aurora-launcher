package app.aurora.launcher.core.model

sealed interface DropTarget {
    data class DesktopCell(
        val pageId: PageId,
        val cellX: Int,
        val cellY: Int,
    ) : DropTarget

    data class DockSlot(
        val rank: Int,
    ) : DropTarget

    data class HoverItem(
        val targetItemId: ItemId,
    ) : DropTarget
}

sealed interface DropResolution {
    data class MoveToDesktop(
        val itemId: ItemId,
        val pageId: PageId,
        val cellX: Int,
        val cellY: Int,
    ) : DropResolution

    data class MoveToDock(
        val itemId: ItemId,
        val rank: Int,
    ) : DropResolution

    data class CreateFolder(
        val draggedAppId: ItemId,
        val targetAppId: ItemId,
        val placement: ContainerPlacement,
        val defaultTitle: String,
    ) : DropResolution

    data class AddToFolder(
        val draggedAppId: ItemId,
        val folderId: ItemId,
    ) : DropResolution

    data class MergeIntoWidgetStack(
        val draggedWidgetId: ItemId,
        val targetItemId: ItemId,
    ) : DropResolution

    data class SwapItems(
        val movedItemId: ItemId,
        val movedPageId: PageId,
        val movedCellX: Int,
        val movedCellY: Int,
        val displacedItemId: ItemId,
        val displacedPageId: PageId,
        val displacedCellX: Int,
        val displacedCellY: Int,
    ) : DropResolution

    data class Reject(
        val reason: String,
    ) : DropResolution
}

object GridOccupancyEngine {
    const val MAX_PAGES: Int = 15
    const val ORPHAN_RETENTION_MS: Long = 7L * 24 * 60 * 60 * 1000

    fun buildOccupancyGrid(
        items: List<WorkspaceItem>,
        columns: Int,
        rows: Int,
        ignoreItemId: ItemId? = null,
    ): Array<Array<ItemId?>> {
        val grid = Array(rows) { arrayOfNulls<ItemId>(columns) }
        for (item in items) {
            if (item.id == ignoreItemId) continue
            val (cellX, cellY, spanX, spanY) = itemDesktopBounds(item) ?: continue
            for (dy in 0 until spanY) {
                for (dx in 0 until spanX) {
                    val x = cellX + dx
                    val y = cellY + dy
                    if (x in 0 until columns && y in 0 until rows) {
                        grid[y][x] = item.id
                    }
                }
            }
        }
        return grid
    }

    fun canFit(
        items: List<WorkspaceItem>,
        columns: Int,
        rows: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
        ignoreItemId: ItemId? = null,
    ): Boolean {
        if (cellX < 0 || cellY < 0 || spanX <= 0 || spanY <= 0) return false
        if (cellX + spanX > columns || cellY + spanY > rows) return false
        val grid = buildOccupancyGrid(items, columns, rows, ignoreItemId)
        for (dy in 0 until spanY) {
            for (dx in 0 until spanX) {
                if (grid[cellY + dy][cellX + dx] != null) return false
            }
        }
        return true
    }

    fun findFirstFreeSpan(
        items: List<WorkspaceItem>,
        columns: Int,
        rows: Int,
        spanX: Int,
        spanY: Int,
        ignoreItemId: ItemId? = null,
    ): Pair<Int, Int>? {
        if (spanX > columns || spanY > rows) return null
        val grid = buildOccupancyGrid(items, columns, rows, ignoreItemId)
        for (y in 0..(rows - spanY)) {
            for (x in 0..(columns - spanX)) {
                var free = true
                for (dy in 0 until spanY) {
                    for (dx in 0 until spanX) {
                        if (grid[y + dy][x + dx] != null) {
                            free = false
                            break
                        }
                    }
                    if (!free) break
                }
                if (free) return x to y
            }
        }
        return null
    }

    fun resolveDrop(
        snapshot: WorkspaceSnapshot,
        draggedItemId: ItemId,
        target: DropTarget,
        defaultFolderTitle: String = "Folder",
    ): DropResolution {
        val allItems = snapshot.pages.flatMap { it.items } +
            snapshot.dockItems +
            snapshot.pages.flatMap { it.items }.filterIsInstance<WorkspaceItem.Folder>().flatMap { it.children } +
            snapshot.dockItems.filterIsInstance<WorkspaceItem.Folder>().flatMap { it.children }
        val dragged = allItems.firstOrNull { it.id == draggedItemId }
            ?: return DropResolution.Reject("Dragged item no longer exists")

        return when (target) {
            is DropTarget.DesktopCell -> {
                val page = snapshot.pages.firstOrNull { it.id == target.pageId }
                    ?: return DropResolution.Reject("Target page missing")
                val (spanX, spanY) = itemSpan(dragged)
                if (canFit(page.items, snapshot.gridSpec.columns, snapshot.gridSpec.rows, target.cellX, target.cellY, spanX, spanY, dragged.id)) {
                    DropResolution.MoveToDesktop(dragged.id, page.id, target.cellX, target.cellY)
                } else if (spanX == 1 && spanY == 1) {
                    val colliding = page.items.firstOrNull { other ->
                        if (other.id == dragged.id) return@firstOrNull false
                        val bounds = itemDesktopBounds(other) ?: return@firstOrNull false
                        val (sx, sy) = itemSpan(other)
                        target.cellX in bounds.first until (bounds.first + sx) &&
                            target.cellY in bounds.second until (bounds.second + sy)
                    }
                    if (colliding != null && itemSpan(colliding) == (1 to 1)) {
                        val draggedOriginal = itemDesktopBounds(dragged)
                        val draggedPageId: PageId? = when (val p = (dragged as? WorkspaceItem.App)?.placement ?: (dragged as? WorkspaceItem.PinnedShortcut)?.placement) {
                            is LeafPlacement.Desktop -> p.pageId
                            else -> when (val cp = (dragged as? WorkspaceItem.Folder)?.placement) {
                                is ContainerPlacement.Desktop -> cp.pageId
                                else -> null
                            }
                        }
                        if (draggedOriginal != null && draggedPageId != null) {
                            DropResolution.SwapItems(
                                movedItemId = dragged.id,
                                movedPageId = page.id,
                                movedCellX = target.cellX,
                                movedCellY = target.cellY,
                                displacedItemId = colliding.id,
                                displacedPageId = draggedPageId,
                                displacedCellX = draggedOriginal.first,
                                displacedCellY = draggedOriginal.second,
                            )
                        } else {
                            val freeOnThisPage = findFirstFreeSpan(page.items, snapshot.gridSpec.columns, snapshot.gridSpec.rows, 1, 1)
                            if (freeOnThisPage != null) {
                                DropResolution.SwapItems(
                                    movedItemId = dragged.id,
                                    movedPageId = page.id,
                                    movedCellX = target.cellX,
                                    movedCellY = target.cellY,
                                    displacedItemId = colliding.id,
                                    displacedPageId = page.id,
                                    displacedCellX = freeOnThisPage.first,
                                    displacedCellY = freeOnThisPage.second,
                                )
                            } else {
                                DropResolution.Reject("Target cell range is occupied or out of bounds")
                            }
                        }
                    } else {
                        DropResolution.Reject("Target cell range is occupied or out of bounds")
                    }
                } else {
                    DropResolution.Reject("Target cell range is occupied or out of bounds")
                }
            }

            is DropTarget.DockSlot -> {
                if (dragged is WorkspaceItem.Widget || dragged is WorkspaceItem.WidgetStack) {
                    return DropResolution.Reject("Widgets cannot be placed inside the dock")
                }
                val alreadyInDock = snapshot.dockItems.any { it.id == dragged.id }
                if (!alreadyInDock && snapshot.dockItems.size >= snapshot.gridSpec.dockSlots) {
                    DropResolution.Reject("Dock is at capacity")
                } else {
                    DropResolution.MoveToDock(dragged.id, target.rank.coerceIn(0, snapshot.gridSpec.dockSlots - 1))
                }
            }

            is DropTarget.HoverItem -> {
                if (target.targetItemId == dragged.id) {
                    return DropResolution.Reject("Cannot merge an item onto itself")
                }
                val targetItem = (snapshot.pages.flatMap { it.items } + snapshot.dockItems)
                    .firstOrNull { it.id == target.targetItemId }
                    ?: return DropResolution.Reject("Hover target not found")

                when {
                    dragged is WorkspaceItem.App && targetItem is WorkspaceItem.App -> {
                        val containerPlacement = when (val p = targetItem.placement) {
                            is LeafPlacement.Desktop -> ContainerPlacement.Desktop(p.pageId, p.cellX, p.cellY)
                            is LeafPlacement.Dock -> ContainerPlacement.Dock(p.rank)
                            is LeafPlacement.InFolder -> return DropResolution.Reject("Cannot nest folders")
                        }
                        DropResolution.CreateFolder(
                            draggedAppId = dragged.id,
                            targetAppId = targetItem.id,
                            placement = containerPlacement,
                            defaultTitle = defaultFolderTitle,
                        )
                    }

                    dragged is WorkspaceItem.App && targetItem is WorkspaceItem.Folder -> {
                        DropResolution.AddToFolder(dragged.id, targetItem.id)
                    }

                    dragged is WorkspaceItem.Widget && targetItem is WorkspaceItem.Widget -> {
                        if (dragged.spanX == targetItem.spanX && dragged.spanY == targetItem.spanY) {
                            DropResolution.MergeIntoWidgetStack(dragged.id, targetItem.id)
                        } else {
                            DropResolution.Reject("Widget stack requires matching grid dimensions")
                        }
                    }

                    dragged is WorkspaceItem.Widget && targetItem is WorkspaceItem.WidgetStack -> {
                        if (dragged.spanX == targetItem.spanX && dragged.spanY == targetItem.spanY) {
                            DropResolution.MergeIntoWidgetStack(dragged.id, targetItem.id)
                        } else {
                            DropResolution.Reject("Widget stack requires matching grid dimensions")
                        }
                    }

                    else -> DropResolution.Reject("Unsupported merge combination")
                }
            }
        }
    }

    /**
     * Enforces E17 (1-item folder/stack auto-dissolution) and E19 (empty page pruning + 15-page ceiling).
     */
    fun normalizeSnapshot(snapshot: WorkspaceSnapshot): WorkspaceSnapshot {
        val normalizedDock = snapshot.dockItems
            .mapNotNull { normalizeContainerItem(it) }
            .sortedBy { dockRank(it) }
            .mapIndexed { idx, item -> rebindDockRank(item, idx) }

        val nonEmptyPages = snapshot.pages
            .sortedBy { it.position }
            .map { page ->
                page.copy(items = page.items.mapNotNull { normalizeContainerItem(it) })
            }
            .filter { it.items.isNotEmpty() }
            .take(MAX_PAGES)

        val finalPages = if (nonEmptyPages.isEmpty()) {
            val fallbackId = snapshot.pages.firstOrNull()?.id ?: PageId(1L)
            listOf(WorkspacePage(id = fallbackId, position = 0, items = emptyList()))
        } else {
            nonEmptyPages.mapIndexed { idx, page -> page.copy(position = idx) }
        }

        return snapshot.copy(
            pages = finalPages,
            dockItems = normalizedDock,
        )
    }

    /**
     * Deterministic row-major reflow when changing grid dimensions (E15, E16).
     * Clamps oversized widget spans and spills overflow items onto subsequent pages in stable order.
     */
    fun reflowWorkspace(
        snapshot: WorkspaceSnapshot,
        targetSpec: GridSpec,
        nextPageIdGenerator: (Int) -> PageId,
    ): WorkspaceSnapshot {
        val orderedSourceItems = snapshot.pages
            .sortedBy { it.position }
            .flatMap { page ->
                page.items.sortedWith(
                    compareBy<WorkspaceItem>(
                        { itemDesktopBounds(it)?.second ?: 0 },
                        { itemDesktopBounds(it)?.first ?: 0 },
                        { it.id.value },
                    ),
                )
            }

        val existingPageIds = snapshot.pages.sortedBy { it.position }.map { it.id }
        val builtPages = mutableListOf<MutableList<WorkspaceItem>>()
        val pageIds = mutableListOf<PageId>()

        fun ensurePage(index: Int): Pair<PageId, MutableList<WorkspaceItem>> {
            while (builtPages.size <= index) {
                val id = existingPageIds.getOrElse(builtPages.size) { nextPageIdGenerator(builtPages.size) }
                pageIds.add(id)
                builtPages.add(mutableListOf())
            }
            return pageIds[index] to builtPages[index]
        }

        ensurePage(0)

        for (rawItem in orderedSourceItems) {
            val clamped = clampSpanToGrid(rawItem, targetSpec.columns, targetSpec.rows)
            val (spanX, spanY) = itemSpan(clamped)
            var placed = false
            var pageIdx = 0
            while (!placed && pageIdx < MAX_PAGES) {
                val (pageId, pageItems) = ensurePage(pageIdx)
                val free = findFirstFreeSpan(pageItems, targetSpec.columns, targetSpec.rows, spanX, spanY)
                if (free != null) {
                    pageItems.add(rebindDesktopPosition(clamped, pageId, free.first, free.second))
                    placed = true
                } else {
                    pageIdx++
                }
            }
        }

        // Spill excess dock items onto desktop if dockSlots shrank
        val keptDock = snapshot.dockItems
            .sortedBy { dockRank(it) }
            .take(targetSpec.dockSlots)
            .mapIndexed { idx, item -> rebindDockRank(item, idx) }

        val spilledDock = snapshot.dockItems
            .sortedBy { dockRank(it) }
            .drop(targetSpec.dockSlots)

        for (spilled in spilledDock) {
            var placed = false
            var pageIdx = 0
            while (!placed && pageIdx < MAX_PAGES) {
                val (pageId, pageItems) = ensurePage(pageIdx)
                val free = findFirstFreeSpan(pageItems, targetSpec.columns, targetSpec.rows, 1, 1)
                if (free != null) {
                    pageItems.add(rebindDesktopPosition(spilled, pageId, free.first, free.second))
                    placed = true
                } else {
                    pageIdx++
                }
            }
        }

        val resultPages = builtPages.mapIndexed { idx, items ->
            WorkspacePage(id = pageIds[idx], position = idx, items = items)
        }

        return normalizeSnapshot(
            WorkspaceSnapshot(
                pages = resultPages,
                dockItems = keptDock,
                gridSpec = targetSpec,
            ),
        )
    }

    /**
     * Compacts all desktop items across pages in row-major continuous flow (iOS style).
     * Fills all empty gaps by shifting subsequent items forward and pruning trailing empty pages.
     */
    fun packWorkspace(snapshot: WorkspaceSnapshot): WorkspaceSnapshot {
        val targetSpec = snapshot.gridSpec
        val cols = targetSpec.columns
        val rows = targetSpec.rows

        val allDesktopItems = snapshot.pages
            .sortedBy { it.position }
            .flatMap { page ->
                page.items.sortedWith(
                    compareBy(
                        { itemDesktopBounds(it)?.second ?: 0 },
                        { itemDesktopBounds(it)?.first ?: 0 },
                        { it.id.value },
                    ),
                )
            }

        val existingPageIds = snapshot.pages.sortedBy { it.position }.map { it.id }
        val builtPages = mutableListOf<MutableList<WorkspaceItem>>()
        val pageIds = mutableListOf<PageId>()

        fun ensurePage(index: Int): Pair<PageId, MutableList<WorkspaceItem>> {
            while (builtPages.size <= index) {
                val id = existingPageIds.getOrElse(builtPages.size) { PageId((builtPages.size + 1).toLong()) }
                pageIds.add(id)
                builtPages.add(mutableListOf())
            }
            return pageIds[index] to builtPages[index]
        }

        ensurePage(0)

        for (rawItem in allDesktopItems) {
            val clamped = clampSpanToGrid(rawItem, cols, rows)
            val (spanX, spanY) = itemSpan(clamped)
            var placed = false
            var pageIdx = 0
            while (!placed && pageIdx < MAX_PAGES) {
                val (pageId, pageItems) = ensurePage(pageIdx)
                val free = findFirstFreeSpan(pageItems, cols, rows, spanX, spanY)
                if (free != null) {
                    pageItems.add(rebindDesktopPosition(clamped, pageId, free.first, free.second))
                    placed = true
                } else {
                    pageIdx++
                }
            }
        }

        val resultPages = builtPages.mapIndexed { idx, items ->
            WorkspacePage(id = pageIds[idx], position = idx, items = items)
        }

        return normalizeSnapshot(
            snapshot.copy(
                pages = resultPages,
            ),
        )
    }

    private fun normalizeContainerItem(item: WorkspaceItem): WorkspaceItem? = when (item) {
        is WorkspaceItem.Folder -> when (item.children.size) {
            0 -> null
            1 -> {
                val soleChild = item.children.first()
                val leafPlacement = when (val p = item.placement) {
                    is ContainerPlacement.Desktop -> LeafPlacement.Desktop(p.pageId, p.cellX, p.cellY)
                    is ContainerPlacement.Dock -> LeafPlacement.Dock(p.rank)
                }
                soleChild.copy(placement = leafPlacement)
            }
            else -> item.copy(
                children = item.children.mapIndexed { idx, app ->
                    app.copy(placement = LeafPlacement.InFolder(item.id, idx))
                },
            )
        }

        is WorkspaceItem.WidgetStack -> when (item.widgets.size) {
            0 -> null
            1 -> item.widgets.first().copy(
                pageId = item.pageId,
                cellX = item.cellX,
                cellY = item.cellY,
                spanX = item.spanX,
                spanY = item.spanY,
            )
            else -> item.copy(
                activeIndex = item.activeIndex.coerceIn(0, item.widgets.lastIndex),
            )
        }

        else -> item
    }

    private fun clampSpanToGrid(item: WorkspaceItem, columns: Int, rows: Int): WorkspaceItem = when (item) {
        is WorkspaceItem.Widget -> item.copy(
            spanX = item.spanX.coerceIn(1, columns),
            spanY = item.spanY.coerceIn(1, rows),
        )
        is WorkspaceItem.WidgetStack -> {
            val sx = item.spanX.coerceIn(1, columns)
            val sy = item.spanY.coerceIn(1, rows)
            item.copy(
                spanX = sx,
                spanY = sy,
                widgets = item.widgets.map { it.copy(spanX = sx, spanY = sy) },
            )
        }
        else -> item
    }

    private fun rebindDesktopPosition(
        item: WorkspaceItem,
        pageId: PageId,
        cellX: Int,
        cellY: Int,
    ): WorkspaceItem = when (item) {
        is WorkspaceItem.App -> item.copy(placement = LeafPlacement.Desktop(pageId, cellX, cellY))
        is WorkspaceItem.PinnedShortcut -> item.copy(placement = LeafPlacement.Desktop(pageId, cellX, cellY))
        is WorkspaceItem.Folder -> item.copy(placement = ContainerPlacement.Desktop(pageId, cellX, cellY))
        is WorkspaceItem.Widget -> item.copy(pageId = pageId, cellX = cellX, cellY = cellY)
        is WorkspaceItem.WidgetStack -> item.copy(
            pageId = pageId,
            cellX = cellX,
            cellY = cellY,
            widgets = item.widgets.map { it.copy(pageId = pageId, cellX = cellX, cellY = cellY) },
        )
    }

    private fun rebindDockRank(item: WorkspaceItem, rank: Int): WorkspaceItem = when (item) {
        is WorkspaceItem.App -> item.copy(placement = LeafPlacement.Dock(rank))
        is WorkspaceItem.PinnedShortcut -> item.copy(placement = LeafPlacement.Dock(rank))
        is WorkspaceItem.Folder -> item.copy(placement = ContainerPlacement.Dock(rank))
        is WorkspaceItem.Widget, is WorkspaceItem.WidgetStack -> item
    }

    private fun dockRank(item: WorkspaceItem): Int = when (item) {
        is WorkspaceItem.App -> (item.placement as? LeafPlacement.Dock)?.rank ?: 0
        is WorkspaceItem.PinnedShortcut -> (item.placement as? LeafPlacement.Dock)?.rank ?: 0
        is WorkspaceItem.Folder -> (item.placement as? ContainerPlacement.Dock)?.rank ?: 0
        is WorkspaceItem.Widget, is WorkspaceItem.WidgetStack -> Int.MAX_VALUE
    }

    fun itemSpan(item: WorkspaceItem): Pair<Int, Int> = when (item) {
        is WorkspaceItem.Widget -> item.spanX to item.spanY
        is WorkspaceItem.WidgetStack -> item.spanX to item.spanY
        is WorkspaceItem.App, is WorkspaceItem.PinnedShortcut, is WorkspaceItem.Folder -> 1 to 1
    }

    fun itemDesktopBounds(item: WorkspaceItem): Quadruple? = when (item) {
        is WorkspaceItem.App -> (item.placement as? LeafPlacement.Desktop)?.let { Quadruple(it.cellX, it.cellY, 1, 1) }
        is WorkspaceItem.PinnedShortcut -> (item.placement as? LeafPlacement.Desktop)?.let { Quadruple(it.cellX, it.cellY, 1, 1) }
        is WorkspaceItem.Folder -> (item.placement as? ContainerPlacement.Desktop)?.let { Quadruple(it.cellX, it.cellY, 1, 1) }
        is WorkspaceItem.Widget -> Quadruple(item.cellX, item.cellY, item.spanX, item.spanY)
        is WorkspaceItem.WidgetStack -> Quadruple(item.cellX, item.cellY, item.spanX, item.spanY)
    }

    data class Quadruple(
        val first: Int,
        val second: Int,
        val third: Int,
        val fourth: Int,
    )
}
