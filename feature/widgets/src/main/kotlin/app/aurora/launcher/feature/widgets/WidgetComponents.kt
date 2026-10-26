package app.aurora.launcher.feature.widgets

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AspectRatio
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.aurora.launcher.core.data.repository.LauncherSettingsRepository
import app.aurora.launcher.core.data.repository.WorkspaceRepository
import app.aurora.launcher.core.designsystem.AuroraRadii
import app.aurora.launcher.core.designsystem.AuroraSpacing
import app.aurora.launcher.core.designsystem.AuroraTheme
import app.aurora.launcher.core.designsystem.AuroraTypography
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class WidgetPickerUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val candidates: List<WidgetProviderCandidate> = emptyList(),
    val showSmartGlanceCandidate: Boolean = false,
)

@HiltViewModel
class WidgetPickerViewModel @Inject constructor(
    val hostManager: AppWidgetHostManager,
    private val workspaceRepository: WorkspaceRepository,
    private val settingsRepository: LauncherSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WidgetPickerUiState())
    val uiState: StateFlow<WidgetPickerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                _uiState.value = _uiState.value.copy(showSmartGlanceCandidate = !s.showSmartHeaderOnPage1)
            }
        }
    }

    fun restoreSmartHeader(onComplete: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(showSmartHeaderOnPage1 = true) }
            onComplete()
        }
    }

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
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(palette.surface.copy(alpha = if (glassTier == GlassTier.TIER_C_SOLID) 0.98f else 0.94f))
                .navigationBarsPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = AuroraSpacing.lg, vertical = AuroraSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
        ) {
            // Drag handle pill
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(palette.onSurfaceSecondary.copy(alpha = 0.35f)),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.widgets_picker_title),
                    style = AuroraTypography.titleLarge,
                    color = palette.onSurface,
                    fontWeight = FontWeight.Bold,
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

                filteredGroups.isEmpty() && !state.showSmartGlanceCandidate -> {
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
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = AuroraSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.showSmartGlanceCandidate && (state.query.isBlank() || "aurora smart glance jam clock google search".contains(state.query.trim().lowercase()))) {
                            item(key = "hdr_aurora_system", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "Aurora Launcher",
                                    style = AuroraTypography.titleSmall,
                                    color = palette.accent,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                )
                            }
                            item(key = "candidate_smart_glance", span = { GridItemSpan(maxLineSpan) }) {
                                SmartGlanceCandidateCard(
                                    onRestore = {
                                        viewModel.restoreSmartHeader {
                                            onShowMessage(addedNotice)
                                            onDismiss()
                                        }
                                    },
                                )
                            }
                        }

                        filteredGroups.forEach { (appName, list) ->
                            item(key = "hdr_$appName", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = appName,
                                    style = AuroraTypography.titleSmall,
                                    color = palette.accent,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                )
                            }
                            items(list, key = { it.provider.asCompactKey() }) { candidate ->
                                WidgetCandidateCard(
                                    candidate = candidate,
                                    hostManager = viewModel.hostManager,
                                    onAddWidget = {
                                        val allocated = viewModel.hostManager.allocateWidgetId()
                                        if (allocated > 0) {
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
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartGlanceCandidateCard(
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AuroraTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AuroraRadii.card))
            .background(palette.surfaceVariant)
            .border(0.75.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(AuroraRadii.card))
            .padding(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.smart_glance_widget_title),
                style = AuroraTypography.titleSmall,
                color = palette.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.smart_glance_widget_desc),
                style = AuroraTypography.bodySmall,
                color = palette.onSurfaceSecondary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onRestore,
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.onAccent,
            ),
            shape = RoundedCornerShape(AuroraRadii.input),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.widgets_add_button),
                style = AuroraTypography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun WidgetCandidateCard(
    candidate: WidgetProviderCandidate,
    hostManager: AppWidgetHostManager,
    onAddWidget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AuroraTheme.palette
    var previewBitmap by remember(candidate.provider) { mutableStateOf<Bitmap?>(null) }
    var appIconBitmap by remember(candidate.provider) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(candidate.provider) {
        withContext(Dispatchers.IO) {
            previewBitmap = hostManager.loadWidgetPreview(candidate)
            appIconBitmap = hostManager.loadWidgetAppIcon(candidate)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AuroraRadii.card))
            .background(palette.surfaceVariant)
            .border(0.75.dp, palette.glassHairline.copy(alpha = 0.2f), RoundedCornerShape(AuroraRadii.card))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (appIconBitmap != null) {
                    Image(
                        bitmap = appIconBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(5.dp)),
                    )
                }
                Text(
                    text = candidate.widgetLabel,
                    style = AuroraTypography.labelMedium,
                    color = palette.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.surface)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${candidate.defaultSpanX}×${candidate.defaultSpanY}",
                    style = AuroraTypography.labelSmall,
                    color = palette.accent,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.surface)
                .border(0.5.dp, palette.outline.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .padding(6.dp),
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap!!.asImageBitmap(),
                    contentDescription = candidate.widgetLabel,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (appIconBitmap != null) {
                        Image(
                            bitmap = appIconBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = "${candidate.defaultSpanX} × ${candidate.defaultSpanY}",
                        style = AuroraTypography.labelSmall,
                        color = palette.onSurfaceSecondary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Button(
            onClick = onAddWidget,
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.onAccent,
            ),
            shape = RoundedCornerShape(AuroraRadii.input),
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.widgets_add_button),
                style = AuroraTypography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
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
    val cardShape = RoundedCornerShape(22.dp)
    var showResizeDialog by remember { mutableStateOf(false) }

    val shrinkW = stringResource(R.string.widgets_resize_smaller_w)
    val expandW = stringResource(R.string.widgets_resize_larger_w)
    val shrinkH = stringResource(R.string.widgets_resize_smaller_h)
    val expandH = stringResource(R.string.widgets_resize_larger_h)
    val removeLabel = stringResource(R.string.action_remove)

    Box(
        modifier = modifier
            .clip(cardShape)
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
            // iOS-style minus badge at TopStart
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xDD3A3A3C))
                    .clickable { onRemove(widget.id) },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Remove,
                    contentDescription = removeLabel,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Universal resize button at TopEnd
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(palette.surface.copy(alpha = 0.92f))
                    .border(0.5.dp, palette.glassHairline.copy(alpha = 0.3f), CircleShape)
                    .clickable { showResizeDialog = true },
            ) {
                Icon(
                    imageVector = Icons.Outlined.AspectRatio,
                    contentDescription = stringResource(R.string.widgets_resize_dialog_title),
                    tint = palette.accent,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }

    if (showResizeDialog) {
        WidgetResizeDialog(
            widget = widget,
            gridSpec = gridSpec,
            onResize = { w, h -> onResize(widget.id, w, h) },
            onDismiss = { showResizeDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetResizeDialog(
    widget: WorkspaceItem.Widget,
    gridSpec: GridSpec,
    onResize: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AuroraTheme.palette
    var currentW by remember(widget.spanX) { mutableIntStateOf(widget.spanX) }
    var currentH by remember(widget.spanY) { mutableIntStateOf(widget.spanY) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(AuroraRadii.card))
                .background(palette.surface.copy(alpha = 0.96f))
                .border(0.75.dp, palette.glassHairline.copy(alpha = 0.25f), RoundedCornerShape(AuroraRadii.card))
                .padding(AuroraSpacing.lg),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.widgets_resize_dialog_title),
                        style = AuroraTypography.titleMedium,
                        color = palette.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = palette.onSurface,
                        )
                    }
                }

                Text(
                    text = "${stringResource(R.string.widgets_resize_current)}: $currentW × $currentH",
                    style = AuroraTypography.bodyMedium,
                    color = palette.accent,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = stringResource(R.string.widgets_resize_presets),
                    style = AuroraTypography.labelMedium,
                    color = palette.onSurfaceSecondary,
                    fontWeight = FontWeight.Medium,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val presets = listOf(
                        Pair(stringResource(R.string.widgets_resize_preset_2x2), Pair(2, 2)),
                        Pair(stringResource(R.string.widgets_resize_preset_4x1), Pair(4, 1)),
                        Pair(stringResource(R.string.widgets_resize_preset_4x2), Pair(4, 2)),
                        Pair(stringResource(R.string.widgets_resize_preset_4x3), Pair(4, 3)),
                        Pair(stringResource(R.string.widgets_resize_preset_4x4), Pair(4, 4)),
                    )
                    presets.forEach { (label, dim) ->
                        val (pw, ph) = dim
                        val isSelected = currentW == pw && currentH == ph
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) palette.accent else palette.surfaceVariant)
                                .clickable {
                                    currentW = pw
                                    currentH = ph
                                    onResize(pw, ph)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = label,
                                style = AuroraTypography.labelSmall,
                                color = if (isSelected) palette.onAccent else palette.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.widgets_resize_width),
                        style = AuroraTypography.bodyMedium,
                        color = palette.onSurface,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = {
                                if (currentW > 1) {
                                    currentW -= 1
                                    onResize(currentW, currentH)
                                }
                            },
                            enabled = currentW > 1,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Remove,
                                contentDescription = stringResource(R.string.widgets_resize_smaller_w),
                                tint = if (currentW > 1) palette.onSurface else palette.onSurfaceSecondary.copy(alpha = 0.3f),
                            )
                        }
                        Text(
                            text = "$currentW",
                            style = AuroraTypography.titleMedium,
                            color = palette.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(
                            onClick = {
                                if (currentW < gridSpec.columns) {
                                    currentW += 1
                                    onResize(currentW, currentH)
                                }
                            },
                            enabled = currentW < gridSpec.columns,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.widgets_resize_larger_w),
                                tint = if (currentW < gridSpec.columns) palette.onSurface else palette.onSurfaceSecondary.copy(alpha = 0.3f),
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.widgets_resize_height),
                        style = AuroraTypography.bodyMedium,
                        color = palette.onSurface,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = {
                                if (currentH > 1) {
                                    currentH -= 1
                                    onResize(currentW, currentH)
                                }
                            },
                            enabled = currentH > 1,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Remove,
                                contentDescription = stringResource(R.string.widgets_resize_smaller_h),
                                tint = if (currentH > 1) palette.onSurface else palette.onSurfaceSecondary.copy(alpha = 0.3f),
                            )
                        }
                        Text(
                            text = "$currentH",
                            style = AuroraTypography.titleMedium,
                            color = palette.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(
                            onClick = {
                                if (currentH < gridSpec.rows) {
                                    currentH += 1
                                    onResize(currentW, currentH)
                                }
                            },
                            enabled = currentH < gridSpec.rows,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.widgets_resize_larger_h),
                                tint = if (currentH < gridSpec.rows) palette.onSurface else palette.onSurfaceSecondary.copy(alpha = 0.3f),
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = palette.onAccent,
                    ),
                    shape = RoundedCornerShape(AuroraRadii.input),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                ) {
                    Text(
                        text = stringResource(R.string.action_done),
                        style = AuroraTypography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
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
