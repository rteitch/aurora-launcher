package app.aurora.launcher.core.platform

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.aurora.launcher.core.common.AuroraDispatchers
import app.aurora.launcher.core.common.safeSystemCall
import app.aurora.launcher.core.model.ComponentKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

open class IconCacheAndLoader(
    private val context: Context? = null,
    private val iconPackGateway: IconPackGateway? = null,
    private val dispatchers: AuroraDispatchers? = null,
) {
    private val launcherApps = context?.getSystemService(LauncherApps::class.java)
    private val userManager = context?.getSystemService(UserManager::class.java)

    val isLowRamDevice: Boolean = run {
        val am = context?.getSystemService(ActivityManager::class.java)
        am?.isLowRamDevice == true
    }

    // Byte-weighted LruCache: 8 MB on Android Go / low-RAM devices, 24 MB on standard devices (E25).
    private val maxCacheBytes: Int = if (isLowRamDevice) 8 * 1024 * 1024 else 24 * 1024 * 1024

    private val memoryCache = object : LruCache<String, ImageBitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return value.width * value.height * 4
        }
    }

    private val diskCacheDir: File? = safeSystemCall {
        val base = context?.cacheDir ?: return@safeSystemCall null
        File(base, "icons").apply {
            if (!exists()) mkdirs()
        }
    }.getOrNull()

    open fun peekCachedIcon(key: ComponentKey, iconPackPackage: String? = null): ImageBitmap? {
        return memoryCache.get(cacheKey(key, iconPackPackage))
    }

    open suspend fun loadAppIcon(
        key: ComponentKey,
        iconPackPackage: String? = null,
        sizePx: Int = 192,
    ): ImageBitmap? {
        val targetSizePx = if (isLowRamDevice) sizePx.coerceIn(96, 128) else sizePx.coerceIn(96, 256)
        val cKey = cacheKey(key, iconPackPackage)
        memoryCache.get(cKey)?.let { return it }

        val ioDispatcher = dispatchers?.io ?: kotlinx.coroutines.Dispatchers.IO
        return withContext(ioDispatcher) {
            memoryCache.get(cKey)?.let { return@withContext it }

            loadFromDisk(cKey)?.let { diskBitmap ->
                memoryCache.put(cKey, diskBitmap)
                return@withContext diskBitmap
            }

            val customDrawable = if (!iconPackPackage.isNullOrBlank()) {
                iconPackGateway?.loadCustomDrawable(iconPackPackage, key)
            } else {
                null
            }

            val drawable = customDrawable ?: resolveSystemDrawable(key) ?: return@withContext null
            val rawBitmap = rasterizeDrawable(drawable, targetSizePx)
            val bitmap = rawBitmap.asImageBitmap()
            memoryCache.put(cKey, bitmap)
            saveToDisk(cKey, rawBitmap)
            bitmap
        }
    }

    open fun evict(key: ComponentKey, iconPackPackage: String? = null) {
        val cKey = cacheKey(key, iconPackPackage)
        safeSystemCall { memoryCache.remove(cKey) }
        safeSystemCall { getDiskFile(cKey)?.delete() }
    }

    open fun invalidateAll() {
        safeSystemCall { memoryCache.evictAll() }
        safeSystemCall {
            diskCacheDir?.listFiles()?.forEach { it.delete() }
        }
        iconPackGateway?.clearCache()
    }

    open fun trimMemory(level: Int) {
        safeSystemCall {
            when {
                level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE ||
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                (isLowRamDevice && level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) -> {
                    memoryCache.evictAll()
                    iconPackGateway?.clearCache()
                }
                level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                    memoryCache.trimToSize(maxCacheBytes / 2)
                }
                isLowRamDevice && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                    memoryCache.trimToSize(maxCacheBytes / 2)
                }
            }
        }
    }

    private fun resolveSystemDrawable(key: ComponentKey): Drawable? {
        return safeSystemCall {
            val handle = userManager?.getUserForSerialNumber(key.userSerial)
                ?: android.os.Process.myUserHandle()
            val activities = launcherApps?.getActivityList(key.packageName, handle).orEmpty()
            val match = activities.firstOrNull {
                it.componentName.className == key.activityClass
            } ?: activities.firstOrNull()
            if (match != null) {
                match.getBadgedIcon(0)
            } else {
                context?.packageManager?.getActivityIcon(
                    ComponentName(key.packageName, key.activityClass),
                )
            }
        }.getOrNull()
    }

    private fun rasterizeDrawable(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val isAdaptive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable
        if (isAdaptive) {
            // Slight overscan avoids transparent seams around OEM adaptive icon masks when clipped to SquircleShape.
            val overscan = (sizePx * 0.08f).roundToInt()
            drawable.setBounds(-overscan, -overscan, sizePx + overscan, sizePx + overscan)
            drawable.draw(canvas)
        } else {
            // Non-adaptive legacy icons sit on a neutral Warm Ivory surface with inset padding (E8).
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F6F5F0")
            }
            canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), bgPaint)
            val inset = (sizePx * 0.15f).roundToInt()
            drawable.setBounds(inset, inset, sizePx - inset, sizePx - inset)
            drawable.draw(canvas)
        }
        return bitmap
    }

    private fun cacheKey(key: ComponentKey, iconPackPackage: String?): String {
        return "${iconPackPackage.orEmpty()}|${key.asCompactKey()}"
    }

    private fun getDiskFile(cKey: String, extension: String = "webp"): File? {
        val dir = diskCacheDir ?: return null
        val hash = safeSystemCall {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(cKey.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        }.getOrNull() ?: cKey.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        return File(dir, "$hash.$extension")
    }

    private fun loadFromDisk(cKey: String): ImageBitmap? {
        val webpFile = getDiskFile(cKey, "webp")
        val targetFile = if (webpFile != null && webpFile.exists() && webpFile.length() > 0L) {
            webpFile
        } else {
            val pngFile = getDiskFile(cKey, "png")
            if (pngFile != null && pngFile.exists() && pngFile.length() > 0L) pngFile else null
        } ?: return null

        return safeSystemCall {
            targetFile.setLastModified(System.currentTimeMillis())
            BitmapFactory.decodeFile(targetFile.absolutePath)?.asImageBitmap()
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun saveToDisk(cKey: String, bitmap: Bitmap) {
        val file = getDiskFile(cKey, "webp") ?: return
        safeSystemCall {
            val parent = file.parentFile ?: return@safeSystemCall
            if (!parent.exists()) {
                parent.mkdirs()
            }
            val temp = File(parent, "${file.name}.${System.nanoTime()}.tmp")
            try {
                temp.outputStream().use { out ->
                    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        Bitmap.CompressFormat.WEBP
                    }
                    bitmap.compress(format, 88, out)
                    out.flush()
                }
                if (temp.exists()) {
                    if (file.exists()) file.delete()
                    temp.renameTo(file)
                    val legacyPng = getDiskFile(cKey, "png")
                    if (legacyPng != null && legacyPng.exists()) {
                        legacyPng.delete()
                    }
                }
                pruneDiskCacheIfNeeded(parent)
            } finally {
                if (temp.exists()) {
                    temp.delete()
                }
            }
        }
    }

    private fun pruneDiskCacheIfNeeded(dir: File) {
        safeSystemCall {
            val files = dir.listFiles { f -> f.extension == "webp" || f.extension == "png" } ?: return@safeSystemCall
            if (files.size > 400) {
                files.sortedBy { it.lastModified() }
                    .take(files.size - 300)
                    .forEach { it.delete() }
            }
        }
    }
}
