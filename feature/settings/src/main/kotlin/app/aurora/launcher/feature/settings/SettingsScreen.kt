package app.aurora.launcher.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.aurora.launcher.core.common.AuroraDispatchers
import app.aurora.launcher.core.common.SystemResult
import app.aurora.launcher.core.common.safeSystemCall
import app.aurora.launcher.core.data.repository.AppCatalogRepository
import app.aurora.launcher.core.data.repository.BackupRepository
import app.aurora.launcher.core.data.repository.LauncherSettingsRepository
import app.aurora.launcher.core.data.repository.SnapshotSummary
import app.aurora.launcher.core.data.repository.WorkspaceRepository
import app.aurora.launcher.core.designsystem.AuroraIsometricLogo
import app.aurora.launcher.core.designsystem.AuroraRadii
import app.aurora.launcher.core.designsystem.AuroraSpacing
import app.aurora.launcher.core.designsystem.AuroraTheme
import app.aurora.launcher.core.designsystem.AuroraTypography
import app.aurora.launcher.core.designsystem.R
import app.aurora.launcher.core.designsystem.auroraFocusRing
import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.GlassIntensity
import app.aurora.launcher.core.model.GlassTier
import app.aurora.launcher.core.model.GridPreset
import app.aurora.launcher.core.model.IconShapeOption
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.ThemeMode
import app.aurora.launcher.core.model.WallpaperStyle
import app.aurora.launcher.core.platform.AppWidgetHostManager
import app.aurora.launcher.core.platform.BadgeServiceCapability
import app.aurora.launcher.core.platform.BiometricAuthGateway
import app.aurora.launcher.core.platform.ContactsAndSettingsSearchGateway
import app.aurora.launcher.core.platform.IconCacheAndLoader
import app.aurora.launcher.core.platform.IconPackCandidate
import app.aurora.launcher.core.platform.IconPackGateway
import app.aurora.launcher.core.platform.NotificationBadgeGateway
import app.aurora.launcher.core.platform.RoleGateway
import app.aurora.launcher.core.platform.WallpaperGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlin.math.roundToInt

@Serializable
data object SettingsHomeRoute

@Serializable
data object HiddenAppsRoute

@Serializable
data object PrivacyPolicyRoute

@Serializable
data object OpenSourceLicensesRoute

data class SettingsUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val hiddenApps: List<AppEntry> = emptyList(),
    val snapshots: List<SnapshotSummary> = emptyList(),
    val iconPacks: List<IconPackCandidate> = emptyList(),
    val activeGlassTier: GlassTier = GlassTier.TIER_C_SOLID,
    val badgeCapability: BadgeServiceCapability = BadgeServiceCapability.UNSUPPORTED_IN_BUILD,
    val isContactsSearchSupported: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val statusBanner: String? = null,
    val canUndoLastGridChange: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context? = null,
    private val settingsRepository: LauncherSettingsRepository,
    private val appCatalogRepository: AppCatalogRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val backupRepository: BackupRepository,
    private val wallpaperGateway: WallpaperGateway,
    private val roleGateway: RoleGateway,
    private val iconPackGateway: IconPackGateway,
    private val iconLoader: IconCacheAndLoader,
    private val badgeGateway: NotificationBadgeGateway,
    private val contactsAndSettingsGateway: ContactsAndSettingsSearchGateway,
    private val biometricGateway: BiometricAuthGateway,
    private val widgetHostManager: AppWidgetHostManager,
    private val dispatchers: AuroraDispatchers,
) : ViewModel() {

    private data class ExternalPlatformCapabilities(
        val iconPacks: List<IconPackCandidate>,
        val badgeCapability: BadgeServiceCapability,
        val isContactsSupported: Boolean,
    )

    private val externalCapabilitiesFlow = MutableStateFlow(
        ExternalPlatformCapabilities(
            iconPacks = emptyList(),
            badgeCapability = badgeGateway.readCapability(),
            isContactsSupported = contactsAndSettingsGateway.isContactsPermissionDeclaredInPackage(),
        ),
    )
    private val bannerFlow = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        appCatalogRepository.allApps,
        workspaceRepository.snapshots,
        externalCapabilitiesFlow,
        bannerFlow,
    ) { settings, apps, snapshots, capabilities, (banner, canUndo) ->
        val signals = wallpaperGateway.readEnvironmentSignals()
        val resolvedTier = settings.resolveGlassTier(
            sdkInt = signals.sdkInt,
            isLowRamDevice = signals.isLowRamDevice,
            isPowerSaveMode = signals.isPowerSaveMode,
            isLiveWallpaperActive = signals.isLiveWallpaperActive,
        )
        SettingsUiState(
            settings = settings,
            hiddenApps = apps.filter { it.isHidden },
            snapshots = snapshots,
            iconPacks = capabilities.iconPacks,
            activeGlassTier = resolvedTier,
            badgeCapability = capabilities.badgeCapability,
            isContactsSearchSupported = capabilities.isContactsSupported,
            isBiometricAvailable = biometricGateway.isAuthenticationAvailable(),
            statusBanner = banner,
            canUndoLastGridChange = canUndo,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun refreshExternalState() {
        viewModelScope.launch {
            val packs = iconPackGateway.discoverIconPacks()
            externalCapabilitiesFlow.value = ExternalPlatformCapabilities(
                iconPacks = packs,
                badgeCapability = badgeGateway.readCapability(),
                isContactsSupported = contactsAndSettingsGateway.isContactsPermissionDeclaredInPackage(),
            )
        }
    }

    fun dismissBanner() {
        bannerFlow.value = null to false
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(transform)
        }
    }

    fun selectIconPack(packPackage: String?) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(selectedIconPackPackage = packPackage) }
            iconLoader.invalidateAll()
        }
    }

    fun changeGridPreset(newPreset: GridPreset, isTablet: Boolean, noticeMessage: String) {
        viewModelScope.launch {
            val current = uiState.value.settings
            if (current.gridPreset == newPreset) return@launch
            val oldSpec = current.effectiveGridSpec(isTablet)
            val updated = current.copy(gridPreset = newPreset)
            val newSpec = updated.effectiveGridSpec(isTablet)
            workspaceRepository.reflowToGrid(oldSpec, newSpec)
            settingsRepository.updateSettings { it.copy(gridPreset = newPreset) }
            bannerFlow.value = noticeMessage to true
        }
    }

    fun undoLatestSnapshot() {
        viewModelScope.launch {
            workspaceRepository.restoreLatestSnapshot()
            bannerFlow.value = null to false
        }
    }

    fun restoreSnapshot(snapshotId: Long) {
        viewModelScope.launch {
            workspaceRepository.restoreSnapshotById(snapshotId)
        }
    }

    fun pickCustomWallpaper(uri: Uri) {
        viewModelScope.launch {
            when (val res = wallpaperGateway.copyCustomWallpaperFromUri(uri)) {
                is SystemResult.Success -> {
                    settingsRepository.updateSettings {
                        it.copy(
                            wallpaperStyle = WallpaperStyle.CUSTOM_IMAGE,
                            customWallpaperPath = res.data,
                        )
                    }
                }
                is SystemResult.Failure -> {
                    bannerFlow.value = res.reason to false
                }
            }
        }
    }

    fun unhideApp(key: ComponentKey) {
        viewModelScope.launch {
            appCatalogRepository.setAppHidden(key, false)
        }
    }

    fun openNotificationAccessSettings() {
        badgeGateway.openNotificationListenerSettings()
    }

    fun openDefaultLauncherSettings() {
        roleGateway.openHomeLauncherChooser()
    }

    fun exportBackupToUri(uri: Uri, successMsg: String) {
        viewModelScope.launch {
            val json = backupRepository.exportBackupJson(uiState.value.settings)
            val writeRes = withContext(dispatchers.io) {
                val ctx = context ?: return@withContext SystemResult.Failure("No context available")
                safeSystemCall("exportBackup") {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Cannot open destination URI")
                }
            }
            bannerFlow.value = when (writeRes) {
                is SystemResult.Success -> successMsg to false
                is SystemResult.Failure -> writeRes.reason to false
            }
        }
    }

    fun importBackupFromUri(
        uri: Uri,
        onSuccessFormat: (Int) -> String,
        onErrorFormat: (String) -> String,
    ) {
        viewModelScope.launch {
            val rawRes = withContext(dispatchers.io) {
                val ctx = context ?: return@withContext SystemResult.Failure("No context available")
                safeSystemCall("importBackup") {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        val maxBytes = 5 * 1024 * 1024 + 1024
                        val buffer = ByteArray(16 * 1024)
                        val out = java.io.ByteArrayOutputStream()
                        var total = 0
                        while (true) {
                            val r = input.read(buffer)
                            if (r <= 0) break
                            total += r
                            require(total <= maxBytes) { "Backup file exceeds 5 MB limit" }
                            out.write(buffer, 0, r)
                        }
                        out.toString(Charsets.UTF_8.name())
                    } ?: throw IllegalStateException("Cannot read selected backup file")
                }
            }
            when (rawRes) {
                is SystemResult.Failure -> {
                    bannerFlow.value = onErrorFormat(rawRes.reason) to false
                }
                is SystemResult.Success -> {
                    when (val imported = backupRepository.importBackupJson(rawRes.data)) {
                        is SystemResult.Success -> {
                            bannerFlow.value = onSuccessFormat(imported.data) to false
                        }
                        is SystemResult.Failure -> {
                            bannerFlow.value = onErrorFormat(imported.reason) to false
                        }
                    }
                }
            }
        }
    }

    fun wipeAllLocalData(onDone: () -> Unit) {
        viewModelScope.launch {
            val deletedWidgetIds = backupRepository.wipeAllLocalData()
            deletedWidgetIds.forEach { widgetHostManager.deleteWidgetId(it.value) }
            iconLoader.invalidateAll()
            onDone()
        }
    }
}

@Composable
fun SettingsOverlay(
    visible: Boolean,
    isTabletViewport: Boolean,
    versionName: String,
    onClose: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    if (!visible) return

    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshExternalState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    NavHost(
        navController = navController,
        startDestination = SettingsHomeRoute,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable<SettingsHomeRoute> {
            BackHandler(onBack = onClose)
            SettingsMainContent(
                state = state,
                isTabletViewport = isTabletViewport,
                versionName = versionName,
                viewModel = viewModel,
                onClose = onClose,
                onOpenHiddenApps = { navController.navigate(HiddenAppsRoute) },
                onOpenPrivacyPolicy = { navController.navigate(PrivacyPolicyRoute) },
                onOpenLicenses = { navController.navigate(OpenSourceLicensesRoute) },
            )
        }
        composable<HiddenAppsRoute> {
            HiddenAppsSubScreen(
                hiddenApps = state.hiddenApps,
                onUnhide = viewModel::unhideApp,
                onBack = { navController.popBackStack() },
            )
        }
        composable<PrivacyPolicyRoute> {
            StaticInfoSubScreen(
                title = stringResource(R.string.settings_privacy_policy_cta),
                body = stringResource(R.string.settings_privacy_policy_body),
                onBack = { navController.popBackStack() },
            )
        }
        composable<OpenSourceLicensesRoute> {
            StaticInfoSubScreen(
                title = stringResource(R.string.settings_licenses_cta),
                body = stringResource(R.string.settings_licenses_body),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsMainContent(
    state: SettingsUiState,
    isTabletViewport: Boolean,
    versionName: String,
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
    onOpenHiddenApps: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val palette = AuroraTheme.palette
    val context = LocalContext.current
    val resources = LocalResources.current
    val hostView = LocalView.current

    var showWipeConfirm by remember { mutableStateOf(false) }

    // Protect destructive wipe confirmation against tapjacking overlays (section 11.3).
    DisposableEffect(showWipeConfirm) {
        val previous = hostView.filterTouchesWhenObscured
        if (showWipeConfirm) hostView.filterTouchesWhenObscured = true
        onDispose { hostView.filterTouchesWhenObscured = previous }
    }

    val exportedLabel = stringResource(R.string.settings_backup_exported)
    val importedFmt = stringResource(R.string.settings_backup_imported, 0)
    val failedFmt = stringResource(R.string.settings_backup_failed, "")
    val diagCopiedLabel = stringResource(R.string.settings_diagnostics_copied)
    val biometricUnavailableMsg = stringResource(R.string.biometric_unavailable_notice)

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.pickCustomWallpaper(uri)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportBackupToUri(uri, exportedLabel)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importBackupFromUri(
                uri = uri,
                onSuccessFormat = { count -> resources.getString(R.string.settings_backup_imported, count) },
                onErrorFormat = { err -> resources.getString(R.string.settings_backup_failed, err) },
            )
        }
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuroraSpacing.md, vertical = AuroraSpacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = AuroraTypography.headlineMedium,
                    color = palette.onBackground,
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.defaultMinSize(
                        minWidth = AuroraSpacing.minTouchTarget,
                        minHeight = AuroraSpacing.minTouchTarget,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = palette.onBackground,
                    )
                }
            }

            if (state.statusBanner != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuroraSpacing.md, vertical = AuroraSpacing.xs)
                        .clip(RoundedCornerShape(AuroraRadii.input))
                        .background(palette.surfaceVariant)
                        .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(AuroraRadii.input))
                        .padding(horizontal = AuroraSpacing.md, vertical = AuroraSpacing.sm),
                ) {
                    Text(
                        text = state.statusBanner,
                        style = AuroraTypography.bodyMedium,
                        color = palette.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.canUndoLastGridChange) {
                        TextButton(onClick = viewModel::undoLatestSnapshot) {
                            Text(stringResource(R.string.action_undo), color = palette.accent)
                        }
                    } else {
                        TextButton(onClick = viewModel::dismissBanner) {
                            Text(stringResource(R.string.action_close), color = palette.accent)
                        }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    start = AuroraSpacing.md,
                    end = AuroraSpacing.md,
                    bottom = AuroraSpacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
                modifier = Modifier.fillMaxSize(),
            ) {
                // 1. Appearance & Wallpaper
                item(key = "grp_appearance") {
                    SettingsGroupCard(title = stringResource(R.string.settings_group_appearance)) {
                        Text(
                            text = stringResource(R.string.settings_theme_label),
                            style = AuroraTypography.bodyMedium,
                            color = palette.onSurfaceSecondary,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm)) {
                            listOf(
                                ThemeMode.SYSTEM to R.string.settings_theme_system,
                                ThemeMode.LIGHT to R.string.settings_theme_light,
                                ThemeMode.DARK to R.string.settings_theme_dark,
                            ).forEach { (mode, resId) ->
                                SettingsPill(
                                    label = stringResource(resId),
                                    selected = state.settings.themeMode == mode,
                                    onClick = { viewModel.updateSettings { it.copy(themeMode = mode) } },
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.settings_glass_label),
                            style = AuroraTypography.bodyMedium,
                            color = palette.onSurfaceSecondary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs),
                        ) {
                            listOf(
                                GlassIntensity.AUTO to R.string.settings_glass_auto,
                                GlassIntensity.OFF to R.string.settings_glass_off,
                                GlassIntensity.LOW to R.string.settings_glass_low,
                                GlassIntensity.MEDIUM to R.string.settings_glass_medium,
                                GlassIntensity.HIGH to R.string.settings_glass_high,
                            ).forEach { (gi, resId) ->
                                SettingsPill(
                                    label = stringResource(resId),
                                    selected = state.settings.glassIntensity == gi,
                                    onClick = { viewModel.updateSettings { it.copy(glassIntensity = gi) } },
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.settings_active_tier, state.activeGlassTier.name),
                            style = AuroraTypography.labelMedium,
                            color = palette.accent,
                        )

                        Text(
                            text = stringResource(R.string.settings_wallpaper_label),
                            style = AuroraTypography.bodyMedium,
                            color = palette.onSurfaceSecondary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs),
                        ) {
                            listOf(
                                WallpaperStyle.BOREALIS_TWILIGHT to R.string.settings_wallpaper_borealis,
                                WallpaperStyle.IVORY_MIST to R.string.settings_wallpaper_ivory,
                                WallpaperStyle.SLATE_HORIZON to R.string.settings_wallpaper_slate,
                                WallpaperStyle.AURA_FLOW to R.string.settings_wallpaper_aura_flow,
                                WallpaperStyle.IRIDESCENT_STRATA to R.string.settings_wallpaper_iridescent_strata,
                            ).forEach { (ws, resId) ->
                                SettingsPill(
                                    label = stringResource(resId),
                                    selected = state.settings.wallpaperStyle == ws,
                                    onClick = { viewModel.updateSettings { it.copy(wallpaperStyle = ws) } },
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { photoPicker.launch("image/*") },
                            shape = RoundedCornerShape(AuroraRadii.input),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_wallpaper_pick_photo),
                                color = palette.accent,
                            )
                        }
                    }
                }

                // 2. Grid & Dock
                item(key = "grp_grid") {
                    SettingsGroupCard(title = stringResource(R.string.settings_group_grid)) {
                        Text(
                            text = stringResource(R.string.settings_grid_preset_label),
                            style = AuroraTypography.bodyMedium,
                            color = palette.onSurfaceSecondary,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm)) {
                            listOf(
                                GridPreset.GRID_4X5 to "4 x 5",
                                GridPreset.GRID_4X6 to "4 x 6",
                                GridPreset.GRID_5X5 to "5 x 5",
                                GridPreset.GRID_5X6 to "5 x 6",
                            ).forEach { (preset, label) ->
                                val notice = stringResource(R.string.settings_grid_reflow_done, label)
                                SettingsPill(
                                    label = label,
                                    selected = state.settings.gridPreset == preset,
                                    onClick = { viewModel.changeGridPreset(preset, isTabletViewport, notice) },
                                )
                            }
                        }

                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_grid_adaptive_tablet),
                            checked = state.settings.adaptiveTabletGrid,
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(adaptiveTabletGrid = v) } },
                        )

                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_autoreflow_title),
                            checked = state.settings.autoReflowGrid,
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoReflowGrid = v) } },
                        )

                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_smart_header_title),
                            checked = state.settings.showSmartHeaderOnPage1,
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(showSmartHeaderOnPage1 = v) } },
                        )

                        Text(
                            text = stringResource(R.string.settings_icon_size_label, state.settings.iconSizeDp),
                            style = AuroraTypography.bodyMedium,
                            color = palette.onSurface,
                        )
                        Slider(
                            value = state.settings.iconSizeDp.toFloat(),
                            onValueChange = { v -> viewModel.updateSettings { it.copy(iconSizeDp = v.roundToInt()) } },
                            valueRange = 52f..68f,
                        )

                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_show_labels),
                            checked = state.settings.showIconLabels,
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(showIconLabels = v) } },
                        )

                        Text(
                            text = stringResource(R.string.settings_dock_slots_label, state.settings.dockSlots),
                            style = AuroraTypography.bodyMedium,
                            color = palette.onSurfaceSecondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm)) {
                            listOf(4, 5).forEach { slots ->
                                SettingsPill(
                                    label = "$slots",
                                    selected = state.settings.dockSlots == slots,
                                    onClick = { viewModel.updateSettings { it.copy(dockSlots = slots) } },
                                )
                            }
                        }
                    }
                }

                // 3. Icons & Icon Packs
                item(key = "grp_icons") {
                    SettingsGroupCard(title = stringResource(R.string.settings_group_icons)) {
                        Text(
                            text = stringResource(R.string.settings_icon_shape_label),
                            style = AuroraTypography.bodyMedium,
                            color = palette.onSurfaceSecondary,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm)) {
                            listOf(
                                IconShapeOption.SQUIRCLE to R.string.settings_shape_squircle,
                                IconShapeOption.CIRCLE to R.string.settings_shape_circle,
                                IconShapeOption.ROUNDED_SQUARE to R.string.settings_shape_rounded,
                            ).forEach { (shapeOpt, resId) ->
                                SettingsPill(
                                    label = stringResource(resId),
                                    selected = state.settings.iconShape == shapeOpt,
                                    onClick = { viewModel.updateSettings { it.copy(iconShape = shapeOpt) } },
                                )
                            }
                        }

                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_monochrome_icons),
                            checked = state.settings.monochromeIcons,
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(monochromeIcons = v) } },
                        )

                        Text(
                            text = stringResource(R.string.settings_icon_pack_label),
                            style = AuroraTypography.bodyMedium,
                            color = palette.onSurfaceSecondary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(AuroraSpacing.xs),
                        ) {
                            SettingsPill(
                                label = stringResource(R.string.settings_icon_pack_none),
                                selected = state.settings.selectedIconPackPackage == null,
                                onClick = { viewModel.selectIconPack(null) },
                            )
                            state.iconPacks.forEach { pack ->
                                SettingsPill(
                                    label = pack.label,
                                    selected = state.settings.selectedIconPackPackage == pack.packageName,
                                    onClick = { viewModel.selectIconPack(pack.packageName) },
                                )
                            }
                        }
                    }
                }

                // 4. Gestures & Search
                item(key = "grp_gestures") {
                    SettingsGroupCard(title = stringResource(R.string.settings_group_gestures)) {
                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_swipe_down_spotlight),
                            checked = state.settings.swipeDownSpotlightEnabled,
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(swipeDownSpotlightEnabled = v) } },
                        )
                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_top_right_control),
                            checked = state.settings.topRightControlPanelEnabled,
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(topRightControlPanelEnabled = v) } },
                        )
                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_show_app_library),
                            checked = state.settings.showAppLibraryPage,
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(showAppLibraryPage = v) } },
                        )
                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_search_history),
                            checked = state.settings.saveSearchHistory,
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(saveSearchHistory = v) } },
                        )
                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_search_settings),
                            checked = state.settings.includeSettingsInSearch,
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(includeSettingsInSearch = v) } },
                        )
                        if (state.isContactsSearchSupported) {
                            SettingsSwitchRow(
                                label = stringResource(R.string.settings_search_contacts),
                                checked = state.settings.includeContactsInSearch,
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(includeContactsInSearch = v) } },
                            )
                        }
                    }
                }

                item(key = "grp_privacy") {
                    SettingsGroupCard(title = stringResource(R.string.settings_group_privacy)) {
                        if (state.badgeCapability != BadgeServiceCapability.UNSUPPORTED_IN_BUILD) {
                            SettingsSwitchRow(
                                label = stringResource(R.string.settings_badges_toggle),
                                checked = state.settings.notificationBadgesEnabled,
                                onCheckedChange = { v ->
                                    viewModel.updateSettings { it.copy(notificationBadgesEnabled = v) }
                                },
                            )
                            Text(
                                text = stringResource(R.string.settings_badges_subtitle),
                                style = AuroraTypography.bodyMedium,
                                color = palette.onSurfaceSecondary,
                            )
                            if (state.settings.notificationBadgesEnabled && state.badgeCapability == BadgeServiceCapability.PERMISSION_REQUIRED) {
                                OutlinedButton(
                                    onClick = viewModel::openNotificationAccessSettings,
                                    shape = RoundedCornerShape(AuroraRadii.input),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget),
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_badges_grant_cta),
                                        color = palette.accent,
                                    )
                                }
                            }
                        }

                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_biometric_toggle),
                            checked = state.settings.biometricAppLockEnabled,
                            onCheckedChange = { v ->
                                if (v && !state.isBiometricAvailable) {
                                    android.widget.Toast.makeText(context, biometricUnavailableMsg, android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.updateSettings { it.copy(biometricAppLockEnabled = v) }
                                }
                            },
                        )
                        Text(
                            text = stringResource(R.string.settings_biometric_subtitle),
                            style = AuroraTypography.bodyMedium,
                            color = palette.onSurfaceSecondary,
                        )

                        OutlinedButton(
                            onClick = onOpenHiddenApps,
                            shape = RoundedCornerShape(AuroraRadii.input),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_hidden_apps_cta, state.hiddenApps.size),
                                color = palette.onSurface,
                            )
                        }

                        OutlinedButton(
                            onClick = { showWipeConfirm = true },
                            shape = RoundedCornerShape(AuroraRadii.input),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_wipe_data_cta),
                                color = palette.error,
                            )
                        }
                    }
                }

                // 6. Backup, Restore & Default Home (UC-18, UC-20)
                item(key = "grp_backup") {
                    SettingsGroupCard(title = stringResource(R.string.settings_group_backup)) {
                        Button(
                            onClick = { exportLauncher.launch("aurora-launcher-backup.json") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accent,
                                contentColor = palette.onAccent,
                            ),
                            shape = RoundedCornerShape(AuroraRadii.input),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget),
                        ) {
                            Text(stringResource(R.string.settings_export_backup))
                        }

                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            shape = RoundedCornerShape(AuroraRadii.input),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_import_backup),
                                color = palette.onSurface,
                            )
                        }

                        Text(
                            text = stringResource(R.string.settings_snapshots_label),
                            style = AuroraTypography.bodyMedium,
                            color = palette.onSurfaceSecondary,
                        )
                        if (state.snapshots.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_snapshots_empty),
                                style = AuroraTypography.bodyMedium,
                                color = palette.onSurfaceSecondary,
                            )
                        } else {
                            val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
                            state.snapshots.forEach { snap ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(AuroraRadii.input))
                                        .background(palette.surfaceVariant)
                                        .padding(horizontal = AuroraSpacing.md, vertical = AuroraSpacing.xs),
                                ) {
                                    Text(
                                        text = dateFmt.format(Date(snap.createdAtEpochMs)),
                                        style = AuroraTypography.bodyMedium,
                                        color = palette.onSurface,
                                    )
                                    TextButton(onClick = { viewModel.restoreSnapshot(snap.id) }) {
                                        Text(
                                            text = stringResource(R.string.settings_restore_snapshot),
                                            color = palette.accent,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(AuroraSpacing.xs))
                        OutlinedButton(
                            onClick = viewModel::openDefaultLauncherSettings,
                            shape = RoundedCornerShape(AuroraRadii.input),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_switch_launcher),
                                color = palette.accent,
                            )
                        }
                        Text(
                            text = stringResource(R.string.settings_switch_launcher_sub),
                            style = AuroraTypography.bodyMedium,
                            color = palette.onSurfaceSecondary,
                        )
                    }
                }

                // 7. Performance & Accessibility
                item(key = "grp_advanced") {
                    SettingsGroupCard(title = stringResource(R.string.settings_group_advanced)) {
                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_force_light_mode),
                            checked = state.settings.lightModeForced,
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(lightModeForced = v) } },
                        )
                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_reduce_motion),
                            checked = state.settings.reduceMotionOverride == true,
                            onCheckedChange = { v ->
                                viewModel.updateSettings { it.copy(reduceMotionOverride = if (v) true else null) }
                            },
                        )
                        Text(
                            text = stringResource(R.string.settings_oem_battery_tip),
                            style = AuroraTypography.bodyMedium,
                            color = palette.onSurfaceSecondary,
                        )
                    }
                }

                item(key = "grp_about") {
                    SettingsGroupCard(title = stringResource(R.string.settings_group_about)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
                        ) {
                            AuroraIsometricLogo(size = 40.dp)
                            Text(
                                text = stringResource(R.string.settings_version_label, versionName),
                                style = AuroraTypography.titleMedium,
                                color = palette.onSurface,
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenPrivacyPolicy,
                            shape = RoundedCornerShape(AuroraRadii.input),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget),
                        ) {
                            Text(stringResource(R.string.settings_privacy_policy_cta), color = palette.onSurface)
                        }
                        OutlinedButton(
                            onClick = onOpenLicenses,
                            shape = RoundedCornerShape(AuroraRadii.input),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget),
                        ) {
                            Text(stringResource(R.string.settings_licenses_cta), color = palette.onSurface)
                        }
                        OutlinedButton(
                            onClick = {
                                val report = buildString {
                                    appendLine("Aurora Launcher v$versionName")
                                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                                    appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
                                    appendLine("Glass Tier: ${state.activeGlassTier.name}")
                                    appendLine("Grid Preset: ${state.settings.gridPreset.name}")
                                }
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Aurora Diagnostics", report))
                                android.widget.Toast.makeText(context, diagCopiedLabel, android.widget.Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(AuroraRadii.input),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget),
                        ) {
                            Text(stringResource(R.string.settings_copy_diagnostics), color = palette.accent)
                        }
                    }
                }
            }
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text(stringResource(R.string.settings_wipe_confirm_title)) },
            text = { Text(stringResource(R.string.settings_wipe_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWipeConfirm = false
                        viewModel.wipeAllLocalData(onDone = onClose)
                    },
                ) {
                    Text(stringResource(R.string.action_confirm), color = palette.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun HiddenAppsSubScreen(
    hiddenApps: List<AppEntry>,
    onUnhide: (ComponentKey) -> Unit,
    onBack: () -> Unit,
) {
    val palette = AuroraTheme.palette
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(AuroraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.defaultMinSize(
                    minWidth = AuroraSpacing.minTouchTarget,
                    minHeight = AuroraSpacing.minTouchTarget,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_close),
                    tint = palette.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.settings_hidden_apps_title),
                style = AuroraTypography.headlineMedium,
                color = palette.onBackground,
            )
        }

        if (hiddenApps.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_hidden_apps_empty),
                style = AuroraTypography.bodyLarge,
                color = palette.onSurfaceSecondary,
                modifier = Modifier.padding(AuroraSpacing.md),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(AuroraSpacing.sm)) {
                items(hiddenApps, key = { it.key.asCompactKey() }) { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AuroraRadii.card))
                            .background(palette.surface)
                            .padding(horizontal = AuroraSpacing.md, vertical = AuroraSpacing.sm),
                    ) {
                        Text(
                            text = app.label,
                            style = AuroraTypography.bodyLarge,
                            color = palette.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onUnhide(app.key) }) {
                            Text(
                                text = stringResource(R.string.settings_unhide_action),
                                color = palette.accent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaticInfoSubScreen(
    title: String,
    body: String,
    onBack: () -> Unit,
) {
    val palette = AuroraTheme.palette
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(AuroraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AuroraSpacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.defaultMinSize(
                    minWidth = AuroraSpacing.minTouchTarget,
                    minHeight = AuroraSpacing.minTouchTarget,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_close),
                    tint = palette.onBackground,
                )
            }
            Text(
                text = title,
                style = AuroraTypography.titleLarge,
                color = palette.onBackground,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AuroraRadii.card))
                .background(palette.surface)
                .padding(AuroraSpacing.lg),
        ) {
            Text(
                text = body,
                style = AuroraTypography.bodyLarge,
                color = palette.onSurface,
            )
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit,
) {
    val palette = AuroraTheme.palette
    val cardShape = RoundedCornerShape(AuroraRadii.card)
    Column(
        verticalArrangement = Arrangement.spacedBy(AuroraSpacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(palette.surface)
            .border(1.dp, palette.outline.copy(alpha = 0.25f), cardShape)
            .padding(AuroraSpacing.md),
    ) {
        Text(
            text = title,
            style = AuroraTypography.titleMedium,
            color = palette.accent,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = AuroraTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuroraSpacing.minTouchTarget)
            .clickable { onCheckedChange(!checked) },
    ) {
        Text(
            text = label,
            style = AuroraTypography.bodyLarge,
            color = palette.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(end = AuroraSpacing.sm),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = AuroraTheme.palette
    val shape = RoundedCornerShape(AuroraRadii.input)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .defaultMinSize(minHeight = 42.dp)
            .clip(shape)
            .background(if (selected) palette.accent else palette.surfaceVariant)
            .auroraFocusRing(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = AuroraSpacing.md, vertical = AuroraSpacing.xs),
    ) {
        Text(
            text = label,
            style = AuroraTypography.bodyMedium,
            color = if (selected) palette.onAccent else palette.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
