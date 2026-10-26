package app.aurora.launcher.feature.widgets

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.aurora.launcher.core.data.repository.WorkspaceRepository
import app.aurora.launcher.core.designsystem.AuroraRadii
import app.aurora.launcher.core.designsystem.AuroraSpacing
import app.aurora.launcher.core.designsystem.AuroraTheme
import app.aurora.launcher.core.designsystem.AuroraTypography
import app.aurora.launcher.core.designsystem.GlassSurface
import app.aurora.launcher.core.designsystem.R
import app.aurora.launcher.core.model.AppWidgetId
import app.aurora.launcher.core.model.GlassTier
import app.aurora.launcher.core.model.GridSpec
import app.aurora.launcher.core.model.ItemId
import app.aurora.launcher.core.model.PageId
import app.aurora.launcher.core.model.WidgetProviderCandidate
import app.aurora.launcher.core.model.WidgetStatus
import app.aurora.launcher.core.model.WorkspaceItem
import app.aurora.launcher.core.platform.AppWidgetHostManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WidgetPickerUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val candidates: List<WidgetProviderCandidate> = emptyList(),
)

@HiltViewModel
class WidgetPickerViewModel @Inject constructor(
    val hostManager: AppWidgetHostManager,
    private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WidgetPickerUiState())
    val uiState: StateFlow<WidgetPickerUiState> = _uiState.asStateFlow()

    fun loadProviders(gridSpec: GridSpec) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val providers = hostManager.listInstalledProviders(gridSpec)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                candidates = providers,
            )
        }
    }

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun commitPlacedWidget(
        candidate: WidgetProviderCandidate,
        appWidgetId: Int,
        preferredPageId: PageId?,
        gridSpec: GridSpec,
        onComplete: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val itemId = workspaceRepository.placeWidget(
                candidate = candidate,
                appWidgetId = AppWidgetId(appWidgetId),
                preferredPageId = preferredPageId,
                gridSpec = gridSpec,
                needsConfig = false,
            )
            if (itemId == null) {
                hostManager.deleteWidgetId(appWidgetId)
                onComplete(false)
            } else {
                onComplete(true)
            }
        }
    }
}

@Composable
fun WidgetPickerOverlay(
    visible: Boolean,
    glassTier: GlassTier,
    gridSpec: GridSpec,
    preferredPageId: PageId?,
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit,
    viewModel: WidgetPickerViewModel = hiltViewModel(),
) {
    if (!visible) return

    BackHandler(onBack = onDismiss)

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = AuroraTheme.palette
    val addedNotice = stringResource(R.string.home_grid_added_notice)
    val fullNotice = stringResource(R.string.home_grid_full_notice)

    LaunchedEffect(gridSpec) {
        viewModel.loadProviders(gridSpec)
    }

    var pendingCandidate by remember { mutableStateOf<WidgetProviderCandidate?>(null) }
    var pendingWidgetId by remember { mutableIntStateOf(-1) }

    val configLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val cand = pendingCandidate
        val wId = pendingWidgetId
        pendingCandidate = null
        pendingWidgetId = -1
        if (result.resultCode == Activity.RESULT_OK && cand != null && wId > 0) {
            viewModel.commitPlacedWidget(cand, wId, preferredPageId, gridSpec) { ok ->
                onShowMessage(if (ok) addedNotice else fullNotice)
                onDismiss()
            }
        } else if (wId > 0) {
            // Cancel cleans up allocated appWidgetId immediately (E13).
            viewModel.hostManager.deleteWidgetId(wId)
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val cand = pendingCandidate
        val wId = pendingWidgetId
        if (result.resultCode == Activity.RESULT_OK && cand != null && wId > 0) {
            val configIntent = viewModel.hostManager.createConfigurationIntent(wId)
            if (configIntent != null) {
                runCatching { configLauncher.launch(configIntent) }.onFailure {
                    viewModel.hostManager.deleteWidgetId(wId)
                    pendingCandidate = null
                    pendingWidgetId = -1
                }
            } else {
                pendingCandidate = null
                pendingWidgetId = -1
                viewModel.commitPlacedWidget(cand, wId, preferredPageId, gridSpec) { ok ->
                    onShowMessage(if (ok) addedNotice else fullNotice)
                    onDismiss()
                }
            }
        } else {
            if (wId > 0) viewModel.hostManager.deleteWidgetId(wId)
            pendingCandidate = null
            pendingWidgetId = -1
        }
    }

    val filteredGroups = remember(state.candidates, state.query) {
        val q = state.query.trim().lowercase()
        val matched = if (q.isEmpty()) {
            state.candidates
        } else {
            state.candidates.filter {
                it.appLabel.lowercase().contains(q) ||
                    it.widgetLabel.lowercase().contains(q) ||
                    it.description.lowercase().contains(q)
            }
        }
        matched.groupBy { it.appLabel }
    }

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.54f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .padding(AuroraSpacing.md),
    ) {
        GlassSurface(
            tier = glassTier,
            cornerRadius = AuroraRadii.folder,
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(540.dp)
                    .background(palette.surface.copy(alpha = if (glassTier == GlassTier.TIER_C_SOLID) 0.98f else 0.92f))
                    .padding(AuroraSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.widgets_picker_title),
                        style = AuroraTypography.titleLarge,
                        color = palette.onSurface,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(
                            minWidth = AuroraSpacing.minTouchTarget,
                            minHeight = AuroraSpacing.minTouchTarget,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = palette.onSurface,
                        )
                    }
                }

                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.widgets_search_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = palette.accent,
                        )
                    },
                    shape = RoundedCornerShape(AuroraRadii.input),
                    modifier = Modifier.fillMaxWidth(),
                )

                when {
                    state.isLoading -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                            ) {
                                CircularProgressIndicator(
                                    color = palette.accent,
                                    modifier = Modifier.size(24.dp),
                                )
                                Text(
                                    text = stringResource(R.string.widgets_loading),
                                    style = AuroraTypography.bodyMedium,
                                    color = palette.onSurfaceSecondary,
                                )
                            }
                        }
                    }

                    filteredGroups.isEmpty() -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = AuroraSpacing.lg),
                        ) {
                            Text(
                                text = stringResource(R.string.widgets_empty_title),
                                style = AuroraTypography.titleMedium,
                                color = palette.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.widgets_empty_body),
                                style = AuroraTypography.bodyMedium,
                                color = palette.onSurfaceSecondary,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = AuroraSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            filteredGroups.forEach { (appName, list) ->
                                item(key = "hdr_$appName") {
                                    Text(
                                        text = appName,
                                        style = AuroraTypography.titleMedium,
                                        color = palette.accent,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                items(list, key = { it.provider.asCompactKey() }) { candidate ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(AuroraRadii.card))
                                            .background(palette.surfaceVariant)
                                            .padding(AuroraSpacing.md),
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(
                                                text = candidate.widgetLabel,
                                                style = AuroraTypography.bodyLarge,
                                                color = palette.onSurface,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Text(
                                                text = stringResource(
                                                    R.string.widgets_span_badge,
                                                    candidate.defaultSpanX,
                                                    candidate.defaultSpanY,
                                                ),
                                                style = AuroraTypography.labelMedium,
                                                color = palette.onSurfaceSecondary,
                                            )
                                            if (candidate.description.isNotBlank()) {
                                                Text(
                                                    text = candidate.description,
                                                    style = AuroraTypography.bodyMedium,
                                                    color = palette.onSurfaceSecondary,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(AuroraSpacing.sm))
                                        Button(
                                            onClick = {
                                                val allocated = viewModel.hostManager.allocateWidgetId()
                                                if (allocated <= 0) return@Button
                                                val boundNow = viewModel.hostManager.bindWidgetIfAllowed(allocated, candidate)
                                                if (boundNow) {
                                                    val cfg = viewModel.hostManager.createConfigurationIntent(allocated)
                                                    if (cfg != null) {
                                                        pendingCandidate = candidate
                                                        pendingWidgetId = allocated
                                                        runCatching { configLauncher.launch(cfg) }.onFailure {
                                                            viewModel.hostManager.deleteWidgetId(allocated)
                                                            pendingCandidate = null
                                                            pendingWidgetId = -1
                                                        }
                                                    } else {
                                                        viewModel.commitPlacedWidget(
                                                            candidate,
                                                            allocated,
                                                            preferredPageId,
                                                            gridSpec,
                                                        ) { ok ->
                                                            onShowMessage(if (ok) addedNotice else fullNotice)
                                                            onDismiss()
                                                        }
                                                    }
                                                } else {
                                                    pendingCandidate = candidate
                                                    pendingWidgetId = allocated
                                                    val bindIntent = viewModel.hostManager.createBindWidgetIntent(allocated, candidate)
                                                    runCatching { bindLauncher.launch(bindIntent) }.onFailure {
                                                        viewModel.hostManager.deleteWidgetId(allocated)
                                                        pendingCandidate = null
                                                        pendingWidgetId = -1
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = palette.accent,
                                                contentColor = palette.onAccent,
                                            ),
                                            shape = RoundedCornerShape(AuroraRadii.input),
                                            modifier = Modifier.defaultMinSize(minHeight = AuroraSpacing.minTouchTarget),
                                        ) {
                                            Text(stringResource(R.string.widgets_add_button))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WidgetHostSlot(
    widget: WorkspaceItem.Widget,
    hostManager: AppWidgetHostManager,
    isEditMode: Boolean,
    isDragging: Boolean,
    gridSpec: GridSpec,
    onResize: (ItemId, Int, Int) -> Unit,
    onRemove: (ItemId) -> Unit,
    onLongPressEnterEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AuroraTheme.palette
    val errorText = stringResource(R.string.widgets_error_card)
    val missingText = stringResource(R.string.widgets_missing_provider)
    val cardShape = RoundedCornerShape(AuroraRadii.card)

    val shrinkW = stringResource(R.string.widgets_resize_smaller_w)
    val expandW = stringResource(R.string.widgets_resize_larger_w)
    val shrinkH = stringResource(R.string.widgets_resize_smaller_h)
    val expandH = stringResource(R.string.widgets_resize_larger_h)
    val removeLabel = stringResource(R.string.action_remove)

    Box(
        modifier = modifier
            .clip(cardShape)
            .border(
                width = if (isEditMode) 1.5.dp else 0.5.dp,
                color = if (isEditMode) palette.accent else palette.outline.copy(alpha = 0.25f),
                shape = cardShape,
            )
            .semantics {
                customActions = buildList {
                    if (widget.spanX > widget.minSpanX) {
                        add(CustomAccessibilityAction(shrinkW) { onResize(widget.id, widget.spanX - 1, widget.spanY); true })
                    }
                    if (widget.spanX < minOf(widget.maxSpanX, gridSpec.columns)) {
                        add(CustomAccessibilityAction(expandW) { onResize(widget.id, widget.spanX + 1, widget.spanY); true })
                    }
                    if (widget.spanY > widget.minSpanY) {
                        add(CustomAccessibilityAction(shrinkH) { onResize(widget.id, widget.spanX, widget.spanY - 1); true })
                    }
                    if (widget.spanY < minOf(widget.maxSpanY, gridSpec.rows)) {
                        add(CustomAccessibilityAction(expandH) { onResize(widget.id, widget.spanX, widget.spanY + 1); true })
                    }
                    add(CustomAccessibilityAction(removeLabel) { onRemove(widget.id); true })
                }
            }
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPressEnterEdit,
            ),
    ) {
        if (widget.status == WidgetStatus.PROVIDER_MISSING || widget.status == WidgetStatus.ERROR) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.surfaceVariant)
                    .padding(AuroraSpacing.md),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs),
                ) {
                    Text(
                        text = if (widget.status == WidgetStatus.PROVIDER_MISSING) missingText else errorText,
                        style = AuroraTypography.bodyMedium,
                        color = palette.onSurface,
                    )
                    IconButton(onClick = { onRemove(widget.id) }) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = removeLabel,
                            tint = palette.error,
                        )
                    }
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    hostManager.createHostView(ctx, widget.appWidgetId.value, errorText)
                        ?: android.widget.FrameLayout(ctx)
                },
                update = { view ->
                    (view as? app.aurora.launcher.core.platform.SafeAppWidgetHostView)
                        ?.setDragFrozen(isDragging || isEditMode)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isEditMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(palette.surface.copy(alpha = 0.88f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (widget.spanX > widget.minSpanX) {
                        SmallResizePill("-W") { onResize(widget.id, widget.spanX - 1, widget.spanY) }
                    }
                    if (widget.spanX < minOf(widget.maxSpanX, gridSpec.columns)) {
                        SmallResizePill("+W") { onResize(widget.id, widget.spanX + 1, widget.spanY) }
                    }
                    if (widget.spanY > widget.minSpanY) {
                        SmallResizePill("-H") { onResize(widget.id, widget.spanX, widget.spanY - 1) }
                    }
                    if (widget.spanY < minOf(widget.maxSpanY, gridSpec.rows)) {
                        SmallResizePill("+H") { onResize(widget.id, widget.spanX, widget.spanY + 1) }
                    }
                }
                IconButton(
                    onClick = { onRemove(widget.id) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = removeLabel,
                        tint = palette.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallResizePill(
    label: String,
    onClick: () -> Unit,
) {
    val palette = AuroraTheme.palette
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = AuroraTypography.labelSmall,
            color = palette.accent,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun WidgetStackSlot(
    stack: WorkspaceItem.WidgetStack,
    hostManager: AppWidgetHostManager,
    isEditMode: Boolean,
    isDragging: Boolean,
    gridSpec: GridSpec,
    onActiveIndexChange: (ItemId, Int) -> Unit,
    onResize: (ItemId, Int, Int) -> Unit,
    onRemove: (ItemId) -> Unit,
    onLongPressEnterEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AuroraTheme.palette
    val count = stack.widgets.size.coerceAtLeast(1)
    val initialPage = stack.activeIndex.coerceIn(0, count - 1)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { count })
    val scope = rememberCoroutineScope()
    val nextActionLabel = stringResource(R.string.widgets_stack_next)

    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != stack.activeIndex) {
            onActiveIndexChange(stack.id, pagerState.settledPage)
        }
    }

    Box(
        modifier = modifier.semantics {
            if (count > 1) {
                customActions = listOf(
                    CustomAccessibilityAction(nextActionLabel) {
                        scope.launch {
                            pagerState.animateScrollToPage((pagerState.currentPage + 1) % count)
                        }
                        true
                    },
                )
            }
        },
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIdx ->
            val childWidget = stack.widgets.getOrNull(pageIdx)
            if (childWidget != null) {
                WidgetHostSlot(
                    widget = childWidget,
                    hostManager = hostManager,
                    isEditMode = isEditMode,
                    isDragging = isDragging,
                    gridSpec = gridSpec,
                    onResize = { _, w, h -> onResize(stack.id, w, h) },
                    onRemove = { childId -> onRemove(childId) },
                    onLongPressEnterEdit = onLongPressEnterEdit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (count > 1) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .clip(CircleShape)
                    .background(palette.surface.copy(alpha = 0.72f))
                    .padding(vertical = 6.dp, horizontal = 3.dp),
            ) {
                for (i in 0 until count) {
                    Box(
                        modifier = Modifier
                            .size(if (i == pagerState.currentPage) 6.dp else 4.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) palette.accent else palette.onSurfaceSecondary,
                            ),
                    )
                }
            }
        }
    }
}
