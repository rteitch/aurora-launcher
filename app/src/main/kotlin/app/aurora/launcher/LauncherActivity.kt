package app.aurora.launcher

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.aurora.launcher.core.data.usecase.LaunchTarget
import app.aurora.launcher.core.designsystem.AuroraLauncherTheme
import app.aurora.launcher.core.designsystem.AuroraTheme
import app.aurora.launcher.core.designsystem.ProceduralWallpaperCanvas
import app.aurora.launcher.core.designsystem.R
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.GlassTier
import app.aurora.launcher.core.model.GridSpec
import app.aurora.launcher.core.model.ShellOverlay
import app.aurora.launcher.core.model.WallpaperStyle
import app.aurora.launcher.core.platform.AppWidgetHostManager
import app.aurora.launcher.core.platform.BiometricAuthGateway
import app.aurora.launcher.feature.applibrary.AppLibraryPage
import app.aurora.launcher.feature.controlpanel.ControlPanelOverlay
import app.aurora.launcher.feature.folder.FolderOverlayModal
import app.aurora.launcher.feature.folder.FolderTilePreview
import app.aurora.launcher.feature.home.HomeScreen
import app.aurora.launcher.feature.onboarding.OnboardingScreen
import app.aurora.launcher.feature.settings.SettingsOverlay
import app.aurora.launcher.feature.spotlight.SpotlightOverlay
import app.aurora.launcher.feature.widgets.WidgetHostSlot
import app.aurora.launcher.feature.widgets.WidgetPickerOverlay
import app.aurora.launcher.feature.widgets.WidgetStackSlot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LauncherActivity : AppCompatActivity() {

    @Inject
    lateinit var appWidgetHostManager: AppWidgetHostManager

    @Inject
    lateinit var biometricAuthGateway: BiometricAuthGateway

    private val shellViewModel: LauncherShellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(appWidgetHostManager)

        setContent {
            LauncherShell(
                activity = this,
                viewModel = shellViewModel,
                biometricAuthGateway = biometricAuthGateway,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Validate HOME intent action before mutating navigation state (E30).
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.ime())
            shellViewModel.onHomeIntentReceived()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        shellViewModel.onTrimMemory(level)
    }
}

@Composable
private fun LauncherShell(
    activity: AppCompatActivity,
    viewModel: LauncherShellViewModel,
    biometricAuthGateway: BiometricAuthGateway,
) {
    LifecycleResumeEffect(Unit) {
        viewModel.onActivityResumed()
        onPauseOrDispose {}
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config = LocalConfiguration.current
    val resources = LocalResources.current
    val isTabletViewport = config.smallestScreenWidthDp >= 600 || config.screenWidthDp >= 600

    LaunchedEffect(isTabletViewport) {
        viewModel.updateTabletViewport(isTabletViewport)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val revokedMessage = resources.getString(R.string.home_badge_revoked_notice)
    LaunchedEffect(state.badgeRevokedNoticePending) {
        if (state.badgeRevokedNoticePending) {
            viewModel.consumeBadgeRevokedNotice(revokedMessage)
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onActivityResumed()
    }

    val biometricRequest by viewModel.biometricPromptFlow.collectAsStateWithLifecycle()
    LaunchedEffect(biometricRequest) {
        val req = biometricRequest ?: return@LaunchedEffect
        val authenticated = biometricAuthGateway.authenticate(
            activity = activity,
            title = req.title,
            subtitle = req.subtitle,
        )
        val failedNotice = resources.getString(R.string.home_launch_failed_notice, req.target.packageName)
        viewModel.onBiometricAuthResult(req.target, authenticated, failedNotice)
    }

    val launchTargetHandler: (LaunchTarget) -> Unit = { target ->
        val label = state.catalogByKey.values.firstOrNull { it.key.packageName == target.packageName }?.label ?: target.packageName
        viewModel.launchTarget(
            target = target,
            biometricTitle = resources.getString(R.string.biometric_prompt_title, label),
            biometricSubtitle = resources.getString(R.string.biometric_prompt_subtitle),
            suspendedNotice = resources.getString(R.string.home_suspended_app_notice, label),
            failedNotice = resources.getString(R.string.home_launch_failed_notice, label),
        )
    }

    val launchWithBoundsHandler: (ComponentKey, Rect?) -> Unit = { key, bounds ->
        launchTargetHandler(LaunchTarget.Component(key, bounds))
    }

    val launchHandler: (ComponentKey) -> Unit = { key ->
        launchWithBoundsHandler(key, null)
    }

    AuroraLauncherTheme(
        themeMode = state.settings.themeMode,
        reduceMotionOverride = state.settings.reduceMotionOverride,
    ) {
        val palette = AuroraTheme.palette
        val view = LocalView.current
        val useDarkBarIcons = when {
            !state.settings.onboardingCompleted -> !palette.isDark
            state.overlay is ShellOverlay.Settings -> !palette.isDark
            state.overlay != ShellOverlay.None -> false
            state.settings.wallpaperStyle == WallpaperStyle.IVORY_MIST &&
                state.settings.customWallpaperPath == null -> true
            state.settings.wallpaperStyle == WallpaperStyle.BOREALIS_TWILIGHT ||
                state.settings.wallpaperStyle == WallpaperStyle.SLATE_HORIZON ||
                state.settings.wallpaperStyle == WallpaperStyle.AURA_FLOW ||
                state.settings.wallpaperStyle == WallpaperStyle.IRIDESCENT_STRATA -> false
            else -> !palette.isDark
        }
        if (!view.isInEditMode) {
            SideEffect {
                val controller = WindowCompat.getInsetsController(activity.window, view)
                controller.isAppearanceLightStatusBars = useDarkBarIcons
                controller.isAppearanceLightNavigationBars = useDarkBarIcons
            }
        }

        if (!state.isReady) {
            Box(modifier = Modifier.fillMaxSize())
            return@AuroraLauncherTheme
        }

        if (!state.settings.onboardingCompleted) {
            OnboardingScreen(
                onFinished = { viewModel.onActivityResumed() },
            )
            return@AuroraLauncherTheme
        }

        val isBlurOverlayOpen = state.overlay is ShellOverlay.Folder ||
            state.overlay is ShellOverlay.Spotlight ||
            state.overlay is ShellOverlay.ControlPanel ||
            state.overlay is ShellOverlay.WidgetPicker

        val backdropBlurModifier = if (
            isBlurOverlayOpen &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            state.glassTier != GlassTier.TIER_C_SOLID
        ) {
            Modifier.blur(18.dp)
        } else {
            Modifier
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(backdropBlurModifier),
            ) {
                ProceduralWallpaperCanvas(
                    style = state.settings.wallpaperStyle,
                    customWallpaperPath = state.settings.customWallpaperPath,
                )

                val currentGridSpec = state.workspace?.gridSpec ?: GridSpec(4, 6, 4)

                HomeScreen(
                    isTabletViewport = isTabletViewport,
                    isDefaultHome = state.isDefaultHome,
                    glassTier = state.glassTier,
                    badgeCounts = state.badgeCounts,
                    homeButtonSignal = state.homeButtonSignal,
                    onRequestSetDefaultHome = {
                        val intent = viewModel.buildRequestRoleIntent()
                        if (intent != null) {
                            runCatching { roleLauncher.launch(intent) }
                        }
                    },
                    onLaunchApp = launchWithBoundsHandler,
                    onLaunchShortcut = launchTargetHandler,
                    onOpenFolder = viewModel::openFolder,
                    onOpenSpotlight = viewModel::openSpotlight,
                    onOpenControlPanel = viewModel::openControlPanel,
                    onOpenWidgetPicker = viewModel::openWidgetPicker,
                    onOpenSettings = viewModel::openSettings,
                    onShowMessage = viewModel::showMessage,
                    folderTileSlot = { folder, iconSize, showLabel, isEditMode, scrim, a11y, onClick, onLongClick, mod ->
                        FolderTilePreview(
                            folder = folder,
                            iconSize = iconSize,
                            showLabel = showLabel,
                            iconShape = state.settings.iconShape,
                            isEditMode = isEditMode,
                            useWallpaperScrimForLabel = scrim,
                            iconLoader = viewModel.iconLoader,
                            iconPackPackage = state.settings.selectedIconPackPackage,
                            accessibilityActions = a11y,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = mod,
                        )
                    },
                    widgetSlot = { widget, isEditMode, isDragging, onEnterEdit, mod ->
                        WidgetHostSlot(
                            widget = widget,
                            hostManager = viewModel.widgetHostManager,
                            isEditMode = isEditMode,
                            isDragging = isDragging,
                            gridSpec = currentGridSpec,
                            onResize = viewModel::resizeWidget,
                            onRemove = viewModel::removeWidgetItem,
                            onLongPressEnterEdit = onEnterEdit,
                            modifier = mod,
                        )
                    },
                    widgetStackSlot = { stack, isEditMode, isDragging, onEnterEdit, mod ->
                        WidgetStackSlot(
                            stack = stack,
                            hostManager = viewModel.widgetHostManager,
                            isEditMode = isEditMode,
                            isDragging = isDragging,
                            gridSpec = currentGridSpec,
                            onActiveIndexChange = viewModel::updateWidgetStackActiveIndex,
                            onResize = viewModel::resizeWidget,
                            onRemove = viewModel::removeWidgetItem,
                            onLongPressEnterEdit = onEnterEdit,
                            modifier = mod,
                        )
                    },
                    appLibrarySlot = {
                        AppLibraryPage(
                            isTabletViewport = isTabletViewport,
                            badgeCounts = state.badgeCounts,
                            onLaunchApp = launchHandler,
                            onShowMessage = viewModel::showMessage,
                        )
                    },
                )
            }

            // Modal overlays
            val activeFolder = (state.overlay as? ShellOverlay.Folder)?.let {
                viewModel.resolveFolderById(it.folderId)
            }
            if (state.overlay is ShellOverlay.Folder && activeFolder == null) {
                LaunchedEffect(state.overlay) {
                    viewModel.closeOverlay()
                }
            }
            FolderOverlayModal(
                folder = activeFolder,
                catalogByKey = state.catalogByKey,
                badgeCounts = state.badgeCounts,
                glassTier = state.glassTier,
                iconSize = state.settings.iconSizeDp.coerceIn(52, 64).dp,
                iconShape = state.settings.iconShape,
                monochromeIcons = state.settings.monochromeIcons,
                iconPackPackage = state.settings.selectedIconPackPackage,
                iconLoader = viewModel.iconLoader,
                onDismiss = viewModel::closeOverlay,
                onRenameFolder = viewModel::renameFolder,
                onAppClick = launchHandler,
                onRemoveChildFromFolder = viewModel::removeChildFromFolder,
            )

            SpotlightOverlay(
                visible = state.overlay is ShellOverlay.Spotlight,
                glassTier = state.glassTier,
                badgeCounts = state.badgeCounts,
                onDismiss = viewModel::closeOverlay,
                onLaunchApp = launchHandler,
            )

            ControlPanelOverlay(
                visible = state.overlay is ShellOverlay.ControlPanel,
                glassTier = state.glassTier,
                onDismiss = viewModel::closeOverlay,
                onOpenLauncherSettings = viewModel::openSettings,
            )

            WidgetPickerOverlay(
                visible = state.overlay is ShellOverlay.WidgetPicker,
                glassTier = state.glassTier,
                gridSpec = state.workspace?.gridSpec ?: GridSpec(4, 6, 4),
                preferredPageId = state.preferredWidgetPageId,
                onDismiss = viewModel::closeOverlay,
                onShowMessage = viewModel::showMessage,
            )

            val installedVersionName = remember(activity) {
                runCatching {
                    @Suppress("DEPRECATION")
                    activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
                }.getOrNull() ?: "1.3.0"
            }

            SettingsOverlay(
                visible = state.overlay is ShellOverlay.Settings,
                isTabletViewport = isTabletViewport,
                versionName = installedVersionName,
                onClose = viewModel::closeOverlay,
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 112.dp),
            )
        }
    }
}
