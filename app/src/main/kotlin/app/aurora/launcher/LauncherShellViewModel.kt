package app.aurora.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aurora.launcher.core.common.SystemResult
import app.aurora.launcher.core.data.repository.AppCatalogRepository
import app.aurora.launcher.core.data.repository.LauncherSettingsRepository
import app.aurora.launcher.core.data.repository.WorkspaceRepository
import app.aurora.launcher.core.data.usecase.LaunchAppResult
import app.aurora.launcher.core.data.usecase.LaunchAppUseCase
import app.aurora.launcher.core.data.usecase.LaunchTarget
import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.GlassTier
import app.aurora.launcher.core.model.GridSpec
import app.aurora.launcher.core.model.ItemId
import app.aurora.launcher.core.model.LauncherSettings
import app.aurora.launcher.core.model.PageId
import app.aurora.launcher.core.model.ShellOverlay
import app.aurora.launcher.core.model.WorkspaceItem
import app.aurora.launcher.core.model.WorkspaceSnapshot
import app.aurora.launcher.core.platform.AppWidgetHostManager
import app.aurora.launcher.core.platform.HardwareEnvironmentSignals
import app.aurora.launcher.core.platform.IconCacheAndLoader
import app.aurora.launcher.core.platform.LauncherAppsGateway
import app.aurora.launcher.core.platform.NotificationBadgeGateway
import app.aurora.launcher.core.platform.NotificationBadgeStore
import app.aurora.launcher.core.platform.RoleGateway
import app.aurora.launcher.core.platform.WallpaperGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BiometricPromptRequest(
    val target: LaunchTarget,
    val title: String,
    val subtitle: String,
)

data class ShellUiState(
    val isReady: Boolean = false,
    val settings: LauncherSettings = LauncherSettings(),
    val glassTier: GlassTier = GlassTier.TIER_C_SOLID,
    val isDefaultHome: Boolean = false,
    val overlay: ShellOverlay = ShellOverlay.None,
    val preferredWidgetPageId: PageId? = null,
    val workspace: WorkspaceSnapshot? = null,
    val catalogByKey: Map<ComponentKey, AppEntry> = emptyMap(),
    val badgeCounts: Map<String, Int> = emptyMap(),
    val badgeRevokedNoticePending: Boolean = false,
    val homeButtonSignal: Int = 0,
    val snackbarMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LauncherShellViewModel @Inject constructor(
    private val settingsRepository: LauncherSettingsRepository,
    private val appCatalogRepository: AppCatalogRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val launcherAppsGateway: LauncherAppsGateway,
    private val wallpaperGateway: WallpaperGateway,
    private val roleGateway: RoleGateway,
    private val launchAppUseCase: LaunchAppUseCase,
    private val badgeStore: NotificationBadgeStore,
    private val badgeGateway: NotificationBadgeGateway,
    val widgetHostManager: AppWidgetHostManager,
    val iconLoader: IconCacheAndLoader,
) : ViewModel() {

    private val isTabletFlow = MutableStateFlow(false)
    private val overlayFlow = MutableStateFlow<ShellOverlay>(ShellOverlay.None)
    private val widgetPageFlow = MutableStateFlow<PageId?>(null)
    private val envSignalsFlow = MutableStateFlow(wallpaperGateway.readEnvironmentSignals())
    private val isDefaultFlow = MutableStateFlow(roleGateway.isDefaultHomeLauncher())
    private val homeSignalFlow = MutableStateFlow(0)
    private val snackbarFlow = MutableStateFlow<String?>(null)
    private val _biometricPromptFlow = MutableStateFlow<BiometricPromptRequest?>(null)
    val biometricPromptFlow: StateFlow<BiometricPromptRequest?> = _biometricPromptFlow.asStateFlow()

    private val gridSpecFlow = combine(
        settingsRepository.settings,
        isTabletFlow,
    ) { settings, isTablet ->
        settings.effectiveGridSpec(isTablet)
    }.distinctUntilChanged()

    private val workspaceFlow = gridSpecFlow.flatMapLatest { spec ->
        workspaceRepository.observeWorkspace(MutableStateFlow(spec))
    }

    private data class CoreSlice(
        val settings: LauncherSettings,
        val workspace: WorkspaceSnapshot,
        val apps: List<AppEntry>,
        val env: HardwareEnvironmentSignals,
        val isDefault: Boolean,
    )

    private val coreSliceFlow = combine(
        settingsRepository.settings,
        workspaceFlow,
        appCatalogRepository.allApps,
        envSignalsFlow,
        isDefaultFlow,
    ) { settings, workspace, apps, env, isDefault ->
        CoreSlice(settings, workspace, apps, env, isDefault)
    }

    private data class UiSlice(
        val overlay: ShellOverlay,
        val widgetPageId: PageId?,
        val badges: Map<String, Int>,
        val badgeRevoked: Boolean,
        val homeSignal: Int,
    )

    private val uiSliceFlow = combine(
        overlayFlow,
        widgetPageFlow,
        badgeStore.countsByPackage,
        badgeStore.listenerRevokedNoticePending,
        homeSignalFlow,
    ) { overlay, widgetPage, badges, revoked, signal ->
        UiSlice(overlay, widgetPage, badges, revoked, signal)
    }

    val uiState: StateFlow<ShellUiState> = combine(
        coreSliceFlow,
        uiSliceFlow,
        snackbarFlow,
    ) { core, ui, snack ->
        val tier = core.settings.resolveGlassTier(
            sdkInt = core.env.sdkInt,
            isLowRamDevice = core.env.isLowRamDevice,
            isPowerSaveMode = core.env.isPowerSaveMode,
            isLiveWallpaperActive = core.env.isLiveWallpaperActive,
        )
        ShellUiState(
            isReady = true,
            settings = core.settings,
            glassTier = tier,
            isDefaultHome = core.isDefault,
            overlay = ui.overlay,
            preferredWidgetPageId = ui.widgetPageId,
            workspace = core.workspace,
            catalogByKey = core.apps.associateBy { it.key },
            badgeCounts = if (core.settings.notificationBadgesEnabled) ui.badges else emptyMap(),
            badgeRevokedNoticePending = ui.badgeRevoked,
            homeButtonSignal = ui.homeSignal,
            snackbarMessage = snack,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShellUiState(),
    )

    init {
        syncCatalogAndWorkspace(uninstalledPackages = emptySet())
        viewModelScope.launch {
            launcherAppsGateway.catalogInvalidations.collect { removedPackages ->
                iconLoader.invalidateAll()
                syncCatalogAndWorkspace(uninstalledPackages = removedPackages)
            }
        }
    }

    private fun syncCatalogAndWorkspace(uninstalledPackages: Set<String>) {
        viewModelScope.launch {
            val catalog = launcherAppsGateway.queryInstalledCatalog()
            appCatalogRepository.syncInstalledApps(
                installed = catalog.apps,
                isPrivateSpaceLocked = catalog.isPrivateSpaceLocked,
            )
            if (catalog.isPrivateSpaceLocked) {
                appCatalogRepository.purgePrivateSpaceApps()
            }
            val currentSpec = uiState.value.workspace?.gridSpec ?: GridSpec(4, 6, 4)
            workspaceRepository.seedInitialWorkspaceIfEmpty(catalog.apps, currentSpec)
            val removedWidgetIds = workspaceRepository.reconcileInstalledPackages(
                presentKeys = catalog.apps.mapTo(HashSet()) { it.key },
                uninstalledPackages = uninstalledPackages,
            )
            removedWidgetIds.forEach { widgetHostManager.deleteWidgetId(it.value) }
        }
    }

    fun onActivityResumed() {
        envSignalsFlow.value = wallpaperGateway.readEnvironmentSignals()
        isDefaultFlow.value = roleGateway.isDefaultHomeLauncher()
        if (uiState.value.settings.notificationBadgesEnabled && badgeGateway.isNotificationAccessGranted()) {
            badgeGateway.requestRebindService()
        }
    }

    fun updateTabletViewport(isTablet: Boolean) {
        isTabletFlow.value = isTablet
    }

    fun onHomeIntentReceived() {
        if (overlayFlow.value != ShellOverlay.None) {
            overlayFlow.value = ShellOverlay.None
        } else {
            homeSignalFlow.value += 1
        }
    }

    fun onTrimMemory(level: Int) {
        iconLoader.trimMemory(level)
    }

    fun buildRequestRoleIntent() = roleGateway.createRequestDefaultHomeIntent()

    fun openFolder(folderId: ItemId) {
        overlayFlow.value = ShellOverlay.Folder(folderId)
    }

    fun openSpotlight() {
        overlayFlow.value = ShellOverlay.Spotlight
    }

    fun openControlPanel() {
        overlayFlow.value = ShellOverlay.ControlPanel
    }

    fun openWidgetPicker(preferredPageId: PageId?) {
        widgetPageFlow.value = preferredPageId
        overlayFlow.value = ShellOverlay.WidgetPicker
    }

    fun openSettings() {
        overlayFlow.value = ShellOverlay.Settings
    }

    fun closeOverlay() {
        overlayFlow.value = ShellOverlay.None
    }

    fun showMessage(msg: String) {
        snackbarFlow.value = msg
    }

    fun clearMessage() {
        snackbarFlow.value = null
    }

    fun consumeBadgeRevokedNotice(message: String) {
        badgeStore.consumeRevokedNotice()
        snackbarFlow.value = message
    }

    fun renameFolder(folderId: ItemId, title: String) {
        viewModelScope.launch {
            workspaceRepository.renameFolder(folderId, title)
        }
    }

    fun removeChildFromFolder(childItemId: ItemId) {
        viewModelScope.launch {
            workspaceRepository.extractChildFromFolder(childItemId)
        }
    }

    fun resizeWidget(itemId: ItemId, newSpanX: Int, newSpanY: Int) {
        val spec = uiState.value.workspace?.gridSpec ?: return
        viewModelScope.launch {
            workspaceRepository.resizeWidget(itemId, newSpanX, newSpanY, spec)
        }
    }

    fun removeWidgetItem(itemId: ItemId) {
        viewModelScope.launch {
            val ids = workspaceRepository.removeItemFromHome(itemId)
            ids.forEach { widgetHostManager.deleteWidgetId(it.value) }
        }
    }

    fun updateWidgetStackActiveIndex(stackId: ItemId, index: Int) {
        viewModelScope.launch {
            workspaceRepository.setWidgetStackActiveIndex(stackId, index)
        }
    }

    fun launchTarget(
        target: LaunchTarget,
        biometricTitle: String,
        biometricSubtitle: String,
        suspendedNotice: String,
        failedNotice: String,
    ) {
        viewModelScope.launch {
            when (val result = launchAppUseCase.prepareLaunch(target)) {
                is LaunchAppResult.Success -> {
                    if (overlayFlow.value != ShellOverlay.None) {
                        overlayFlow.value = ShellOverlay.None
                    }
                }
                is LaunchAppResult.Suspended -> {
                    snackbarFlow.value = suspendedNotice
                }
                is LaunchAppResult.BiometricRequired -> {
                    _biometricPromptFlow.value = BiometricPromptRequest(
                        target = target,
                        title = biometricTitle,
                        subtitle = biometricSubtitle,
                    )
                }
                is LaunchAppResult.Failed -> {
                    snackbarFlow.value = failedNotice
                }
            }
        }
    }

    fun onBiometricAuthResult(target: LaunchTarget, authenticated: Boolean, failedNotice: String) {
        _biometricPromptFlow.value = null
        if (authenticated) {
            viewModelScope.launch {
                when (launchAppUseCase.executeLaunch(target)) {
                    is LaunchAppResult.Success -> {
                        if (overlayFlow.value != ShellOverlay.None) {
                            overlayFlow.value = ShellOverlay.None
                        }
                    }
                    is LaunchAppResult.Failed -> {
                        snackbarFlow.value = failedNotice
                    }
                    else -> Unit
                }
            }
        }
    }

    fun launchAppWithGate(
        key: ComponentKey,
        biometricTitle: String,
        biometricSubtitle: String,
        suspendedNotice: String,
        failedNotice: String,
    ) {
        launchTarget(
            target = LaunchTarget.Component(key),
            biometricTitle = biometricTitle,
            biometricSubtitle = biometricSubtitle,
            suspendedNotice = suspendedNotice,
            failedNotice = failedNotice,
        )
    }

    fun resolveFolderById(folderId: ItemId): WorkspaceItem.Folder? {
        val ws = uiState.value.workspace ?: return null
        val all = ws.pages.flatMap { it.items } + ws.dockItems
        return all.filterIsInstance<WorkspaceItem.Folder>().firstOrNull { it.id == folderId }
    }
}
