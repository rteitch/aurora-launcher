package app.aurora.launcher.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.aurora.launcher.core.model.IconShapeOption
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.withSign

/**
 * Continuous superellipse shape (|x/a|^n + |y/b|^n = 1) with exponent 4.5 matching 22.4% icon curvature.
 */
class SquircleShape(
    private val exponent: Float = 4.5f,
    private val sampleCount: Int = 72,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = buildSuperellipsePath(size.width, size.height, exponent, sampleCount)
        return Outline.Generic(path)
    }

    companion object {
        val Default = SquircleShape()

        fun computeSuperellipsePoints(
            width: Float,
            height: Float,
            exponent: Float = 4.5f,
            samples: Int = 72,
        ): List<Pair<Float, Float>> {
            if (width <= 0f || height <= 0f || samples < 4) return emptyList()
            val a = width / 2f
            val b = height / 2f
            val power = 2f / exponent
            return List(samples) { i ->
                val theta = (2.0 * Math.PI * i) / samples
                val cosT = cos(theta).toFloat()
                val sinT = sin(theta).toFloat()
                val x = a + a * abs(cosT).pow(power).withSign(cosT)
                val y = b + b * abs(sinT).pow(power).withSign(sinT)
                x to y
            }
        }

        fun buildSuperellipsePath(
            width: Float,
            height: Float,
            exponent: Float = 4.5f,
            samples: Int = 72,
        ): Path {
            val path = Path()
            val points = computeSuperellipsePoints(width, height, exponent, samples)
            if (points.isEmpty()) return path
            points.forEachIndexed { idx, (x, y) ->
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            return path
        }
    }
}

fun resolveIconShape(option: IconShapeOption): Shape = when (option) {
    IconShapeOption.SQUIRCLE -> SquircleShape.Default
    IconShapeOption.CIRCLE -> CircleShape
    IconShapeOption.ROUNDED_SQUARE -> RoundedCornerShape(percent = 20)
}
