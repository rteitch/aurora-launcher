package app.aurora.launcher.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Serializable
enum class GlassIntensity {
    AUTO,
    OFF,
    LOW,
    MEDIUM,
    HIGH,
}

@Serializable
enum class GlassTier {
    TIER_A_AGSL,
    TIER_B_BLUR,
    TIER_C_SOLID,
}

@Serializable
enum class WallpaperStyle {
    BOREALIS_TWILIGHT,
    IVORY_MIST,
    SLATE_HORIZON,
    AURA_FLOW,
    IRIDESCENT_STRATA,
    CUSTOM_IMAGE,
}

@Serializable
enum class GridPreset(val columns: Int, val rows: Int) {
    GRID_4X5(4, 5),
    GRID_4X6(4, 6),
    GRID_5X5(5, 5),
    GRID_5X6(5, 6),
    TABLET_6X5(6, 5),
}

@Serializable
enum class IconShapeOption {
    SQUIRCLE,
    CIRCLE,
    ROUNDED_SQUARE,
}

@Serializable
enum class SwipeUpAction {
    APP_LIBRARY,
    SPOTLIGHT,
    NONE,
}

@Serializable
data class LauncherSettings(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val glassIntensity: GlassIntensity = GlassIntensity.AUTO,
    val wallpaperStyle: WallpaperStyle = WallpaperStyle.BOREALIS_TWILIGHT,
    val customWallpaperPath: String? = null,
    val gridPreset: GridPreset = GridPreset.GRID_4X6,
    val adaptiveTabletGrid: Boolean = true,
    val iconSizeDp: Int = 60,
    val showIconLabels: Boolean = true,
    val iconShape: IconShapeOption = IconShapeOption.SQUIRCLE,
    val monochromeIcons: Boolean = false,
    val selectedIconPackPackage: String? = null,
    val dockSlots: Int = 4,
    val showAppLibraryPage: Boolean = true,
    val swipeDownSpotlightEnabled: Boolean = true,
    val topRightControlPanelEnabled: Boolean = true,
    val swipeUpAction: SwipeUpAction = SwipeUpAction.APP_LIBRARY,
    val autoReflowGrid: Boolean = false,
    val hiddenPageIds: Set<Long> = emptySet(),
    val showSmartHeaderOnPage1: Boolean = true,
    val includeSettingsInSearch: Boolean = false,
    val includeContactsInSearch: Boolean = false,
    val saveSearchHistory: Boolean = false,
    val notificationBadgesEnabled: Boolean = false,
    val biometricAppLockEnabled: Boolean = false,
    val lightModeForced: Boolean = false,
    val reduceMotionOverride: Boolean? = null,
) {
    fun effectiveGridSpec(isTabletViewport: Boolean): GridSpec {
        val preset = if (isTabletViewport && adaptiveTabletGrid && gridPreset == GridPreset.GRID_4X6) {
            GridPreset.TABLET_6X5
        } else {
            gridPreset
        }
        val effectiveDock = if (isTabletViewport) maxOf(dockSlots, 5) else dockSlots
        return GridSpec(
            columns = preset.columns,
            rows = preset.rows,
            dockSlots = effectiveDock.coerceIn(4, 6),
        )
    }

    fun resolveGlassTier(
        sdkInt: Int,
        isLowRamDevice: Boolean,
        isPowerSaveMode: Boolean,
        isLiveWallpaperActive: Boolean,
    ): GlassTier {
        if (lightModeForced || glassIntensity == GlassIntensity.OFF || isLowRamDevice || isPowerSaveMode || isLiveWallpaperActive) {
            return GlassTier.TIER_C_SOLID
        }
        return when {
            sdkInt >= 33 && glassIntensity != GlassIntensity.LOW -> GlassTier.TIER_A_AGSL
            sdkInt >= 31 -> GlassTier.TIER_B_BLUR
            else -> GlassTier.TIER_C_SOLID
        }
    }
}
