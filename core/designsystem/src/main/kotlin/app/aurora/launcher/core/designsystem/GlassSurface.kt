package app.aurora.launcher.core.designsystem

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aurora.launcher.core.model.GlassTier

private const val AURORA_GLASS_AGSL = """
    uniform float2 resolution;
    uniform half4 baseTint;
    uniform half4 edgeTint;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / max(resolution, float2(1.0, 1.0));
        float topHighlight = smoothstep(0.32, 0.0, uv.y) * 0.16;
        float cornerSheen = smoothstep(0.85, 0.0, length(uv - float2(0.18, 0.08))) * 0.10;
        float blend = clamp(topHighlight + cornerSheen, 0.0, 0.25);
        return mix(baseTint, edgeTint, blend);
    }
"""

/**
 * Tiered glass surface obeying DESIGN.md and antislop R-10 (reserved for Dock and active modal overlays).
 */
@Composable
fun GlassSurface(
    tier: GlassTier,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = AuroraRadii.dock,
    shape: Shape = RoundedCornerShape(cornerRadius),
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = AuroraTheme.palette
    val baseFill = when (tier) {
        GlassTier.TIER_A_AGSL, GlassTier.TIER_B_BLUR -> palette.glassFill
        GlassTier.TIER_C_SOLID -> palette.glassSolidFallback
    }

    val agslModifier = if (tier == GlassTier.TIER_A_AGSL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(AURORA_GLASS_AGSL) }
        Modifier.drawWithContent {
            shader.setFloatUniform("resolution", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
            shader.setFloatUniform(
                "baseTint",
                baseFill.red,
                baseFill.green,
                baseFill.blue,
                baseFill.alpha,
            )
            shader.setFloatUniform(
                "edgeTint",
                palette.glassHairline.red,
                palette.glassHairline.green,
                palette.glassHairline.blue,
                (baseFill.alpha + 0.18f).coerceAtMost(0.92f),
            )
            drawRect(brush = ShaderBrush(shader))
            drawContent()
        }
    } else {
        Modifier.background(baseFill)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(agslModifier)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.glassHairline,
                        palette.glassHairline.copy(alpha = palette.glassHairline.alpha * 0.28f),
                    ),
                ),
                shape = shape,
            )
            .drawWithContent {
                drawContent()
                drawLine(
                    color = palette.glassHairline,
                    start = Offset(cornerRadius.toPx() * 0.5f, 0.5.dp.toPx()),
                    end = Offset((size.width - cornerRadius.toPx() * 0.5f).coerceAtLeast(0f), 0.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            },
        content = content,
    )
}

/**
 * Visible >= 3:1 keyboard and D-pad focus outline required by antislop-human R-32.
 */
fun Modifier.auroraFocusRing(
    shape: Shape = RoundedCornerShape(12.dp),
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val focusColor: Color = AuroraTheme.palette.accent
    this
        .onFocusChanged { state -> isFocused = state.isFocused }
        .then(
            if (isFocused) {
                Modifier.border(width = 2.dp, color = focusColor, shape = shape)
            } else {
                Modifier
            },
        )
}
