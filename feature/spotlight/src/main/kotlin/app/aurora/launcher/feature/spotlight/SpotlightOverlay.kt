package app.aurora.launcher.feature.spotlight

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.aurora.launcher.core.data.repository.AppCatalogRepository
import app.aurora.launcher.core.data.repository.LauncherSettingsRepository
import app.aurora.launcher.core.data.repository.SearchHistoryRepository
import app.aurora.launcher.core.designsystem.AppIconTile
import app.aurora.launcher.core.designsystem.AuroraRadii
import app.aurora.launcher.core.designsystem.AuroraSpacing
import app.aurora.launcher.core.designsystem.AuroraTheme
import app.aurora.launcher.core.designsystem.AuroraTypography
import app.aurora.launcher.core.designsystem.GlassSurface
import app.aurora.launcher.core.designsystem.R
import app.aurora.launcher.core.designsystem.auroraFocusRing
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
import app.aurora.launcher.core.platform.rememberAppIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
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
    appCatalogRepository: AppCatalogRepository,
    settingsRepository: LauncherSettingsRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val contactsAndSettingsGateway: ContactsAndSettingsSearchGateway,
    val iconLoader: IconCacheAndLoader,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val contactsPermFlow = MutableStateFlow(contactsAndSettingsGateway.hasContactsPermission())

    private val debouncedQuery = queryFlow.debounce(90L)

    val uiState: StateFlow<SpotlightUiState> = combine(
        queryFlow,
        debouncedQuery,
        appCatalogRepository.allApps,
        settingsRepository.settings,
        searchHistoryRepository.recentQueries,
    ) { rawQuery, effectiveQuery, apps, settings, history ->
        val contactsSupported = contactsAndSettingsGateway.isContactsPermissionDeclaredInPackage()
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
    }.stateIn(
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
}

@Composable
fun SpotlightOverlay(
    visible: Boolean,
    glassTier: GlassTier,
    badgeCounts: Map<String, Int>,
    onDismiss: () -> Unit,
    onLaunchApp: (ComponentKey) -> Unit,
    viewModel: SpotlightViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = AuroraTheme.palette
    val reduceMotion = AuroraTheme.reduceMotion
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val dismissWithFocusClear: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 56.dp)
                                            .clip(RoundedCornerShape(AuroraRadii.input))
                                            .auroraFocusRing(RoundedCornerShape(AuroraRadii.input))
                                            .clickable {
                                                viewModel.commitQueryToHistoryIfEnabled()
                                                dismissWithFocusClear()
                                                onLaunchApp(app.key)
                                            }
                                            .padding(horizontal = AuroraSpacing.sm, vertical = AuroraSpacing.xs),
                                    ) {
                                        AppIconTile(
                                            label = app.label,
                                            iconBitmap = bmp,
                                            iconSize = 44.dp,
                                            showLabel = false,
                                            iconShape = iconShape,
                                            monochromeEnabled = state.settings.monochromeIcons,
                                            isSuspendedOrPaused = app.isSuspended || app.isQuietModePaused,
                                            isLocked = app.isLocked,
                                            badgeCount = badgeCounts[app.key.packageName] ?: 0,
                                            profileBadge = app.profileBadge,
                                            useWallpaperScrimForLabel = false,
                                            onClick = {
                                                viewModel.commitQueryToHistoryIfEnabled()
                                                dismissWithFocusClear()
                                                onLaunchApp(app.key)
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
