package app.aurora.launcher.core.designsystem

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import app.aurora.launcher.core.model.WallpaperStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Draws Aurora's original zero-copyright atmospheric backdrops or a downsampled user image for Strategy A blur.
 */
@Composable
fun ProceduralWallpaperCanvas(
    style: WallpaperStyle,
    customWallpaperPath: String?,
    modifier: Modifier = Modifier,
) {
    if (style == WallpaperStyle.CUSTOM_IMAGE && !customWallpaperPath.isNullOrBlank()) {
        val loadedBitmap = produceState<ImageBitmap?>(initialValue = null, customWallpaperPath) {
            value = withContext(Dispatchers.IO) {
                decodeDownsampledWallpaper(customWallpaperPath, maxDimension = 1600)
            }
        }.value

        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize(),
            )
            return
        }
    }

    Canvas(modifier = modifier.fillMaxSize().graphicsLayer()) {
        val w = size.width
        val h = size.height

        when (style) {
            WallpaperStyle.BOREALIS_TWILIGHT, WallpaperStyle.CUSTOM_IMAGE -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF090E16),
                            Color(0xFF0F1A28),
                            Color(0xFF142638),
                            Color(0xFF0B121B),
                        ),
                    ),
                )
                // Subtle northern-sky atmospheric veil
                val ribbon = Path().apply {
                    moveTo(0f, h * 0.28f)
                    cubicTo(
                        w * 0.28f,
                        h * 0.16f,
                        w * 0.68f,
                        h * 0.38f,
                        w,
                        h * 0.22f,
                    )
                    lineTo(w, h * 0.56f)
                    cubicTo(
                        w * 0.65f,
                        h * 0.66f,
                        w * 0.32f,
                        h * 0.44f,
                        0f,
                        h * 0.58f,
                    )
                    close()
                }
                drawPath(
                    path = ribbon,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x001D6B8C),
                            Color(0x421D6B8C),
                            Color(0x2438A3B8),
                            Color(0x00142638),
                        ),
                        startY = h * 0.14f,
                        endY = h * 0.66f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x285CC8FF), Color.Transparent),
                        center = Offset(w * 0.76f, h * 0.24f),
                        radius = w * 0.65f,
                    ),
                    center = Offset(w * 0.76f, h * 0.24f),
                    radius = w * 0.65f,
                )
            }

            WallpaperStyle.IVORY_MIST -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE9EEF2),
                            Color(0xFFF3F2EC),
                            Color(0xFFE4E9EE),
                        ),
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x260B5C8A), Color.Transparent),
                        center = Offset(w * 0.25f, h * 0.22f),
                        radius = w * 0.75f,
                    ),
                    center = Offset(w * 0.25f, h * 0.22f),
                    radius = w * 0.75f,
                )
            }

            WallpaperStyle.SLATE_HORIZON -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF151C26),
                            Color(0xFF1F2B3A),
                            Color(0xFF283749),
                            Color(0xFF131A23),
                        ),
                    ),
                )
                val ridge = Path().apply {
                    moveTo(0f, h * 0.72f)
                    quadraticTo(w * 0.42f, h * 0.64f, w, h * 0.75f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    path = ridge,
                    color = Color(0x550C1118),
                )
            }

            WallpaperStyle.AURA_FLOW -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF060913),
                            Color(0xFF0C1322),
                            Color(0xFF142036),
                            Color(0xFF080C16),
                        ),
                    ),
                )
                // Ambient chromatic glows
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x356366F1), Color.Transparent),
                        center = Offset(w * 0.25f, h * 0.25f),
                        radius = w * 0.85f,
                    ),
                    center = Offset(w * 0.25f, h * 0.25f),
                    radius = w * 0.85f,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x3000D2FF), Color.Transparent),
                        center = Offset(w * 0.80f, h * 0.65f),
                        radius = w * 0.75f,
                    ),
                    center = Offset(w * 0.80f, h * 0.65f),
                    radius = w * 0.75f,
                )

                // Fluid Ribbon 1 (Neon Violet & Royal Purple)
                val ribbonViolet = Path().apply {
                    moveTo(0f, h * 0.18f)
                    cubicTo(w * 0.35f, h * 0.08f, w * 0.75f, h * 0.32f, w, h * 0.20f)
                    lineTo(w, h * 0.46f)
                    cubicTo(w * 0.65f, h * 0.58f, w * 0.25f, h * 0.36f, 0f, h * 0.48f)
                    close()
                }
                drawPath(
                    path = ribbonViolet,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x007928CA),
                            Color(0x607928CA),
                            Color(0x454A00E0),
                            Color(0x00000000),
                        ),
                        startY = h * 0.10f,
                        endY = h * 0.55f,
                    ),
                )

                // Fluid Ribbon 2 (Electric Cyan & Spectral Teal)
                val ribbonCyan = Path().apply {
                    moveTo(0f, h * 0.52f)
                    cubicTo(w * 0.30f, h * 0.38f, w * 0.70f, h * 0.62f, w, h * 0.45f)
                    lineTo(w, h * 0.72f)
                    cubicTo(w * 0.68f, h * 0.84f, w * 0.32f, h * 0.68f, 0f, h * 0.78f)
                    close()
                }
                drawPath(
                    path = ribbonCyan,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x0000D2FF),
                            Color(0x5500D2FF),
                            Color(0x350070F3),
                            Color(0x00000000),
                        ),
                        startY = h * 0.40f,
                        endY = h * 0.80f,
                    ),
                )

                // Fluid Ribbon 3 (Warm Amber chromatic core)
                val ribbonAmber = Path().apply {
                    moveTo(0f, h * 0.38f)
                    cubicTo(w * 0.40f, h * 0.30f, w * 0.60f, h * 0.48f, w, h * 0.35f)
                    lineTo(w, h * 0.44f)
                    cubicTo(w * 0.60f, h * 0.54f, w * 0.40f, h * 0.38f, 0f, h * 0.45f)
                    close()
                }
                drawPath(
                    path = ribbonAmber,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x00FF8008),
                            Color(0x40FF8008),
                            Color(0x30FFC837),
                            Color(0x00000000),
                        ),
                        startY = h * 0.30f,
                        endY = h * 0.50f,
                    ),
                )
            }

            WallpaperStyle.IRIDESCENT_STRATA -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF080C14),
                            Color(0xFF0F1726),
                            Color(0xFF090D18),
                        ),
                    ),
                )
                // Spectral Strata Layer 1 (Emerald & Cyan wave)
                val wave1 = Path().apply {
                    moveTo(0f, h * 0.40f)
                    cubicTo(w * 0.30f, h * 0.32f, w * 0.70f, h * 0.46f, w, h * 0.38f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    path = wave1,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x2810B981), Color(0x1806B6D4), Color(0x05080C14)),
                        startY = h * 0.35f,
                        endY = h * 0.70f,
                    ),
                )

                // Spectral Strata Layer 2 (Cyan & Indigo wave)
                val wave2 = Path().apply {
                    moveTo(0f, h * 0.55f)
                    cubicTo(w * 0.35f, h * 0.65f, w * 0.65f, h * 0.48f, w, h * 0.58f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    path = wave2,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x3006B6D4), Color(0x256366F1), Color(0x05080C14)),
                        startY = h * 0.50f,
                        endY = h * 0.85f,
                    ),
                )

                // Spectral Strata Layer 3 (Rose & Violet wave)
                val wave3 = Path().apply {
                    moveTo(0f, h * 0.70f)
                    cubicTo(w * 0.40f, h * 0.62f, w * 0.68f, h * 0.78f, w, h * 0.68f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    path = wave3,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x35F43F5E), Color(0x207C3AED), Color(0x05080C14)),
                        startY = h * 0.65f,
                        endY = h,
                    ),
                )

                // Crest grounding shadow
                val crest = Path().apply {
                    moveTo(0f, h * 0.82f)
                    quadraticTo(w * 0.50f, h * 0.76f, w, h * 0.84f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path = crest, color = Color(0x60090D18))
            }
        }
    }
}

private fun decodeDownsampledWallpaper(path: String, maxDimension: Int): ImageBitmap? {
    return runCatching {
        val file = File(path)
        if (!file.exists()) return null
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        var sample = 1
        while (boundsOpts.outWidth / sample > maxDimension || boundsOpts.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(file.absolutePath, decodeOpts)?.asImageBitmap()
    }.getOrNull()
}
