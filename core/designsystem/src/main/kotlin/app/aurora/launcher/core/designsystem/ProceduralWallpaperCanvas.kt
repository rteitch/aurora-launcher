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

    Canvas(modifier = modifier.fillMaxSize()) {
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
