package app.aurora.launcher.core.platform

import android.app.ActivityManager
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import app.aurora.launcher.core.common.AuroraDispatchers
import app.aurora.launcher.core.common.SystemResult
import app.aurora.launcher.core.common.safeSystemCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class HardwareEnvironmentSignals(
    val sdkInt: Int,
    val isLowRamDevice: Boolean,
    val isPowerSaveMode: Boolean,
    val isLiveWallpaperActive: Boolean,
    val systemReduceMotionEnabled: Boolean,
)

open class WallpaperGateway(
    private val context: Context? = null,
    private val dispatchers: AuroraDispatchers? = null,
) {
    companion object {
        private const val MAX_SOURCE_BYTES = 20L * 1024L * 1024L
        private const val MAX_DECODE_DIMENSION = 2560
    }

    open fun readEnvironmentSignals(): HardwareEnvironmentSignals {
        val ctx = context ?: return HardwareEnvironmentSignals(
            sdkInt = Build.VERSION.SDK_INT,
            isLowRamDevice = false,
            isPowerSaveMode = false,
            isLiveWallpaperActive = false,
            systemReduceMotionEnabled = false,
        )
        val am = ctx.getSystemService(ActivityManager::class.java)
        val pm = ctx.getSystemService(PowerManager::class.java)
        val wm = safeSystemCall { WallpaperManager.getInstance(ctx) }.getOrNull()

        val isLowRam = am?.isLowRamDevice == true
        val isPowerSave = pm?.isPowerSaveMode == true
        val isLiveWallpaper = safeSystemCall { wm?.wallpaperInfo != null }.getOrNull() ?: false
        val animatorScale = safeSystemCall {
            Settings.Global.getFloat(
                ctx.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrNull() ?: 1f

        return HardwareEnvironmentSignals(
            sdkInt = Build.VERSION.SDK_INT,
            isLowRamDevice = isLowRam,
            isPowerSaveMode = isPowerSave,
            isLiveWallpaperActive = isLiveWallpaper,
            systemReduceMotionEnabled = animatorScale <= 0.01f,
        )
    }

    open suspend fun copyCustomWallpaperFromUri(uri: Uri): SystemResult<String> =
        withContext(dispatchers?.io ?: kotlinx.coroutines.Dispatchers.IO) {
            val ctx = context ?: return@withContext SystemResult.Failure("No context")
            safeSystemCall {
                val tempFile = File(ctx.cacheDir, "wallpaper_staging.tmp")
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            copied += read
                            require(copied <= MAX_SOURCE_BYTES) {
                                "Selected image exceeds 20 MB limit"
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: throw IllegalArgumentException("Unable to read selected image")

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(tempFile.absolutePath, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                    "Unsupported image format"
                }

                var sampleSize = 1
                while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > MAX_DECODE_DIMENSION) {
                    sampleSize *= 2
                }

                val decoded = BitmapFactory.decodeFile(
                    tempFile.absolutePath,
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                ) ?: throw IllegalArgumentException("Failed to decode wallpaper image")

                // Alternating filenames ensure Compose remember keys reload immediately when the user picks a new photo.
                val targetName = "wallpaper_custom_${System.currentTimeMillis()}.jpg"
                context.filesDir.listFiles()
                    ?.filter { it.name.startsWith("wallpaper_custom_") }
                    ?.forEach { it.delete() }

                val destination = File(context.filesDir, targetName)
                FileOutputStream(destination).use { out ->
                    decoded.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                decoded.recycle()
                destination.absolutePath
            } finally {
                tempFile.delete()
            }
        }
    }
}
