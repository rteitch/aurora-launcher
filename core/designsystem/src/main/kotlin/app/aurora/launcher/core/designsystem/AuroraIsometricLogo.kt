package app.aurora.launcher.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Brand emblem rendering Aurora Launcher's 3-tier isometric spatial prism inside the 22.4% superellipse.
 */
@Composable
fun AuroraIsometricLogo(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .clip(SquircleShape.Default)
            .border(1.dp, Color(0x42FFFFFF), SquircleShape.Default),
    ) {
        val w = this.size.width
        val h = this.size.height
        val sx = w / 108f
        val sy = h / 108f

        fun poly(vararg coords: Float): Path = Path().apply {
            if (coords.size >= 2) {
                moveTo(coords[0] * sx, coords[1] * sy)
                var i = 2
                while (i + 1 < coords.size) {
                    lineTo(coords[i] * sx, coords[i + 1] * sy)
                    i += 2
                }
                close()
            }
        }

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF0C1017), Color(0xFF172A40)),
                start = Offset.Zero,
                end = Offset(w, h),
            ),
        )
        drawPath(
            path = poly(0f, 74f, 54f, 43f, 108f, 74f, 108f, 108f, 0f, 108f),
            color = Color(0xB8132235),
        )

        // 30-degree isometric projection mapped to the 108x108 adaptive-icon grid.
        drawPath(
            path = poly(54f, 52f, 78f, 66f, 54f, 80f, 30f, 66f),
            color = Color(0xFF18507A),
        )
        drawPath(
            path = poly(30f, 66f, 54f, 80f, 54f, 86.5f, 30f, 72.5f),
            color = Color(0xFF0E4973),
        )
        drawPath(
            path = poly(54f, 80f, 78f, 66f, 78f, 72.5f, 54f, 86.5f),
            color = Color(0xFF082D49),
        )

        drawPath(
            path = poly(54f, 41f, 74f, 52.5f, 54f, 64f, 34f, 52.5f),
            color = Color(0xFF38AEF0),
        )
        drawPath(
            path = poly(34f, 52.5f, 54f, 41f, 74f, 52.5f, 72.1f, 53.6f, 54f, 43.2f, 35.9f, 53.6f),
            color = Color(0xFFBCEAFF),
        )
        drawPath(
            path = poly(34f, 52.5f, 54f, 64f, 54f, 69.5f, 34f, 58f),
            color = Color(0xFF1877B2),
        )
        drawPath(
            path = poly(54f, 64f, 74f, 52.5f, 74f, 58f, 54f, 69.5f),
            color = Color(0xFF0B4F7C),
        )

        drawPath(
            path = poly(54f, 21.5f, 39f, 40.5f, 54f, 49.2f),
            color = Color(0xFFF6F5F0),
        )
        drawPath(
            path = poly(54f, 21.5f, 69f, 40.5f, 54f, 49.2f),
            color = Color(0xFF5CC8FF),
        )
        drawPath(
            path = poly(39f, 40.5f, 54f, 49.2f, 54f, 53.2f, 39f, 44.5f),
            color = Color(0xFFDCE4EE),
        )
        drawPath(
            path = poly(54f, 49.2f, 69f, 40.5f, 69f, 44.5f, 54f, 53.2f),
            color = Color(0xFF1E82BE),
        )
    }
}
