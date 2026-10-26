package app.aurora.launcher.core.platform

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class IconCacheAndLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val iconPackGateway: IconPackGateway,
    private val dispatchers: AuroraDispatchers,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    // Smaller cache budget on Android Go / low-RAM devices prevents LMK pressure (E25).
    private val maxEntries: Int = run {
        val am = context.getSystemService(ActivityManager::class.java)
        if (am?.isLowRamDevice == true) 64 else 180
    }

    private val memoryCache = object : LruCache<String, ImageBitmap>(maxEntries) {}

    fun peekCachedIcon(key: ComponentKey, iconPackPackage: String? = null): ImageBitmap? {
        return memoryCache.get(cacheKey(key, iconPackPackage))
    }

    suspend fun loadAppIcon(
        key: ComponentKey,
        iconPackPackage: String? = null,
        sizePx: Int = 192,
    ): ImageBitmap? {
        val cKey = cacheKey(key, iconPackPackage)
        memoryCache.get(cKey)?.let { return it }

        return withContext(dispatchers.io) {
            memoryCache.get(cKey)?.let { return@withContext it }

            val customDrawable = if (!iconPackPackage.isNullOrBlank()) {
                iconPackGateway.loadCustomDrawable(iconPackPackage, key)
            } else {
                null
            }

            val drawable = customDrawable ?: resolveSystemDrawable(key) ?: return@withContext null
            val bitmap = rasterizeDrawable(drawable, sizePx.coerceIn(96, 256)).asImageBitmap()
            memoryCache.put(cKey, bitmap)
            bitmap
        }
    }

    fun invalidateAll() {
        memoryCache.evictAll()
        iconPackGateway.clearCache()
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
                context.packageManager.getActivityIcon(
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
}
