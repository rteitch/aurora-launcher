package app.aurora.launcher.core.designsystem

import android.provider.Settings
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aurora.launcher.core.model.ThemeMode

@Immutable
data class AuroraPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val outline: Color,
    val error: Color,
    val glassFill: Color,
    val glassSolidFallback: Color,
    val glassHairline: Color,
    val iconLabelText: Color,
    val iconLabelScrim: Color,
)

// Contrast-verified with contrast-check.py in DESIGN.md
val AuroraLightPalette = AuroraPalette(
    isDark = false,
    background = Color(0xFFF6F5F0),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEAE8E0),
    onBackground = Color(0xFF11161F),
    onSurface = Color(0xFF11161F),
    onSurfaceSecondary = Color(0xFF434E5E),
    accent = Color(0xFF0B5C8A),
    onAccent = Color(0xFFFFFFFF),
    outline = Color(0xFF6E7B8B),
    error = Color(0xFFB42318),
    glassFill = Color(0x52FFFFFF),
    glassSolidFallback = Color(0xE8F6F5F0),
    glassHairline = Color(0x8CFFFFFF),
    iconLabelText = Color(0xFFF8FAFC),
    iconLabelScrim = Color(0xB8141A24),
)

val AuroraDarkPalette = AuroraPalette(
    isDark = true,
    background = Color(0xFF0C1017),
    surface = Color(0xFF161D29),
    surfaceVariant = Color(0xFF202938),
    onBackground = Color(0xFFF2F4F8),
    onSurface = Color(0xFFF2F4F8),
    onSurfaceSecondary = Color(0xFFA8B3C4),
    accent = Color(0xFF5CC8FF),
    onAccent = Color(0xFF08131E),
    outline = Color(0xFF5B677A),
    error = Color(0xFFFF8A80),
    glassFill = Color(0x590C1017),
    glassSolidFallback = Color(0xEB141B26),
    glassHairline = Color(0x59FFFFFF),
    iconLabelText = Color(0xFFF8FAFC),
    iconLabelScrim = Color(0xB8141A24),
)

object AuroraSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val workspaceHorizontalMargin: Dp = 20.dp
    val minTouchTarget: Dp = 48.dp
}

object AuroraRadii {
    const val SquircleRatio: Float = 0.224f
    val dock: Dp = 28.dp
    val folder: Dp = 24.dp
    val card: Dp = 18.dp
    val input: Dp = 14.dp
}

object AuroraMotion {
    fun <T> defaultSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = 320f,
    )

    fun <T> folderSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.82f,
        stiffness = 340f,
    )
}

val AuroraTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
    ),
)

val LocalAuroraPalette = staticCompositionLocalOf { AuroraDarkPalette }
val LocalReduceMotion = staticCompositionLocalOf { false }

object AuroraTheme {
    val palette: AuroraPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAuroraPalette.current

    val reduceMotion: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalReduceMotion.current
}

@Composable
fun AuroraLauncherTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    reduceMotionOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (useDark) AuroraDarkPalette else AuroraLightPalette
    val context = LocalContext.current
    val systemReduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
    val effectiveReduceMotion = reduceMotionOverride ?: systemReduceMotion

    val materialScheme: ColorScheme = if (useDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            background = palette.background,
            onBackground = palette.onBackground,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.onSurfaceSecondary,
            outline = palette.outline,
            error = palette.error,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            background = palette.background,
            onBackground = palette.onBackground,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.onSurfaceSecondary,
            outline = palette.outline,
            error = palette.error,
        )
    }

    CompositionLocalProvider(
        LocalAuroraPalette provides palette,
        LocalReduceMotion provides effectiveReduceMotion,
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = AuroraTypography,
            content = content,
        )
    }
}
