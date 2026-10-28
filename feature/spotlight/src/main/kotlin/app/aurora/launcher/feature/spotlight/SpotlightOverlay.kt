package app.aurora.launcher.feature.spotlight

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.aurora.launcher.core.common.AuroraDispatchers
import app.aurora.launcher.core.data.repository.AppCatalogRepository
import app.aurora.launcher.core.data.repository.LauncherSettingsRepository
import app.aurora.launcher.core.data.repository.SearchHistoryRepository
import app.aurora.launcher.core.data.repository.WorkspaceRepository
import app.aurora.launcher.core.designsystem.AppIconTile
import app.aurora.launcher.core.designsystem.AuroraRadii
import app.aurora.launcher.core.designsystem.AuroraSpacing
import app.aurora.launcher.core.designsystem.AuroraTheme
import app.aurora.launcher.core.designsystem.AuroraTypography
import app.aurora.launcher.core.designsystem.GlassSurface
import app.aurora.launcher.core.designsystem.R
import app.aurora.launcher.core.designsystem.auroraFocusRing
import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.ContactSearchResult
import app.aurora.launcher.core.model.GlassTier
import app.aurora.launcher.core.model.IconShapeOption
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.QuickSettingAction
import app.aurora.launcher.core.model.SpotlightResults
import app.aurora.launcher.core.model.SpotlightSearchEngine
import app.aurora.launcher.core.platform.ContactsAndSettingsSearchGateway
import app.aurora.launcher.core.platform.IconCacheAndLoader
import app.aurora.launcher.core.platform.LauncherAppsGateway
import app.aurora.launcher.core.platform.rememberAppIcon
import kotlin.math.roundToInt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpotlightUiState(
    val rawQuery: String = "",
    val results: SpotlightResults = SpotlightResults("", emptyList(), emptyList(), emptyList(), emptyList()),
    val recentQueries: List<String> = emptyList(),
    val settings: LauncherSettings = LauncherSettings(),
    val isContactsSupported: Boolean = false,
    val hasContactsPermission: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SpotlightViewModel @Inject constructor(
    private val appCatalogRepository: AppCatalogRepository,
    settingsRepository: LauncherSettingsRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val contactsAndSettingsGateway: ContactsAndSettingsSearchGateway,
    val iconLoader: IconCacheAndLoader,
    private val dispatchers: AuroraDispatchers,
    private val workspaceRepository: WorkspaceRepository,
    private val launcherAppsGateway: LauncherAppsGateway,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val contactsPermFlow = MutableStateFlow(contactsAndSettingsGateway.hasContactsPermission())
    private val isContactsDeclared: Boolean by lazy {
        contactsAndSettingsGateway.isContactsPermissionDeclaredInPackage()
    }

    private val debouncedQuery = queryFlow.debounce(90L)

    val uiState: StateFlow<SpotlightUiState> = combine(
        queryFlow,
        debouncedQuery,
        appCatalogRepository.allApps,
        settingsRepository.settings,
        searchHistoryRepository.recentQueries,
    ) { rawQuery, effectiveQuery, apps, settings, history ->
        val contactsSupported = isContactsDeclared
        val hasPerm = contactsSupported && (contactsPermFlow.value || contactsAndSettingsGateway.hasContactsPermission())
        val contacts = if (contactsSupported && settings.includeContactsInSearch && hasPerm && effectiveQuery.isNotBlank()) {
            contactsAndSettingsGateway.searchContacts(effectiveQuery).map {
                ContactSearchResult(
                    id = it.contactId,
                    displayName = it.displayName,
                    lookupUri = it.lookupUri,
                )
            }
        } else {
            emptyList()
        }

        val quickSettings = if (settings.includeSettingsInSearch && effectiveQuery.isNotBlank()) {
            contactsAndSettingsGateway.matchSystemSettings(effectiveQuery).map {
                QuickSettingAction(
                    id = it.id,
                    title = it.id,
                    subtitle = it.settingsAction,
                    keywords = it.keywords,
                )
            }
        } else {
            emptyList()
        }

        val searchResults = SpotlightSearchEngine.search(
            query = effectiveQuery,
            apps = apps,
            quickSettings = quickSettings,
            contacts = contacts,
        )

        SpotlightUiState(
            rawQuery = rawQuery,
            results = searchResults,
            recentQueries = if (settings.saveSearchHistory) history else emptyList(),
            settings = settings,
            isContactsSupported = contactsSupported,
            hasContactsPermission = hasPerm,
        )
    }.flowOn(dispatchers.io)
     .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SpotlightUiState(),
    )

    fun updateQuery(newQuery: String) {
        queryFlow.value = newQuery
    }

    fun refreshPermissions() {
        contactsPermFlow.value = contactsAndSettingsGateway.hasContactsPermission()
    }

    fun commitQueryToHistoryIfEnabled() {
        val current = queryFlow.value.trim()
        if (current.isEmpty() || !uiState.value.settings.saveSearchHistory) return
        viewModelScope.launch {
            searchHistoryRepository.recordQuery(current)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            searchHistoryRepository.clearHistory()
        }
    }

    fun openContact(lookupUri: String) {
        contactsAndSettingsGateway.openContact(lookupUri)
    }

    fun openSettingAction(action: String) {
        contactsAndSettingsGateway.openSystemSetting(action)
    }

    fun addAppToWorkspace(app: AppEntry, isTablet: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val spec = uiState.value.settings.effectiveGridSpec(isTablet)
            val added = workspaceRepository.addAppToHome(app.key, spec)
            onResult(added)
        }
    }

    fun openAppInfo(key: ComponentKey) {
        launcherAppsGateway.openAppInfo(key)
    }

    fun uninstallApp(key: ComponentKey) {
        launcherAppsGateway.requestUninstall(key)
    }

    fun setAppLocked(key: ComponentKey, locked: Boolean) {
        viewModelScope.launch {
            appCatalogRepository.setAppLocked(key, locked)
        }
    }

    fun setAppHidden(key: ComponentKey, hidden: Boolean) {
        viewModelScope.launch {
            appCatalogRepository.setAppHidden(key, hidden)
        }
    }
}

@Composable
fun SpotlightOverlay(
    visible: Boolean,
    glassTier: GlassTier,
    badgeCounts: Map<String, Int>,
    onDismiss: () -> Unit,
    onLaunchApp: (ComponentKey) -> Unit,
    onShowMessage: (String) -> Unit = {},
    viewModel: SpotlightViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = AuroraTheme.palette
    val reduceMotion = AuroraTheme.reduceMotion
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val haptics = LocalHapticFeedback.current
    val addedNotice = stringResource(R.string.home_grid_added_notice)
    val fullNotice = stringResource(R.string.home_grid_full_notice)
    var actionTargetApp by remember { mutableStateOf<AppEntry?>(null) }
    var uninstallConfirmTarget by remember { mutableStateOf<AppEntry?>(null) }

    val dismissWithFocusClear: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        actionTargetApp = null
        uninstallConfirmTarget = null
        onDismiss()
    }

    val contactsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissions()
    }

    LaunchedEffect(visible) {
        if (visible) {
            viewModel.refreshPermissions()
            runCatching { focusRequester.requestFocus() }
        } else {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            actionTargetApp = null
            uninstallConfirmTarget = null
            viewModel.updateQuery("")
        }
    }

    if (visible) {
        BackHandler(onBack = dismissWithFocusClear)
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) fadeIn(tween(80)) else fadeIn(tween(150)) + slideInVertically(tween(170)) { -it / 6 },
        exit = if (reduceMotion) fadeOut(tween(80)) else fadeOut(tween(120)) + slideOutVertically(tween(130)) { -it / 6 },
    ) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.54f))
                .statusBarsPadding()
                .imePadding()
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyUp && ev.key == Key.Escape) {
                        dismissWithFocusClear()
                        true
                    } else {
                        false
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = dismissWithFocusClear,
                )
                .padding(horizontal = AuroraSpacing.md, vertical = AuroraSpacing.md),
        ) {
            GlassSurface(
                tier = glassTier,
                cornerRadius = AuroraRadii.folder,
                modifier = Modifier
                    .widthIn(max = 560.dp)
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
                        .background(palette.surface.copy(alpha = if (glassTier == GlassTier.TIER_C_SOLID) 0.98f else 0.90f))
                        .padding(AuroraSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.xs),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = state.rawQuery,
                            onValueChange = viewModel::updateQuery,
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.spotlight_placeholder),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = palette.accent,
                                )
                            },
                            trailingIcon = {
                                if (state.rawQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateQuery("") }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Clear,
                                            contentDescription = stringResource(R.string.action_clear),
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    val topApp = state.results.matchingApps.firstOrNull()
                                    if (topApp != null) {
                                        viewModel.commitQueryToHistoryIfEnabled()
                                        dismissWithFocusClear()
                                        onLaunchApp(topApp.key)
                                    }
                                },
                            ),
                            shape = RoundedCornerShape(AuroraRadii.input),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                        )

                        IconButton(
                            onClick = dismissWithFocusClear,
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

                    val iconDp: Dp = state.settings.iconSizeDp.coerceIn(52, 64).dp
                    val iconShape: IconShapeOption = state.settings.iconShape
                    val pack = state.settings.selectedIconPackPackage

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = AuroraSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.rawQuery.isBlank()) {
                            if (state.results.suggestedApps.isNotEmpty()) {
                                item(key = "header_suggested") {
                                    Text(
                                        text = stringResource(R.string.spotlight_section_suggested),
                                        style = AuroraTypography.titleMedium,
                                        color = palette.onSurfaceSecondary,
                                    )
                                }
                                item(key = "grid_suggested") {
                                    val chunked = state.results.suggestedApps.chunked(4)
                                    Column(verticalArrangement = Arrangement.spacedBy(AuroraSpacing.sm)) {
                                        chunked.forEach { rowApps ->
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                rowApps.forEach { app ->
                                                    val bmp = rememberAppIcon(viewModel.iconLoader, app.key, pack)
                                                    AppIconTile(
                                                        label = app.label,
                                                        iconBitmap = bmp,
                                                        iconSize = iconDp,
                                                        showLabel = true,
                                                        iconShape = iconShape,
                                                        monochromeEnabled = state.settings.monochromeIcons,
                                                        isSuspendedOrPaused = app.isSuspended || app.isQuietModePaused,
                                                        isLocked = app.isLocked,
                                                        badgeCount = badgeCounts[app.key.packageName] ?: 0,
                                                        profileBadge = app.profileBadge,
                                                        useWallpaperScrimForLabel = false,
                                                        onClick = {
                                                            dismissWithFocusClear()
                                                            onLaunchApp(app.key)
                                                        },
                                                        onLongClick = {
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            actionTargetApp = app
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                }
                                                repeat(4 - rowApps.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (state.recentQueries.isNotEmpty()) {
                                item(key = "header_history") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.spotlight_section_history),
                                            style = AuroraTypography.titleMedium,
                                            color = palette.onSurfaceSecondary,
                                        )
                                        TextButton(onClick = viewModel::clearHistory) {
                                            Text(
                                                text = stringResource(R.string.spotlight_clear_history),
                                                color = palette.accent,
                                            )
                                        }
                                    }
                                }
                                items(state.recentQueries, key = { "hist_$it" }) { q ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget)
                                            .clip(RoundedCornerShape(AuroraRadii.input))
                                            .clickable { viewModel.updateQuery(q) }
                                            .padding(horizontal = AuroraSpacing.sm, vertical = AuroraSpacing.xs),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.History,
                                            contentDescription = null,
                                            tint = palette.onSurfaceSecondary,
                                        )
                                        Text(
                                            text = q,
                                            style = AuroraTypography.bodyLarge,
                                            color = palette.onSurface,
                                        )
                                    }
                                }
                            }
                        } else {
                            if (state.isContactsSupported && state.settings.includeContactsInSearch && !state.hasContactsPermission) {
                                item(key = "contacts_perm_cta") {
                                    OutlinedButton(
                                        onClick = { contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS) },
                                        shape = RoundedCornerShape(AuroraRadii.input),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.spotlight_contacts_permission_cta),
                                            color = palette.accent,
                                        )
                                    }
                                }
                            }

                            val hasAnyMatch = state.results.matchingApps.isNotEmpty() ||
                                state.results.matchingSettings.isNotEmpty() ||
                                state.results.matchingContacts.isNotEmpty()

                            if (!hasAnyMatch) {
                                item(key = "empty_search") {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = AuroraSpacing.lg, horizontal = AuroraSpacing.sm),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.spotlight_empty_title, state.rawQuery.trim()),
                                            style = AuroraTypography.titleMedium,
                                            color = palette.onSurface,
                                        )
                                        Text(
                                            text = stringResource(R.string.spotlight_empty_body),
                                            style = AuroraTypography.bodyMedium,
                                            color = palette.onSurfaceSecondary,
                                        )
                                    }
                                }
                            }

                            if (state.results.matchingApps.isNotEmpty()) {
                                item(key = "header_apps") {
                                    Text(
                                        text = stringResource(R.string.spotlight_section_apps),
                                        style = AuroraTypography.titleMedium,
                                        color = palette.onSurfaceSecondary,
                                    )
                                }
                                items(state.results.matchingApps.take(12), key = { it.key.asCompactKey() }) { app ->
                                    val bmp = rememberAppIcon(viewModel.iconLoader, app.key, pack)
                                    SwipeableAppSearchResultRow(
                                        app = app,
                                        iconBitmap = bmp,
                                        iconShape = iconShape,
                                        monochromeEnabled = state.settings.monochromeIcons,
                                        badgeCount = badgeCounts[app.key.packageName] ?: 0,
                                        onLaunch = {
                                            viewModel.commitQueryToHistoryIfEnabled()
                                            dismissWithFocusClear()
                                            onLaunchApp(app.key)
                                        },
                                        onLongClick = {
                                            actionTargetApp = app
                                        },
                                        onQuickAction = { action ->
                                            when (action) {
                                                SpotlightQuickAction.ADD_TO_HOME -> {
                                                    viewModel.addAppToWorkspace(app, isTablet) { added ->
                                                        onShowMessage(if (added) addedNotice else fullNotice)
                                                    }
                                                }
                                                SpotlightQuickAction.APP_INFO -> {
                                                    dismissWithFocusClear()
                                                    viewModel.openAppInfo(app.key)
                                                }
                                                SpotlightQuickAction.UNINSTALL -> {
                                                    uninstallConfirmTarget = app
                                                }
                                            }
                                        },
                                    )
                                }
                            }

                            if (state.results.matchingSettings.isNotEmpty()) {
                                item(key = "header_settings") {
                                    Text(
                                        text = stringResource(R.string.spotlight_section_settings),
                                        style = AuroraTypography.titleMedium,
                                        color = palette.onSurfaceSecondary,
                                    )
                                }
                                items(state.results.matchingSettings, key = { "set_${it.id}" }) { setting ->
                                    val localizedLabel = localizedSettingTitle(setting.id)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget)
                                            .clip(RoundedCornerShape(AuroraRadii.input))
                                            .auroraFocusRing(RoundedCornerShape(AuroraRadii.input))
                                            .clickable {
                                                viewModel.commitQueryToHistoryIfEnabled()
                                                viewModel.openSettingAction(setting.subtitle)
                                                dismissWithFocusClear()
                                            }
                                            .padding(horizontal = AuroraSpacing.sm, vertical = AuroraSpacing.xs),
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(palette.surfaceVariant),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Settings,
                                                contentDescription = null,
                                                tint = palette.accent,
                                            )
                                        }
                                        Text(
                                            text = localizedLabel,
                                            style = AuroraTypography.bodyLarge,
                                            color = palette.onSurface,
                                        )
                                    }
                                }
                            }

                            if (state.results.matchingContacts.isNotEmpty()) {
                                item(key = "header_contacts") {
                                    Text(
                                        text = stringResource(R.string.spotlight_section_contacts),
                                        style = AuroraTypography.titleMedium,
                                        color = palette.onSurfaceSecondary,
                                    )
                                }
                                items(state.results.matchingContacts, key = { "cnt_${it.id}" }) { contact ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget)
                                            .clip(RoundedCornerShape(AuroraRadii.input))
                                            .border(1.dp, palette.outline.copy(alpha = 0.2f), RoundedCornerShape(AuroraRadii.input))
                                            .clickable {
                                                viewModel.commitQueryToHistoryIfEnabled()
                                                viewModel.openContact(contact.lookupUri)
                                                dismissWithFocusClear()
                                            }
                                            .padding(horizontal = AuroraSpacing.sm, vertical = AuroraSpacing.xs),
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(palette.surfaceVariant),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.PersonOutline,
                                                contentDescription = null,
                                                tint = palette.accent,
                                            )
                                        }
                                        Text(
                                            text = contact.displayName,
                                            style = AuroraTypography.bodyLarge,
                                            color = palette.onSurface,
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

    val currentActionApp = actionTargetApp
    if (currentActionApp != null) {
        AlertDialog(
            onDismissRequest = { actionTargetApp = null },
            title = { Text(currentActionApp.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs)) {
                    ActionSheetRow(stringResource(R.string.quick_action_add_to_home)) {
                        viewModel.addAppToWorkspace(currentActionApp, isTablet) { added ->
                            onShowMessage(if (added) addedNotice else fullNotice)
                        }
                        actionTargetApp = null
                    }
                    ActionSheetRow(
                        if (currentActionApp.isLocked) {
                            stringResource(R.string.quick_action_unlock_app)
                        } else {
                            stringResource(R.string.quick_action_lock_app)
                        },
                    ) {
                        viewModel.setAppLocked(currentActionApp.key, !currentActionApp.isLocked)
                        actionTargetApp = null
                    }
                    ActionSheetRow(stringResource(R.string.quick_action_hide_app)) {
                        viewModel.setAppHidden(currentActionApp.key, true)
                        actionTargetApp = null
                    }
                    ActionSheetRow(stringResource(R.string.quick_action_app_info)) {
                        viewModel.openAppInfo(currentActionApp.key)
                        actionTargetApp = null
                        dismissWithFocusClear()
                    }
                    if (!currentActionApp.isSystemApp) {
                        ActionSheetRow(stringResource(R.string.quick_action_uninstall)) {
                            val target = currentActionApp
                            actionTargetApp = null
                            uninstallConfirmTarget = target
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTargetApp = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val confirmingApp = uninstallConfirmTarget
    if (confirmingApp != null) {
        AlertDialog(
            onDismissRequest = { uninstallConfirmTarget = null },
            title = {
                Text(stringResource(R.string.quick_action_uninstall_confirm_title, confirmingApp.label))
            },
            text = {
                Text(stringResource(R.string.quick_action_uninstall_confirm_body, confirmingApp.label))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val key = confirmingApp.key
                        uninstallConfirmTarget = null
                        viewModel.uninstallApp(key)
                        dismissWithFocusClear()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.quick_action_uninstall),
                        color = palette.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallConfirmTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private enum class SpotlightQuickAction {
    ADD_TO_HOME,
    APP_INFO,
    UNINSTALL,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableAppSearchResultRow(
    app: AppEntry,
    iconBitmap: ImageBitmap?,
    iconShape: IconShapeOption,
    monochromeEnabled: Boolean,
    badgeCount: Int,
    onLaunch: () -> Unit,
    onLongClick: () -> Unit,
    onQuickAction: (SpotlightQuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AuroraTheme.palette
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    val actionButtonsWidthDp = if (!app.isSystemApp) 168.dp else 112.dp
    val maxSwipePx = with(density) { -actionButtonsWidthDp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AuroraRadii.input)),
    ) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = AuroraSpacing.xs),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    coroutineScope.launch { offsetX.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
                    onQuickAction(SpotlightQuickAction.ADD_TO_HOME)
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(palette.accent.copy(alpha = 0.15f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.quick_action_add_to_home),
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(AuroraSpacing.xs))
            IconButton(
                onClick = {
                    coroutineScope.launch { offsetX.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
                    onQuickAction(SpotlightQuickAction.APP_INFO)
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(palette.surfaceVariant, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.quick_action_app_info),
                    tint = palette.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (!app.isSystemApp) {
                Spacer(modifier = Modifier.width(AuroraSpacing.xs))
                IconButton(
                    onClick = {
                        coroutineScope.launch { offsetX.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
                        onQuickAction(SpotlightQuickAction.UNINSTALL)
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(palette.error.copy(alpha = 0.15f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.quick_action_uninstall),
                        tint = palette.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(palette.surface)
                .defaultMinSize(minHeight = 56.dp)
                .auroraFocusRing(RoundedCornerShape(AuroraRadii.input))
                .pointerInput(app.key) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                val target = if (offsetX.value < maxSwipePx / 2) maxSwipePx else 0f
                                offsetX.animateTo(target, tween(200, easing = FastOutSlowInEasing))
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(maxSwipePx, 0f)
                                offsetX.snapTo(newOffset)
                            }
                        },
                    )
                }
                .combinedClickable(
                    onClick = {
                        if (offsetX.value < -10f) {
                            coroutineScope.launch { offsetX.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
                        } else {
                            onLaunch()
                        }
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                )
                .padding(horizontal = AuroraSpacing.sm, vertical = AuroraSpacing.xs),
        ) {
            AppIconTile(
                label = app.label,
                iconBitmap = iconBitmap,
                iconSize = 44.dp,
                showLabel = false,
                iconShape = iconShape,
                monochromeEnabled = monochromeEnabled,
                isSuspendedOrPaused = app.isSuspended || app.isQuietModePaused,
                isLocked = app.isLocked,
                badgeCount = badgeCount,
                profileBadge = app.profileBadge,
                useWallpaperScrimForLabel = false,
                onClick = {
                    if (offsetX.value < -10f) {
                        coroutineScope.launch { offsetX.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
                    } else {
                        onLaunch()
                    }
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            Text(
                text = app.label,
                style = AuroraTypography.bodyLarge,
                color = palette.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ActionSheetRow(
    label: String,
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
            color = palette.onSurface,
        )
    }
}

@Composable
private fun localizedSettingTitle(id: String): String = when (id) {
    "wifi" -> stringResource(R.string.spotlight_setting_wifi)
    "bluetooth" -> stringResource(R.string.spotlight_setting_bluetooth)
    "display" -> stringResource(R.string.spotlight_setting_display)
    "sound" -> stringResource(R.string.spotlight_setting_sound)
    "battery" -> stringResource(R.string.spotlight_setting_battery)
    "storage" -> stringResource(R.string.spotlight_setting_storage)
    "apps" -> stringResource(R.string.spotlight_setting_apps)
    "security" -> stringResource(R.string.spotlight_setting_security)
    else -> id
}
