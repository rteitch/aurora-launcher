package app.aurora.launcher.feature.applibrary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.aurora.launcher.core.data.repository.AppCatalogRepository
import app.aurora.launcher.core.data.repository.LauncherSettingsRepository
import app.aurora.launcher.core.data.repository.WorkspaceRepository
import app.aurora.launcher.core.designsystem.AppIconTile
import app.aurora.launcher.core.designsystem.AuroraRadii
import app.aurora.launcher.core.designsystem.AuroraSpacing
import app.aurora.launcher.core.designsystem.AuroraTheme
import app.aurora.launcher.core.designsystem.AuroraTypography
import app.aurora.launcher.core.designsystem.R
import app.aurora.launcher.core.designsystem.auroraFocusRing
import app.aurora.launcher.core.designsystem.resolveIconShape
import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.AppLibraryClassifier
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.LibraryGroup
import app.aurora.launcher.core.model.PageId
import app.aurora.launcher.core.model.SpotlightSearchEngine
import app.aurora.launcher.core.model.WorkspacePage
import app.aurora.launcher.core.platform.IconCacheAndLoader
import app.aurora.launcher.core.platform.LauncherAppsGateway
import app.aurora.launcher.core.platform.rememberAppIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppLibraryUiState(
    val visibleApps: List<AppEntry> = emptyList(),
    val recentlyAdded: List<AppEntry> = emptyList(),
    val frequentlyUsed: List<AppEntry> = emptyList(),
    val grouped: Map<LibraryGroup, List<AppEntry>> = emptyMap(),
    val alphabeticalSections: List<Pair<String, List<AppEntry>>> = emptyList(),
    val filterQuery: String = "",
    val isPrivateSpaceLocked: Boolean = false,
    val settings: LauncherSettings = LauncherSettings(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppLibraryViewModel @Inject constructor(
    private val appCatalogRepository: AppCatalogRepository,
    private val workspaceRepository: WorkspaceRepository,
    settingsRepository: LauncherSettingsRepository,
    private val launcherAppsGateway: LauncherAppsGateway,
    val iconLoader: IconCacheAndLoader,
) : ViewModel() {

    private val filterFlow = MutableStateFlow("")
    private val isTabletFlow = MutableStateFlow(false)

    fun setTabletViewport(isTablet: Boolean) {
        isTabletFlow.value = isTablet
    }

    val workspacePages: StateFlow<List<WorkspacePage>> = combine(
        settingsRepository.settings,
        isTabletFlow,
    ) { settings, isTablet ->
        val spec = settings.effectiveGridSpec(isTablet)
        workspaceRepository.observeWorkspace(flowOf(spec))
    }.flatMapLatest { it }
     .map { it.pages }
     .stateIn(
         scope = viewModelScope,
         started = SharingStarted.WhileSubscribed(5_000),
         initialValue = emptyList(),
     )

    val uiState: StateFlow<AppLibraryUiState> = combine(
        appCatalogRepository.allApps,
        appCatalogRepository.recentlyAddedApps,
        appCatalogRepository.frequentlyUsedApps,
        settingsRepository.settings,
        filterFlow,
        appCatalogRepository.isPrivateSpaceLocked,
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val allApps = args[0] as List<AppEntry>
        @Suppress("UNCHECKED_CAST")
        val recent = args[1] as List<AppEntry>
        @Suppress("UNCHECKED_CAST")
        val frequent = args[2] as List<AppEntry>
        val settings = args[3] as LauncherSettings
        val filter = args[4] as String
        val isPrivateLocked = args[5] as Boolean

        val visible = allApps.filter { !it.isHidden }
        val filtered = if (filter.isBlank()) {
            visible
        } else {
            val norm = SpotlightSearchEngine.normalizeText(filter)
            visible.filter { SpotlightSearchEngine.scoreMatch(norm, it.label, it.launchCount) > 0 }
        }

        val grouped = LibraryGroup.entries.associateWith { grp ->
            filtered.filter { it.effectiveGroup == grp }
        }.filterValues { it.isNotEmpty() }

        val alphaSections = filtered
            .groupBy { AppLibraryClassifier.sectionInitial(it.label) }
            .entries
            .sortedBy { if (it.key == "#") "ZZZ" else it.key }
            .map { it.key to it.value }

        AppLibraryUiState(
            visibleApps = filtered,
            recentlyAdded = recent.filter { !it.isHidden }.take(8),
            frequentlyUsed = frequent.filter { !it.isHidden }.take(8),
            grouped = grouped,
            alphabeticalSections = alphaSections,
            filterQuery = filter,
            isPrivateSpaceLocked = isPrivateLocked,
            settings = settings,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppLibraryUiState(),
    )

    fun unlockPrivateSpace(): Boolean {
        return launcherAppsGateway.requestUnlockPrivateSpace()
    }

    fun updateFilter(query: String) {
        filterFlow.value = query
    }

    fun addAppToWorkspace(app: AppEntry, isTablet: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val spec = uiState.value.settings.effectiveGridSpec(isTablet)
            val added = workspaceRepository.addAppToHome(app.key, spec)
            onResult(added)
        }
    }

    fun addAppToSpecificPage(
        app: AppEntry,
        pageId: PageId,
        isTablet: Boolean,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val spec = uiState.value.settings.effectiveGridSpec(isTablet)
            val added = workspaceRepository.addAppToSpecificPage(app.key, pageId, spec)
            onResult(added)
        }
    }

    fun addAppToNewPage(
        app: AppEntry,
        isTablet: Boolean,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val spec = uiState.value.settings.effectiveGridSpec(isTablet)
            val newPageId = workspaceRepository.addPage(spec)
            val added = workspaceRepository.addAppToSpecificPage(app.key, newPageId, spec)
            onResult(added)
        }
    }

    fun setAppHidden(key: ComponentKey, hidden: Boolean) {
        viewModelScope.launch {
            appCatalogRepository.setAppHidden(key, hidden)
        }
    }

    fun setAppLocked(key: ComponentKey, locked: Boolean) {
        viewModelScope.launch {
            appCatalogRepository.setAppLocked(key, locked)
        }
    }

    fun setCategoryOverride(key: ComponentKey, group: LibraryGroup?) {
        viewModelScope.launch {
            appCatalogRepository.setCategoryOverride(key, group)
        }
    }

    fun openAppInfo(key: ComponentKey) {
        launcherAppsGateway.openAppInfo(key)
    }

    fun uninstallApp(key: ComponentKey) {
        launcherAppsGateway.requestUninstall(key)
    }
}

@Composable
fun AppLibraryPage(
    isTabletViewport: Boolean,
    badgeCounts: Map<String, Int>,
    onLaunchApp: (ComponentKey) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val workspacePages by viewModel.workspacePages.collectAsStateWithLifecycle()
    val palette = AuroraTheme.palette

    LaunchedEffect(isTabletViewport) {
        viewModel.setTabletViewport(isTabletViewport)
    }

    var showAlphabetical by rememberSaveable { mutableStateOf(false) }
    var expandedCluster by remember { mutableStateOf<Pair<String, List<AppEntry>>?>(null) }
    var actionTargetApp by remember { mutableStateOf<AppEntry?>(null) }
    var pageSelectTargetApp by remember { mutableStateOf<AppEntry?>(null) }
    var categoryPickerTarget by remember { mutableStateOf<AppEntry?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val safeLaunchApp: (ComponentKey) -> Unit = { key ->
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        onLaunchApp(key)
    }

    when {
        pageSelectTargetApp != null -> BackHandler { pageSelectTargetApp = null }
        actionTargetApp != null -> BackHandler { actionTargetApp = null }
        categoryPickerTarget != null -> BackHandler { categoryPickerTarget = null }
        expandedCluster != null -> BackHandler { expandedCluster = null }
        state.filterQuery.isNotEmpty() -> BackHandler {
            viewModel.updateFilter("")
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
        showAlphabetical -> BackHandler {
            showAlphabetical = false
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    val addedMsg = stringResource(R.string.home_grid_added_notice)
    val fullMsg = stringResource(R.string.home_grid_full_notice)

    // Solid high-contrast surfaces per DESIGN.md and antislop R-10 (glass reserved for Dock and active modal overlays).
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background.copy(alpha = 0.94f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = AuroraSpacing.md, vertical = AuroraSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.library_title),
                style = AuroraTypography.headlineMedium,
                color = palette.onBackground,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.xs),
                modifier = Modifier
                    .clip(RoundedCornerShape(AuroraRadii.input))
                    .background(palette.surfaceVariant)
                    .padding(2.dp),
            ) {
                LibraryModeTab(
                    label = stringResource(R.string.library_view_categories),
                    selected = !showAlphabetical,
                    onClick = { showAlphabetical = false },
                )
                LibraryModeTab(
                    label = stringResource(R.string.library_view_alphabetical),
                    selected = showAlphabetical,
                    onClick = { showAlphabetical = true },
                )
            }
        }

        OutlinedTextField(
            value = state.filterQuery,
            onValueChange = viewModel::updateFilter,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.library_search_placeholder),
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
                if (state.filterQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            viewModel.updateFilter("")
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = stringResource(R.string.action_clear),
                        )
                    }
                }
            },
            shape = RoundedCornerShape(AuroraRadii.input),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.visibleApps.isEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AuroraRadii.card))
                    .background(palette.surface)
                    .border(1.dp, palette.outline.copy(alpha = 0.25f), RoundedCornerShape(AuroraRadii.card))
                    .padding(AuroraSpacing.lg),
            ) {
                Text(
                    text = stringResource(R.string.library_empty_title),
                    style = AuroraTypography.titleMedium,
                    color = palette.onSurface,
                )
                Text(
                    text = stringResource(R.string.library_empty_body),
                    style = AuroraTypography.bodyMedium,
                    color = palette.onSurfaceSecondary,
                )
            }
        } else if (!showAlphabetical && state.filterQuery.isBlank()) {
            val cards = buildList<Pair<String, List<AppEntry>>> {
                if (state.recentlyAdded.isNotEmpty()) {
                    add(stringResource(R.string.library_group_recently_added) to state.recentlyAdded)
                }
                if (state.frequentlyUsed.isNotEmpty()) {
                    add(stringResource(R.string.library_group_frequently_used) to state.frequentlyUsed)
                }
                for ((group, apps) in state.grouped) {
                    add(localizedGroupTitle(group) to apps)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isTabletViewport) 3 else 2),
                contentPadding = PaddingValues(bottom = AuroraSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(cards, key = { it.first }) { (title, apps) ->
                    CategoryClusterCard(
                        title = title,
                        apps = apps,
                        settings = state.settings,
                        iconLoader = viewModel.iconLoader,
                        onLaunchApp = safeLaunchApp,
                        onAppLongPress = { actionTargetApp = it },
                        onExpandCluster = { expandedCluster = title to apps },
                    )
                }

                if (state.isPrivateSpaceLocked) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "private_space_card") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AuroraRadii.card))
                                .background(palette.surfaceVariant)
                                .clickable { viewModel.unlockPrivateSpace() }
                                .padding(AuroraSpacing.md),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = palette.accent,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.private_space_locked_title),
                                    style = AuroraTypography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.private_space_locked_desc),
                                    style = AuroraTypography.bodyMedium,
                                    color = palette.onSurfaceSecondary,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = AuroraSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs),
                modifier = Modifier.fillMaxSize(),
            ) {
                for ((section, apps) in state.alphabeticalSections) {
                    item(key = "sec_$section") {
                        Text(
                            text = section,
                            style = AuroraTypography.titleMedium,
                            color = palette.accent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = AuroraSpacing.sm, bottom = AuroraSpacing.xs),
                        )
                    }
                    items(apps, key = { it.key.asCompactKey() }) { app ->
                        AlphabeticalAppRow(
                            app = app,
                            settings = state.settings,
                            badgeCount = badgeCounts[app.key.packageName] ?: 0,
                            iconLoader = viewModel.iconLoader,
                            onClick = { safeLaunchApp(app.key) },
                            onLongClick = { actionTargetApp = app },
                        )
                    }
                }

                if (state.isPrivateSpaceLocked) {
                    item(key = "private_space_card_alpha") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AuroraRadii.card))
                                .background(palette.surfaceVariant)
                                .clickable { viewModel.unlockPrivateSpace() }
                                .padding(AuroraSpacing.md),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = palette.accent,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.private_space_locked_title),
                                    style = AuroraTypography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.private_space_locked_desc),
                                    style = AuroraTypography.bodyMedium,
                                    color = palette.onSurfaceSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val currentExpanded = expandedCluster
    if (currentExpanded != null) {
        AlertDialog(
            onDismissRequest = { expandedCluster = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(currentExpanded.first)
                    IconButton(onClick = { expandedCluster = null }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                }
            },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                ) {
                    items(currentExpanded.second, key = { it.key.asCompactKey() }) { app ->
                        val bmp = rememberAppIcon(
                            viewModel.iconLoader,
                            app.key,
                            state.settings.selectedIconPackPackage,
                        )
                        AppIconTile(
                            label = app.label,
                            iconBitmap = bmp,
                            iconSize = 54.dp,
                            showLabel = true,
                            iconShape = state.settings.iconShape,
                            monochromeEnabled = state.settings.monochromeIcons,
                            isSuspendedOrPaused = app.isSuspended || app.isQuietModePaused,
                            isLocked = app.isLocked,
                            badgeCount = badgeCounts[app.key.packageName] ?: 0,
                            profileBadge = app.profileBadge,
                            useWallpaperScrimForLabel = false,
                            onClick = {
                                expandedCluster = null
                                safeLaunchApp(app.key)
                            },
                            onLongClick = { actionTargetApp = app },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { expandedCluster = null }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    val currentActionApp = actionTargetApp
    if (currentActionApp != null) {
        AlertDialog(
            onDismissRequest = { actionTargetApp = null },
            title = { Text(currentActionApp.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs)) {
                    ActionSheetRow(stringResource(R.string.quick_action_add_to_home)) {
                        val target = currentActionApp
                        actionTargetApp = null
                        pageSelectTargetApp = target
                    }
                    ActionSheetRow(stringResource(R.string.quick_action_category_override)) {
                        actionTargetApp = null
                        categoryPickerTarget = currentActionApp
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
                    }
                    if (!currentActionApp.isSystemApp) {
                        ActionSheetRow(stringResource(R.string.quick_action_uninstall)) {
                            viewModel.uninstallApp(currentActionApp.key)
                            actionTargetApp = null
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

    val selectingApp = pageSelectTargetApp
    if (selectingApp != null) {
        AlertDialog(
            onDismissRequest = { pageSelectTargetApp = null },
            title = { Text(stringResource(R.string.library_select_page_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs)) {
                    workspacePages.forEachIndexed { idx, page ->
                        ActionSheetRow(
                            label = stringResource(R.string.library_select_page_item, idx + 1),
                        ) {
                            pageSelectTargetApp = null
                            viewModel.addAppToSpecificPage(selectingApp, page.id, isTabletViewport) { ok ->
                                onShowMessage(if (ok) addedMsg else fullMsg)
                            }
                        }
                    }
                    ActionSheetRow(
                        label = stringResource(R.string.library_select_page_new),
                    ) {
                        pageSelectTargetApp = null
                        viewModel.addAppToNewPage(selectingApp, isTabletViewport) { ok ->
                            onShowMessage(if (ok) addedMsg else fullMsg)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pageSelectTargetApp = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val currentCatApp = categoryPickerTarget
    if (currentCatApp != null) {
        AlertDialog(
            onDismissRequest = { categoryPickerTarget = null },
            title = { Text(stringResource(R.string.library_category_picker_title, currentCatApp.label)) },
            text = {
                LazyColumn {
                    item(key = "auto") {
                        ActionSheetRow(stringResource(R.string.library_category_auto)) {
                            viewModel.setCategoryOverride(currentCatApp.key, null)
                            categoryPickerTarget = null
                        }
                    }
                    items(LibraryGroup.entries, key = { it.name }) { grp ->
                        ActionSheetRow(localizedGroupTitle(grp)) {
                            viewModel.setCategoryOverride(currentCatApp.key, grp)
                            categoryPickerTarget = null
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { categoryPickerTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun LibraryModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = AuroraTheme.palette
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .background(if (selected) palette.surface else palette.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = AuroraSpacing.sm, vertical = AuroraSpacing.xs),
    ) {
        Text(
            text = label,
            style = AuroraTypography.labelMedium,
            color = if (selected) palette.accent else palette.onSurfaceSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryClusterCard(
    title: String,
    apps: List<AppEntry>,
    settings: LauncherSettings,
    iconLoader: IconCacheAndLoader,
    onLaunchApp: (ComponentKey) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    onExpandCluster: () -> Unit,
) {
    val palette = AuroraTheme.palette
    val cardShape = RoundedCornerShape(AuroraRadii.card)
    val iconShape = remember(settings.iconShape) { resolveIconShape(settings.iconShape) }
    val showMiniQuadrant = apps.size > 4

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(palette.surface)
            .border(1.dp, palette.outline.copy(alpha = 0.25f), cardShape)
            .clickable(onClick = onExpandCluster)
            .padding(AuroraSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AuroraSpacing.sm)) {
            for (row in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm)) {
                    for (col in 0 until 2) {
                        val slotIndex = row * 2 + col
                        if (slotIndex == 3 && showMiniQuadrant) {
                            val overflowApps = apps.drop(3).take(4)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(iconShape)
                                    .background(palette.surfaceVariant)
                                    .clickable(onClick = onExpandCluster)
                                    .padding(6.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    for (mr in 0 until 2) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            for (mc in 0 until 2) {
                                                val oa = overflowApps.getOrNull(mr * 2 + mc)
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(RoundedCornerShape(4.dp)),
                                                ) {
                                                    if (oa != null) {
                                                        val bmp = rememberAppIcon(
                                                            iconLoader,
                                                            oa.key,
                                                            settings.selectedIconPackPackage,
                                                        )
                                                        if (bmp != null) {
                                                            Image(
                                                                bitmap = bmp,
                                                                contentDescription = null,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize(),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val app = apps.getOrNull(slotIndex)
                            if (app != null) {
                                val bmp = rememberAppIcon(
                                    iconLoader,
                                    app.key,
                                    settings.selectedIconPackPackage,
                                )
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(iconShape)
                                        .background(palette.surfaceVariant)
                                        .combinedClickable(
                                            onClick = { onLaunchApp(app.key) },
                                            onLongClick = { onAppLongPress(app) },
                                        ),
                                ) {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = app.label,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Text(
                                            text = app.label.take(1).uppercase(),
                                            style = AuroraTypography.titleMedium,
                                            color = palette.accent,
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.size(56.dp))
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = title,
            style = AuroraTypography.bodyMedium,
            color = palette.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlphabeticalAppRow(
    app: AppEntry,
    settings: LauncherSettings,
    badgeCount: Int,
    iconLoader: IconCacheAndLoader,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val palette = AuroraTheme.palette
    val bmp = rememberAppIcon(iconLoader, app.key, settings.selectedIconPackPackage)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(AuroraRadii.input))
            .background(palette.surface)
            .auroraFocusRing(RoundedCornerShape(AuroraRadii.input))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = AuroraSpacing.md, vertical = AuroraSpacing.xs),
    ) {
        AppIconTile(
            label = app.label,
            iconBitmap = bmp,
            iconSize = 44.dp,
            showLabel = false,
            iconShape = settings.iconShape,
            monochromeEnabled = settings.monochromeIcons,
            isSuspendedOrPaused = app.isSuspended || app.isQuietModePaused,
            isLocked = app.isLocked,
            badgeCount = badgeCount,
            profileBadge = app.profileBadge,
            useWallpaperScrimForLabel = false,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = AuroraTypography.bodyLarge,
                color = palette.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = localizedGroupTitle(app.effectiveGroup),
                style = AuroraTypography.labelMedium,
                color = palette.onSurfaceSecondary,
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
private fun localizedGroupTitle(group: LibraryGroup): String = when (group) {
    LibraryGroup.SOCIAL -> stringResource(R.string.library_group_social)
    LibraryGroup.PRODUCTIVITY_FINANCE -> stringResource(R.string.library_group_productivity)
    LibraryGroup.ENTERTAINMENT -> stringResource(R.string.library_group_entertainment)
    LibraryGroup.PHOTO_VIDEO -> stringResource(R.string.library_group_photo_video)
    LibraryGroup.UTILITIES -> stringResource(R.string.library_group_utilities)
    LibraryGroup.GAMES -> stringResource(R.string.library_group_games)
    LibraryGroup.SHOPPING -> stringResource(R.string.library_group_shopping)
    LibraryGroup.TRAVEL -> stringResource(R.string.library_group_travel)
    LibraryGroup.OTHER -> stringResource(R.string.library_group_other)
}
