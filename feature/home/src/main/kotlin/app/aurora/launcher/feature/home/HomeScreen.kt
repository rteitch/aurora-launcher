package app.aurora.launcher.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aurora.launcher.core.designsystem.AppIconTile
import app.aurora.launcher.core.designsystem.AuroraRadii
import app.aurora.launcher.core.designsystem.AuroraSpacing
import app.aurora.launcher.core.designsystem.AuroraTheme
import app.aurora.launcher.core.designsystem.AuroraTypography
import app.aurora.launcher.core.designsystem.GlassSurface
import app.aurora.launcher.core.designsystem.R
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.ContainerPlacement
import app.aurora.launcher.core.model.DropTarget
import app.aurora.launcher.core.model.GlassTier
import app.aurora.launcher.core.model.GridOccupancyEngine
import app.aurora.launcher.core.model.HomeUiMode
import app.aurora.launcher.core.model.ItemId
import app.aurora.launcher.core.model.LeafPlacement
import app.aurora.launcher.core.model.SwipeUpAction
import app.aurora.launcher.core.model.WorkspaceItem
import app.aurora.launcher.core.platform.rememberAppIcon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    isTabletViewport: Boolean,
    isDefaultHome: Boolean,
    glassTier: GlassTier,
    badgeCounts: Map<String, Int>,
    homeButtonSignal: Int,
    onRequestSetDefaultHome: () -> Unit,
    onLaunchApp: (ComponentKey) -> Unit,
    onOpenFolder: (ItemId) -> Unit,
    onOpenSpotlight: () -> Unit,
    onOpenControlPanel: () -> Unit,
    onOpenWidgetPicker: (app.aurora.launcher.core.model.PageId?) -> Unit,
    onOpenSettings: () -> Unit,
    onShowMessage: (String) -> Unit,
    folderTileSlot: @Composable (
        folder: WorkspaceItem.Folder,
        iconSize: Dp,
        showLabel: Boolean,
        isEditMode: Boolean,
        useWallpaperScrim: Boolean,
        a11yActions: List<CustomAccessibilityAction>,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        modifier: Modifier,
    ) -> Unit,
    widgetSlot: @Composable (
        widget: WorkspaceItem.Widget,
        isEditMode: Boolean,
        isDragging: Boolean,
        onEnterEdit: () -> Unit,
        modifier: Modifier,
    ) -> Unit,
    widgetStackSlot: @Composable (
        stack: WorkspaceItem.WidgetStack,
        isEditMode: Boolean,
        isDragging: Boolean,
        onEnterEdit: () -> Unit,
        modifier: Modifier,
    ) -> Unit,
    appLibrarySlot: @Composable () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    LaunchedEffect(isTabletViewport) {
        viewModel.updateViewportTabletFlag(isTabletViewport)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = AuroraTheme.palette
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val isEditMode = state.mode is HomeUiMode.EditMode
    val activeDrag = (state.mode as? HomeUiMode.EditMode)?.dragSession
    val isDragging = activeDrag != null

    if (isEditMode) {
        BackHandler { viewModel.exitEditMode() }
    }

    val workspacePages = state.workspace.pages
    val showAppLibrary = state.settings.showAppLibraryPage
    val totalPagerPages = workspacePages.size + if (showAppLibrary) 1 else 0
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { totalPagerPages.coerceAtLeast(1) })

    LaunchedEffect(homeButtonSignal) {
        if (homeButtonSignal > 0) {
            if (isEditMode) {
                viewModel.exitEditMode()
            } else if (pagerState.currentPage != 0) {
                pagerState.animateScrollToPage(0)
            }
        }
    }

    var moveDialogTarget by remember { mutableStateOf<WorkspaceItem?>(null) }
    var uninstallConfirmTarget by remember { mutableStateOf<Pair<ComponentKey, String>?>(null) }

    // Pointer drag tracking in window coordinates
    var draggedWindowPos by remember { mutableStateOf(Offset.Zero) }
    var hoveredItemId by remember { mutableStateOf<ItemId?>(null) }
    var gridBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    var dockBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    var edgeScrollJob by remember { mutableStateOf<Job?>(null) }

    val defaultFolderTitle = stringResource(R.string.folder_default_name)
    val orphanNotice = stringResource(R.string.home_orphan_placeholder_notice)
    val addedNotice = stringResource(R.string.home_grid_added_notice)
    val fullNotice = stringResource(R.string.home_grid_full_notice)

    val a11yEdit = stringResource(R.string.a11y_enter_edit_mode)
    val a11yLeft = stringResource(R.string.a11y_move_left)
    val a11yRight = stringResource(R.string.a11y_move_right)
    val a11yUp = stringResource(R.string.a11y_move_up)
    val a11yDown = stringResource(R.string.a11y_move_down)
    val a11yMoveDialog = stringResource(R.string.a11y_move_dialog)
    val a11yRemove = stringResource(R.string.a11y_remove_from_home)

    if (state.isLoading) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
            ) {
                CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(24.dp))
                Text(
                    text = stringResource(R.string.home_loading),
                    style = AuroraTypography.bodyMedium,
                    color = palette.iconLabelText,
                )
            }
        }
        return
    }

    val onAppLibraryPage = showAppLibrary && pagerState.currentPage == workspacePages.size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(
                onAppLibraryPage,
                isEditMode,
                state.settings.swipeDownSpotlightEnabled,
                state.settings.topRightControlPanelEnabled,
            ) {
                if (onAppLibraryPage || isEditMode) return@pointerInput
                var startX = 0f
                var startY = 0f
                var accumDy = 0f
                var triggered = false
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        startY = offset.y
                        accumDy = 0f
                        triggered = false
                    },
                    onVerticalDrag = { _, dragAmount ->
                        if (triggered) return@detectVerticalDragGestures
                        // Ignore top 40dp edge so Android notification shade remains untouched (Appendix A).
                        if (startY < 40.dp.toPx()) return@detectVerticalDragGestures
                        accumDy += dragAmount
                        if (accumDy > 68.dp.toPx()) {
                            triggered = true
                            val isTopRightZone = startX > size.width * 0.68f && startY < size.height * 0.36f
                            if (isTopRightZone && state.settings.topRightControlPanelEnabled) {
                                onOpenControlPanel()
                            } else if (state.settings.swipeDownSpotlightEnabled) {
                                onOpenSpotlight()
                            }
                        }
                    },
                )
            },
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isDragging,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            if (showAppLibrary && pageIndex == workspacePages.size) {
                appLibrarySlot()
            } else {
                val page = workspacePages.getOrNull(pageIndex)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (isEditMode && !isDragging) {
                                    viewModel.exitEditMode()
                                }
                            },
                            onLongClick = {
                                if (!isEditMode) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.enterEditMode()
                                }
                            },
                        )
                        .padding(
                            start = AuroraSpacing.workspaceHorizontalMargin,
                            end = AuroraSpacing.workspaceHorizontalMargin,
                            top = AuroraSpacing.sm,
                            bottom = 118.dp,
                        ),
                ) {
                    if (!isDefaultHome && pageIndex == 0 && !isEditMode) {
                        PreviewModeBanner(
                            onSetDefault = onRequestSetDefaultHome,
                            modifier = Modifier.padding(bottom = AuroraSpacing.sm),
                        )
                    }

                    if (isEditMode) {
                        EditModeTopBar(
                            onAddWidget = { onOpenWidgetPicker(page?.id) },
                            onCycleWallpaper = viewModel::cycleWallpaperStyle,
                            onOpenSettings = {
                                viewModel.exitEditMode()
                                onOpenSettings()
                            },
                            onFinishEdit = viewModel::exitEditMode,
                            modifier = Modifier.padding(bottom = AuroraSpacing.sm),
                        )
                    }

                    if (page != null) {
                        if (page.items.isEmpty() && state.workspace.dockItems.isEmpty() && !isEditMode) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs),
                                    modifier = Modifier
                                        .widthIn(max = 360.dp)
                                        .clip(RoundedCornerShape(AuroraRadii.card))
                                        .background(palette.surface.copy(alpha = 0.92f))
                                        .padding(AuroraSpacing.lg),
                                ) {
                                    Text(
                                        text = stringResource(R.string.home_empty_title),
                                        style = AuroraTypography.titleMedium,
                                        color = palette.onSurface,
                                    )
                                    Text(
                                        text = stringResource(R.string.home_empty_body),
                                        style = AuroraTypography.bodyMedium,
                                        color = palette.onSurfaceSecondary,
                                    )
                                }
                            }
                        } else {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onGloballyPositioned { coords ->
                                        if (pageIndex == pagerState.currentPage) {
                                            gridBoundsInWindow = coords.boundsInWindow()
                                        }
                                    },
                            ) {
                                val cols = state.workspace.gridSpec.columns
                                val rows = state.workspace.gridSpec.rows
                                val cellW = maxWidth / cols
                                val cellH = maxHeight / rows
                                val iconDp = state.settings.iconSizeDp.coerceIn(52, 68).dp

                                if (isEditMode) {
                                    for (ry in 0 until rows) {
                                        for (cx in 0 until cols) {
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = cellW * cx, y = cellH * ry)
                                                    .size(width = cellW, height = cellH)
                                                    .padding(4.dp)
                                                    .border(
                                                        width = 0.75.dp,
                                                        color = palette.glassHairline.copy(alpha = 0.25f),
                                                        shape = RoundedCornerShape(14.dp),
                                                    ),
                                            )
                                        }
                                    }
                                }

                                page.items.forEach { item ->
                                    val bounds = desktopCoords(item) ?: return@forEach
                                    key(item.id.value) {
                                    val (cx, cy, sx, sy) = bounds
                                    val isBeingDragged = activeDrag?.draggedItemId == item.id
                                    val isHoverTarget = hoveredItemId == item.id && !isBeingDragged

                                    val a11yActions = remember(item.id, isEditMode) {
                                        listOf(
                                            CustomAccessibilityAction(a11yLeft) {
                                                viewModel.moveItemByAccessibility(item, -1, 0, defaultFolderTitle)
                                                true
                                            },
                                            CustomAccessibilityAction(a11yRight) {
                                                viewModel.moveItemByAccessibility(item, 1, 0, defaultFolderTitle)
                                                true
                                            },
                                            CustomAccessibilityAction(a11yUp) {
                                                viewModel.moveItemByAccessibility(item, 0, -1, defaultFolderTitle)
                                                true
                                            },
                                            CustomAccessibilityAction(a11yDown) {
                                                viewModel.moveItemByAccessibility(item, 0, 1, defaultFolderTitle)
                                                true
                                            },
                                            CustomAccessibilityAction(a11yMoveDialog) {
                                                moveDialogTarget = item
                                                true
                                            },
                                            CustomAccessibilityAction(a11yRemove) {
                                                viewModel.removeItemFromHome(item.id)
                                                true
                                            },
                                            CustomAccessibilityAction(a11yEdit) {
                                                viewModel.enterEditMode()
                                                true
                                            },
                                        )
                                    }

                                    var itemCenterInWindow by remember { mutableStateOf(Offset.Zero) }
                                    val dragModifier = if (isEditMode) {
                                        Modifier.pointerInput(item.id, page.id, cols, rows) {
                                            detectDragGestures(
                                                onDragStart = { startOffset ->
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    draggedWindowPos = itemCenterInWindow + startOffset
                                                    viewModel.beginDrag(item, pageIndex)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    draggedWindowPos += dragAmount
                                                    val gRect = gridBoundsInWindow
                                                    if (gRect != null && gRect.contains(draggedWindowPos)) {
                                                        val relX = (draggedWindowPos.x - gRect.left).coerceAtLeast(0f)
                                                        val relY = (draggedWindowPos.y - gRect.top).coerceAtLeast(0f)
                                                        val cellPxW = gRect.width / cols
                                                        val cellPxH = gRect.height / rows
                                                        val tx = (relX / cellPxW).toInt().coerceIn(0, cols - 1)
                                                        val ty = (relY / cellPxH).toInt().coerceIn(0, rows - 1)
                                                        val curPage = workspacePages.getOrNull(pagerState.currentPage)
                                                        val occ = curPage?.let {
                                                            GridOccupancyEngine.buildOccupancyGrid(it.items, cols, rows, item.id)[ty][tx]
                                                        }
                                                        val centerDistX = abs((relX % cellPxW) - cellPxW / 2f) / cellPxW
                                                        val centerDistY = abs((relY % cellPxH) - cellPxH / 2f) / cellPxH
                                                        hoveredItemId = if (occ != null && centerDistX < 0.32f && centerDistY < 0.32f) occ else null

                                                        // 600ms dwell near horizontal screen edges switches workspace page (Appendix A).
                                                        val edgePx = with(density) { 28.dp.toPx() }
                                                        if (draggedWindowPos.x < gRect.left + edgePx && pagerState.currentPage > 0) {
                                                            if (edgeScrollJob?.isActive != true) {
                                                                edgeScrollJob = scope.launch {
                                                                    delay(600L)
                                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                                }
                                                            }
                                                        } else if (draggedWindowPos.x > gRect.right - edgePx && pagerState.currentPage < workspacePages.lastIndex) {
                                                            if (edgeScrollJob?.isActive != true) {
                                                                edgeScrollJob = scope.launch {
                                                                    delay(600L)
                                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                                }
                                                            }
                                                        } else {
                                                            edgeScrollJob?.cancel()
                                                        }
                                                    } else {
                                                        hoveredItemId = null
                                                        edgeScrollJob?.cancel()
                                                    }
                                                },
                                                onDragCancel = {
                                                    edgeScrollJob?.cancel()
                                                    hoveredItemId = null
                                                    viewModel.cancelDrag()
                                                },
                                                onDragEnd = {
                                                    edgeScrollJob?.cancel()
                                                    val dRect = dockBoundsInWindow
                                                    val gRect = gridBoundsInWindow
                                                    val hoverId = hoveredItemId
                                                    hoveredItemId = null
                                                    val target: DropTarget? = when {
                                                        hoverId != null -> DropTarget.HoverItem(hoverId)
                                                        dRect != null && dRect.contains(draggedWindowPos) -> {
                                                            val slots = state.workspace.gridSpec.dockSlots
                                                            val slotW = (dRect.width / slots).coerceAtLeast(1f)
                                                            val rank = ((draggedWindowPos.x - dRect.left) / slotW).toInt()
                                                                .coerceIn(0, slots - 1)
                                                            DropTarget.DockSlot(rank)
                                                        }
                                                        gRect != null && gRect.contains(draggedWindowPos) -> {
                                                            val curPage = workspacePages.getOrNull(pagerState.currentPage)
                                                            if (curPage != null) {
                                                                val tx = (((draggedWindowPos.x - gRect.left) / (gRect.width / cols)).toInt())
                                                                    .coerceIn(0, cols - 1)
                                                                val ty = (((draggedWindowPos.y - gRect.top) / (gRect.height / rows)).toInt())
                                                                    .coerceIn(0, rows - 1)
                                                                DropTarget.DesktopCell(curPage.id, tx, ty)
                                                            } else {
                                                                null
                                                            }
                                                        }
                                                        else -> null
                                                    }

                                                    if (target != null) {
                                                        viewModel.completeDrop(
                                                            draggedItemId = item.id,
                                                            target = target,
                                                            defaultFolderTitle = defaultFolderTitle,
                                                            onRejected = {
                                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            },
                                                        )
                                                    } else {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        viewModel.cancelDrag()
                                                    }
                                                },
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .offset(x = cellW * cx, y = cellH * cy)
                                            .size(width = cellW * sx, height = cellH * sy)
                                            .onGloballyPositioned { coords ->
                                                val b = coords.boundsInWindow()
                                                itemCenterInWindow = Offset(b.left, b.top)
                                            }
                                            .graphicsLayer {
                                                alpha = if (isBeingDragged) 0.28f else 1f
                                                val hoverScale = if (isHoverTarget) 1.12f else 1f
                                                scaleX = hoverScale
                                                scaleY = hoverScale
                                            }
                                            .then(dragModifier),
                                    ) {
                                        when (item) {
                                            is WorkspaceItem.App -> {
                                                val entry = state.catalogByKey[item.component]
                                                val label = entry?.label ?: item.component.packageName.substringAfterLast('.')
                                                val bmp = rememberAppIcon(
                                                    viewModel.iconLoader,
                                                    item.component,
                                                    state.settings.selectedIconPackPackage,
                                                )
                                                AppIconTile(
                                                    label = label,
                                                    iconBitmap = bmp,
                                                    iconSize = iconDp,
                                                    showLabel = state.settings.showIconLabels,
                                                    iconShape = state.settings.iconShape,
                                                    monochromeEnabled = state.settings.monochromeIcons,
                                                    isSuspendedOrPaused = entry == null || entry.isSuspended || entry.isQuietModePaused,
                                                    isLocked = entry?.isLocked == true,
                                                    isEditMode = isEditMode,
                                                    badgeCount = if (state.settings.notificationBadgesEnabled) {
                                                        badgeCounts[item.component.packageName] ?: 0
                                                    } else {
                                                        0
                                                    },
                                                    profileBadge = entry?.profileBadge ?: app.aurora.launcher.core.model.ProfileBadge.PERSONAL,
                                                    useWallpaperScrimForLabel = true,
                                                    accessibilityActions = a11yActions,
                                                    onClick = {
                                                        if (isEditMode) return@AppIconTile
                                                        if (item.orphanSinceEpochMs != null && entry == null) {
                                                            onShowMessage(orphanNotice)
                                                        } else {
                                                            onLaunchApp(item.component)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        if (!isEditMode) {
                                                            viewModel.openQuickActions(item)
                                                        }
                                                    },
                                                    onRemoveClick = { viewModel.removeItemFromHome(item.id) },
                                                )
                                            }

                                            is WorkspaceItem.PinnedShortcut -> {
                                                AppIconTile(
                                                    label = item.label,
                                                    iconBitmap = null,
                                                    iconSize = iconDp,
                                                    showLabel = state.settings.showIconLabels,
                                                    iconShape = state.settings.iconShape,
                                                    monochromeEnabled = state.settings.monochromeIcons,
                                                    isEditMode = isEditMode,
                                                    useWallpaperScrimForLabel = true,
                                                    accessibilityActions = a11yActions,
                                                    onClick = {
                                                        if (!isEditMode) viewModel.launchPinnedShortcut(item)
                                                    },
                                                    onLongClick = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        if (!isEditMode) viewModel.openQuickActions(item)
                                                    },
                                                    onRemoveClick = { viewModel.removeItemFromHome(item.id) },
                                                )
                                            }

                                            is WorkspaceItem.Folder -> {
                                                folderTileSlot(
                                                    item,
                                                    iconDp,
                                                    state.settings.showIconLabels,
                                                    isEditMode,
                                                    true,
                                                    a11yActions,
                                                    { if (!isEditMode) onOpenFolder(item.id) },
                                                    {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        viewModel.enterEditMode()
                                                    },
                                                    Modifier,
                                                )
                                            }

                                            is WorkspaceItem.Widget -> {
                                                widgetSlot(
                                                    item,
                                                    isEditMode,
                                                    isDragging,
                                                    {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        viewModel.enterEditMode()
                                                    },
                                                    Modifier
                                                        .fillMaxSize()
                                                        .padding(4.dp),
                                                )
                                            }

                                            is WorkspaceItem.WidgetStack -> {
                                                widgetStackSlot(
                                                    item,
                                                    isEditMode,
                                                    isDragging,
                                                    {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        viewModel.enterEditMode()
                                                    },
                                                    Modifier
                                                        .fillMaxSize()
                                                        .padding(4.dp),
                                                )
                                            }
                                        }
                                    }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Page Indicators + Dock (hidden on App Library page so App Library scrolls cleanly)
        if (!onAppLibraryPage) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = AuroraSpacing.md, vertical = AuroraSpacing.sm)
                    .pointerInput(state.settings.swipeUpAction, showAppLibrary, workspacePages.size) {
                        var dySum = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dySum = 0f },
                            onVerticalDrag = { _, dy ->
                                dySum += dy
                                if (dySum < -56.dp.toPx()) {
                                    dySum = 0f
                                    when (state.settings.swipeUpAction) {
                                        SwipeUpAction.APP_LIBRARY -> {
                                            if (showAppLibrary) {
                                                scope.launch { pagerState.animateScrollToPage(workspacePages.size) }
                                            }
                                        }
                                        SwipeUpAction.SPOTLIGHT -> onOpenSpotlight()
                                        SwipeUpAction.NONE -> Unit
                                    }
                                }
                            },
                        )
                    },
            ) {
                PageIndicatorRow(
                    workspacePageCount = workspacePages.size,
                    hasAppLibrary = showAppLibrary,
                    currentPage = pagerState.currentPage,
                    onSelectPage = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
                )

                // Sole GlassSurface on Home screen per DESIGN.md & antislop R-10.
                GlassSurface(
                    tier = glassTier,
                    cornerRadius = AuroraRadii.dock,
                    modifier = Modifier
                        .then(
                            if (isTabletViewport) Modifier.widthIn(max = 520.dp) else Modifier.fillMaxWidth(),
                        )
                        .onGloballyPositioned { coords ->
                            dockBoundsInWindow = coords.boundsInWindow()
                        },
                ) {
                    val dockIconDp = state.settings.iconSizeDp.coerceIn(52, 64).dp
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuroraSpacing.sm, vertical = 10.dp),
                    ) {
                        val slots = if (isEditMode) {
                            state.workspace.gridSpec.dockSlots
                        } else {
                            state.workspace.dockItems.size.coerceIn(1, state.workspace.gridSpec.dockSlots)
                        }
                        for (slotIdx in 0 until slots) {
                            val dockItem = state.workspace.dockItems.getOrNull(slotIdx)
                            key(dockItem?.id?.value ?: -(slotIdx + 1L)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.weight(1f),
                            ) {
                                when (dockItem) {
                                    is WorkspaceItem.App -> {
                                        val entry = state.catalogByKey[dockItem.component]
                                        val label = entry?.label ?: dockItem.component.packageName
                                        val bmp = rememberAppIcon(
                                            viewModel.iconLoader,
                                            dockItem.component,
                                            state.settings.selectedIconPackPackage,
                                        )
                                        AppIconTile(
                                            label = label,
                                            iconBitmap = bmp,
                                            iconSize = dockIconDp,
                                            showLabel = false,
                                            iconShape = state.settings.iconShape,
                                            monochromeEnabled = state.settings.monochromeIcons,
                                            isSuspendedOrPaused = entry == null || entry.isSuspended || entry.isQuietModePaused,
                                            isLocked = entry?.isLocked == true,
                                            isEditMode = isEditMode,
                                            badgeCount = if (state.settings.notificationBadgesEnabled) {
                                                badgeCounts[dockItem.component.packageName] ?: 0
                                            } else {
                                                0
                                            },
                                            profileBadge = entry?.profileBadge ?: app.aurora.launcher.core.model.ProfileBadge.PERSONAL,
                                            useWallpaperScrimForLabel = false,
                                            accessibilityActions = listOf(
                                                CustomAccessibilityAction(a11yMoveDialog) {
                                                    moveDialogTarget = dockItem
                                                    true
                                                },
                                                CustomAccessibilityAction(a11yRemove) {
                                                    viewModel.removeItemFromHome(dockItem.id)
                                                    true
                                                },
                                            ),
                                            onClick = {
                                                if (!isEditMode) onLaunchApp(dockItem.component)
                                            },
                                            onLongClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (!isEditMode) viewModel.openQuickActions(dockItem)
                                            },
                                            onRemoveClick = { viewModel.removeItemFromHome(dockItem.id) },
                                        )
                                    }

                                    is WorkspaceItem.Folder -> {
                                        folderTileSlot(
                                            dockItem,
                                            dockIconDp,
                                            false,
                                            isEditMode,
                                            false,
                                            listOf(
                                                CustomAccessibilityAction(a11yMoveDialog) {
                                                    moveDialogTarget = dockItem
                                                    true
                                                },
                                            ),
                                            { if (!isEditMode) onOpenFolder(dockItem.id) },
                                            {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.enterEditMode()
                                            },
                                            Modifier,
                                        )
                                    }

                                    else -> {
                                        Spacer(modifier = Modifier.size(dockIconDp))
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }
        }

        // Floating ghost preview while dragging an item
        if (activeDrag != null) {
            val ghostSizePx = with(density) { 64.dp.toPx() }
            val draggedAppEntry = activeDrag.sourceComponent?.let { state.catalogByKey[it] }
            val ghostBmp = activeDrag.sourceComponent?.let {
                rememberAppIcon(viewModel.iconLoader, it, state.settings.selectedIconPackPackage)
            }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (draggedWindowPos.x - ghostSizePx / 2f).roundToInt(),
                            (draggedWindowPos.y - ghostSizePx / 2f).roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        scaleX = 1.1f
                        scaleY = 1.1f
                        alpha = 0.9f
                    },
            ) {
                AppIconTile(
                    label = draggedAppEntry?.label.orEmpty(),
                    iconBitmap = ghostBmp,
                    iconSize = 60.dp,
                    showLabel = false,
                    iconShape = state.settings.iconShape,
                    onClick = {},
                )
            }
        }
    }

    // Long-press Quick Actions Menu (UC-06, UC-16)
    val qaTarget = state.quickActionsTarget
    if (qaTarget != null) {
        val label = qaTarget.appEntry?.label
            ?: (qaTarget.item as? WorkspaceItem.PinnedShortcut)?.label
            ?: stringResource(R.string.aurora_app_name)
        AlertDialog(
            onDismissRequest = viewModel::dismissQuickActions,
            title = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs)) {
                    qaTarget.shortcuts.forEach { sc ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget)
                                .clip(RoundedCornerShape(AuroraRadii.input))
                                .background(palette.surfaceVariant)
                                .clickable { viewModel.launchDynamicShortcut(sc) }
                                .padding(horizontal = AuroraSpacing.sm),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.xs),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BookmarkBorder,
                                    contentDescription = null,
                                    tint = palette.accent,
                                )
                                Text(
                                    text = sc.shortLabel,
                                    style = AuroraTypography.bodyMedium,
                                    color = palette.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.pinAppShortcut(sc) { ok ->
                                        onShowMessage(if (ok) addedNotice else fullNotice)
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PushPin,
                                    contentDescription = stringResource(R.string.quick_action_pin_shortcut),
                                    tint = palette.accent,
                                )
                            }
                        }
                    }

                    QuickMenuActionRow(stringResource(R.string.quick_action_edit_home)) {
                        viewModel.enterEditMode()
                    }
                    QuickMenuActionRow(stringResource(R.string.a11y_move_dialog)) {
                        val itemRef = qaTarget.item
                        viewModel.dismissQuickActions()
                        moveDialogTarget = itemRef
                    }
                    QuickMenuActionRow(stringResource(R.string.quick_action_remove_home)) {
                        viewModel.removeItemFromHome(qaTarget.item.id)
                    }

                    if (qaTarget.item is WorkspaceItem.App) {
                        val comp = qaTarget.item.component
                        val locked = qaTarget.appEntry?.isLocked == true
                        QuickMenuActionRow(
                            if (locked) {
                                stringResource(R.string.quick_action_unlock_app)
                            } else {
                                stringResource(R.string.quick_action_lock_app)
                            },
                        ) {
                            viewModel.toggleLockApp(comp, locked)
                        }
                        QuickMenuActionRow(stringResource(R.string.quick_action_hide_app)) {
                            viewModel.hideApp(comp)
                        }
                        QuickMenuActionRow(stringResource(R.string.quick_action_app_info)) {
                            viewModel.openAppInfo(comp)
                        }
                        if (qaTarget.appEntry?.isSystemApp == false) {
                            QuickMenuActionRow(
                                label = stringResource(R.string.quick_action_uninstall),
                                isDestructive = true,
                            ) {
                                viewModel.dismissQuickActions()
                                uninstallConfirmTarget = comp to label
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissQuickActions) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    // Uninstall confirmation protected by filterTouchesWhenObscured (section 11.3)
    val uninstallPair = uninstallConfirmTarget
    val hostView = LocalView.current
    DisposableEffect(uninstallPair != null) {
        val prev = hostView.filterTouchesWhenObscured
        if (uninstallPair != null) hostView.filterTouchesWhenObscured = true
        onDispose { hostView.filterTouchesWhenObscured = prev }
    }
    if (uninstallPair != null) {
        AlertDialog(
            onDismissRequest = { uninstallConfirmTarget = null },
            title = { Text(stringResource(R.string.quick_action_uninstall_confirm_title, uninstallPair.second)) },
            text = { Text(stringResource(R.string.quick_action_uninstall_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val comp = uninstallPair.first
                        uninstallConfirmTarget = null
                        viewModel.requestUninstall(comp)
                    },
                ) {
                    Text(stringResource(R.string.quick_action_uninstall), color = palette.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallConfirmTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Accessible non-gesture "Move to..." destination dialog (E23)
    val movingItem = moveDialogTarget
    if (movingItem != null) {
        val titleName = when (movingItem) {
            is WorkspaceItem.App -> state.catalogByKey[movingItem.component]?.label ?: movingItem.component.packageName
            is WorkspaceItem.Folder -> movingItem.title.ifBlank { defaultFolderTitle }
            is WorkspaceItem.PinnedShortcut -> movingItem.label
            is WorkspaceItem.Widget -> movingItem.label
            is WorkspaceItem.WidgetStack -> defaultFolderTitle
        }
        AlertDialog(
            onDismissRequest = { moveDialogTarget = null },
            title = { Text(stringResource(R.string.a11y_move_dialog_title, titleName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AuroraSpacing.sm)) {
                    val curPage = workspacePages.getOrNull(pagerState.currentPage) ?: workspacePages.first()
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs),
                    ) {
                        for (r in 0 until state.workspace.gridSpec.rows) {
                            for (c in 0 until state.workspace.gridSpec.columns) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.moveItemToExplicitDestination(
                                            movingItem.id,
                                            DropTarget.DesktopCell(curPage.id, c, r),
                                            defaultFolderTitle,
                                        )
                                        moveDialogTarget = null
                                    },
                                    modifier = Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 40.dp),
                                ) {
                                    Text("${c + 1},${r + 1}", style = AuroraTypography.labelSmall)
                                }
                            }
                        }
                    }

                    if (movingItem !is WorkspaceItem.Widget && movingItem !is WorkspaceItem.WidgetStack) {
                        Button(
                            onClick = {
                                viewModel.moveItemToExplicitDestination(
                                    movingItem.id,
                                    DropTarget.DockSlot(state.workspace.dockItems.size),
                                    defaultFolderTitle,
                                )
                                moveDialogTarget = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accent,
                                contentColor = palette.onAccent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.a11y_move_to_dock))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveDialogTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun PreviewModeBanner(
    onSetDefault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AuroraTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AuroraRadii.card))
            .background(palette.surface.copy(alpha = 0.94f))
            .border(1.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(AuroraRadii.card))
            .padding(horizontal = AuroraSpacing.md, vertical = AuroraSpacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.preview_banner_title),
                style = AuroraTypography.bodyMedium,
                color = palette.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.preview_banner_body),
                style = AuroraTypography.labelMedium,
                color = palette.onSurfaceSecondary,
            )
        }
        Spacer(modifier = Modifier.width(AuroraSpacing.sm))
        Button(
            onClick = onSetDefault,
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.onAccent,
            ),
            shape = RoundedCornerShape(AuroraRadii.input),
            modifier = Modifier.defaultMinSize(minHeight = AuroraSpacing.minTouchTarget),
        ) {
            Text(stringResource(R.string.preview_banner_action))
        }
    }
}

@Composable
private fun EditModeTopBar(
    onAddWidget: () -> Unit,
    onCycleWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onFinishEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AuroraTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AuroraRadii.card))
            .background(palette.surface.copy(alpha = 0.95f))
            .border(1.dp, palette.outline.copy(alpha = 0.35f), RoundedCornerShape(AuroraRadii.card))
            .padding(horizontal = AuroraSpacing.sm, vertical = 4.dp),
    ) {
        TextButton(
            onClick = onAddWidget,
            modifier = Modifier.defaultMinSize(minHeight = AuroraSpacing.minTouchTarget),
        ) {
            Icon(Icons.Outlined.Widgets, contentDescription = null, tint = palette.accent)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.edit_bar_widgets), color = palette.onSurface)
        }
        IconButton(
            onClick = onCycleWallpaper,
            modifier = Modifier.defaultMinSize(
                minWidth = AuroraSpacing.minTouchTarget,
                minHeight = AuroraSpacing.minTouchTarget,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.Wallpaper,
                contentDescription = stringResource(R.string.edit_bar_wallpaper),
                tint = palette.onSurface,
            )
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.defaultMinSize(
                minWidth = AuroraSpacing.minTouchTarget,
                minHeight = AuroraSpacing.minTouchTarget,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.edit_bar_settings),
                tint = palette.onSurface,
            )
        }
        Button(
            onClick = onFinishEdit,
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.onAccent,
            ),
            shape = RoundedCornerShape(AuroraRadii.input),
            modifier = Modifier.defaultMinSize(minHeight = 40.dp),
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.action_done))
        }
    }
}

@Composable
private fun PageIndicatorRow(
    workspacePageCount: Int,
    hasAppLibrary: Boolean,
    currentPage: Int,
    onSelectPage: (Int) -> Unit,
) {
    val palette = AuroraTheme.palette
    val total = workspacePageCount + if (hasAppLibrary) 1 else 0
    if (total <= 1) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(palette.iconLabelScrim)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        for (idx in 0 until total) {
            val isLibraryDot = hasAppLibrary && idx == workspacePageCount
            val selected = idx == currentPage
            val cd = if (isLibraryDot) {
                stringResource(R.string.home_app_library_indicator_cd)
            } else {
                stringResource(R.string.home_page_indicator_cd, idx + 1, workspacePageCount)
            }
            Box(
                modifier = Modifier
                    .size(width = if (selected) 16.dp else 7.dp, height = 7.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) palette.iconLabelText else palette.iconLabelText.copy(alpha = 0.42f),
                    )
                    .semantics { contentDescription = cd }
                    .clickable { onSelectPage(idx) },
            )
        }
    }
}

@Composable
private fun QuickMenuActionRow(
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = AuroraTheme.palette
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget)
            .clip(RoundedCornerShape(AuroraRadii.input))
            .clickable(onClick = onClick)
            .padding(horizontal = AuroraSpacing.sm),
    ) {
        Text(
            text = label,
            style = AuroraTypography.bodyLarge,
            color = if (isDestructive) palette.error else palette.onSurface,
        )
    }
}

private data class Quad(val x: Int, val y: Int, val spanX: Int, val spanY: Int)

private fun desktopCoords(item: WorkspaceItem): Quad? = when (item) {
    is WorkspaceItem.App -> {
        val p = item.placement as? LeafPlacement.Desktop
        p?.let { Quad(it.cellX, it.cellY, 1, 1) }
    }
    is WorkspaceItem.PinnedShortcut -> {
        val p = item.placement as? LeafPlacement.Desktop
        p?.let { Quad(it.cellX, it.cellY, 1, 1) }
    }
    is WorkspaceItem.Folder -> {
        val p = item.placement as? ContainerPlacement.Desktop
        p?.let { Quad(it.cellX, it.cellY, 1, 1) }
    }
    is WorkspaceItem.Widget -> Quad(item.cellX, item.cellY, item.spanX, item.spanY)
    is WorkspaceItem.WidgetStack -> Quad(item.cellX, item.cellY, item.spanX, item.spanY)
}
