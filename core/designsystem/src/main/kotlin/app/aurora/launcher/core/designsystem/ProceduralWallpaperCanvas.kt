package app.aurora.launcher.core.designsystem

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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

    val path1 = remember { Path() }
    val path2 = remember { Path() }
    val path3 = remember { Path() }
    val path4 = remember { Path() }

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
                path1.rewind()
                path1.moveTo(0f, h * 0.28f)
                path1.cubicTo(
                    w * 0.28f,
                    h * 0.16f,
                    w * 0.68f,
                    h * 0.38f,
                    w,
                    h * 0.22f,
                )
                path1.lineTo(w, h * 0.56f)
                path1.cubicTo(
                    w * 0.65f,
                    h * 0.66f,
                    w * 0.32f,
                    h * 0.44f,
                    0f,
                    h * 0.58f,
                )
                path1.close()
                drawPath(
                    path = path1,
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
                path1.rewind()
                path1.moveTo(0f, h * 0.72f)
                path1.quadraticTo(w * 0.42f, h * 0.64f, w, h * 0.75f)
                path1.lineTo(w, h)
                path1.lineTo(0f, h)
                path1.close()
                drawPath(
                    path = path1,
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
                // Restrained northern horizon ribbons (Borealis Sky & Emerald veil per DESIGN.md)
                path1.rewind()
                path1.moveTo(0f, h * 0.18f)
                path1.cubicTo(w * 0.35f, h * 0.08f, w * 0.75f, h * 0.32f, w, h * 0.20f)
                path1.lineTo(w, h * 0.46f)
                path1.cubicTo(w * 0.65f, h * 0.58f, w * 0.25f, h * 0.36f, 0f, h * 0.48f)
                path1.close()
                drawPath(
                    path = path1,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x000B5C8A),
                            Color(0x400B5C8A),
                            Color(0x2810B981),
                            Color(0x00000000),
                        ),
                        startY = h * 0.10f,
                        endY = h * 0.55f,
                    ),
                )

                path2.rewind()
                path2.moveTo(0f, h * 0.52f)
                path2.cubicTo(w * 0.30f, h * 0.38f, w * 0.70f, h * 0.62f, w, h * 0.45f)
                path2.lineTo(w, h * 0.72f)
                path2.cubicTo(w * 0.68f, h * 0.84f, w * 0.32f, h * 0.68f, 0f, h * 0.78f)
                path2.close()
                drawPath(
                    path = path2,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x005CC8FF),
                            Color(0x355CC8FF),
                            Color(0x200B5C8A),
                            Color(0x00000000),
                        ),
                        startY = h * 0.40f,
                        endY = h * 0.80f,
                    ),
                )

                path3.rewind()
                path3.moveTo(0f, h * 0.38f)
                path3.cubicTo(w * 0.40f, h * 0.30f, w * 0.60f, h * 0.48f, w, h * 0.35f)
                path3.lineTo(w, h * 0.44f)
                path3.cubicTo(w * 0.60f, h * 0.54f, w * 0.40f, h * 0.38f, 0f, h * 0.45f)
                path3.close()
                drawPath(
                    path = path3,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x001D6B8C),
                            Color(0x281D6B8C),
                            Color(0x18142638),
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
                path1.rewind()
                path1.moveTo(0f, h * 0.40f)
                path1.cubicTo(w * 0.30f, h * 0.32f, w * 0.70f, h * 0.46f, w, h * 0.38f)
                path1.lineTo(w, h)
                path1.lineTo(0f, h)
                path1.close()
                drawPath(
                    path = path1,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x2810B981), Color(0x185CC8FF), Color(0x05080C14)),
                        startY = h * 0.35f,
                        endY = h * 0.70f,
                    ),
                )

                // Spectral Strata Layer 2 (Borealis Cyan & Deep Indigo wave)
                path2.rewind()
                path2.moveTo(0f, h * 0.55f)
                path2.cubicTo(w * 0.35f, h * 0.65f, w * 0.65f, h * 0.48f, w, h * 0.58f)
                path2.lineTo(w, h)
                path2.lineTo(0f, h)
                path2.close()
                drawPath(
                    path = path2,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x255CC8FF), Color(0x180B5C8A), Color(0x05080C14)),
                        startY = h * 0.50f,
                        endY = h * 0.85f,
                    ),
                )

                // Spectral Strata Layer 3 (Nordic Twilight wave)
                path3.rewind()
                path3.moveTo(0f, h * 0.70f)
                path3.cubicTo(w * 0.40f, h * 0.62f, w * 0.68f, h * 0.78f, w, h * 0.68f)
                path3.lineTo(w, h)
                path3.lineTo(0f, h)
                path3.close()
                drawPath(
                    path = path3,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x2038A3B8), Color(0x15142638), Color(0x05080C14)),
                        startY = h * 0.65f,
                        endY = h,
                    ),
                )

                // Crest grounding shadow
                path4.rewind()
                path4.moveTo(0f, h * 0.82f)
                path4.quadraticTo(w * 0.50f, h * 0.76f, w, h * 0.84f)
                path4.lineTo(w, h)
                path4.lineTo(0f, h)
                path4.close()
                drawPath(path = path4, color = Color(0x60090D18))
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
