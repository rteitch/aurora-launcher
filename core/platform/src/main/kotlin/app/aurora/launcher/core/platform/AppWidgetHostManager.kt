package app.aurora.launcher.core.platform

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.RemoteViews
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.aurora.launcher.core.common.AuroraDispatchers
import app.aurora.launcher.core.common.safeSystemCall
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.GridSpec
import app.aurora.launcher.core.model.WidgetProviderCandidate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max

object WidgetSpanCalculator {
    // Standard Android launcher cell formula: 70dp per cell minus 30dp margin allowance.
    fun dpToCells(dp: Int, maxCells: Int): Int {
        if (dp <= 0) return 1
        return ceil((dp + 30.0) / 70.0).toInt().coerceIn(1, max(1, maxCells))
    }
}

class SafeAppWidgetHostView(
    context: Context,
    private val errorLabel: String,
) : AppWidgetHostView(context) {

    private var dragFrozen: Boolean = false

    fun setDragFrozen(frozen: Boolean) {
        dragFrozen = frozen
    }

    // Intercepting touch events during Workspace drag prevents RemoteViews from stealing pointer gestures (S-03).
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (dragFrozen) return true
        return super.onInterceptTouchEvent(ev)
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        try {
            super.updateAppWidget(remoteViews)
        } catch (_: Throwable) {
            super.updateAppWidget(null)
        }
    }

    override fun getErrorView(): View {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * density
            setColor(Color.parseColor("#1D2432"))
            setStroke((1f * density).toInt().coerceAtLeast(1), Color.parseColor("#475264"))
        }
        return TextView(context).apply {
            text = errorLabel
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#F1F5FA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(pad, pad, pad, pad)
            background = backgroundDrawable
        }
    }
}

open class AppWidgetHostManager(
    private val context: Context? = null,
    private val dispatchers: AuroraDispatchers? = null,
) : DefaultLifecycleObserver {

    companion object {
        const val HOST_ID = 1024
    }

    private val appWidgetManager: AppWidgetManager? = if (context != null) AppWidgetManager.getInstance(context) else null
    private val userManager: UserManager? = context?.getSystemService(UserManager::class.java)
    private val appWidgetHost = if (context != null) {
        object : AppWidgetHost(context, HOST_ID) {
            override fun onCreateView(
                context: Context,
                appWidgetId: Int,
                appWidget: AppWidgetProviderInfo?,
            ): AppWidgetHostView {
                return SafeAppWidgetHostView(context, errorLabel = defaultErrorLabel)
            }
        }
    } else null

    @Volatile
    private var defaultErrorLabel: String = "Widget unavailable"

    fun setDefaultErrorLabel(label: String) {
        defaultErrorLabel = label
    }

    override fun onStart(owner: LifecycleOwner) {
        safeSystemCall { appWidgetHost?.startListening() }
    }

    override fun onStop(owner: LifecycleOwner) {
        safeSystemCall { appWidgetHost?.stopListening() }
    }

    fun allocateWidgetId(): Int {
        return safeSystemCall { appWidgetHost?.allocateAppWidgetId() ?: -1 }.getOrNull() ?: -1
    }

    open fun deleteWidgetId(appWidgetId: Int) {
        if (appWidgetId <= 0) return
        safeSystemCall { appWidgetHost?.deleteAppWidgetId(appWidgetId) }
    }

    fun bindWidgetIfAllowed(
        appWidgetId: Int,
        candidate: WidgetProviderCandidate,
    ): Boolean {
        return safeSystemCall {
            val provider = ComponentName(candidate.provider.packageName, candidate.provider.activityClass)
            val handle = userManager?.getUserForSerialNumber(candidate.provider.userSerial)
                ?: android.os.Process.myUserHandle()
            appWidgetManager?.bindAppWidgetIdIfAllowed(appWidgetId, handle, provider, null) ?: false
        }.getOrNull() ?: false
    }

    fun createBindWidgetIntent(
        appWidgetId: Int,
        candidate: WidgetProviderCandidate,
    ): Intent {
        val provider = ComponentName(candidate.provider.packageName, candidate.provider.activityClass)
        val handle = userManager?.getUserForSerialNumber(candidate.provider.userSerial)
            ?: android.os.Process.myUserHandle()
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, handle)
        }
    }

    fun getProviderInfo(appWidgetId: Int): AppWidgetProviderInfo? {
        return safeSystemCall { appWidgetManager?.getAppWidgetInfo(appWidgetId) }.getOrNull()
    }

    fun createConfigurationIntent(appWidgetId: Int): Intent? {
        val info = getProviderInfo(appWidgetId) ?: return null
        val configComponent = info.configure ?: return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = configComponent
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    fun createHostView(
        activityContext: Context,
        appWidgetId: Int,
        errorText: String,
    ): SafeAppWidgetHostView? {
        defaultErrorLabel = errorText
        val info = getProviderInfo(appWidgetId) ?: return null
        return safeSystemCall {
            val view = appWidgetHost?.createView(activityContext, appWidgetId, info)
            (view as? SafeAppWidgetHostView) ?: SafeAppWidgetHostView(activityContext, errorText).apply {
                setAppWidget(appWidgetId, info)
            }
        }.getOrNull()
    }

    fun updateWidgetSize(
        view: AppWidgetHostView?,
        appWidgetId: Int,
        spanX: Int,
        spanY: Int,
        gridSpec: GridSpec,
    ) {
        if (view == null || appWidgetId <= 0) return
        safeSystemCall {
            val minWidth = (spanX * 70 - 16).coerceAtLeast(40)
            val minHeight = (spanY * 70 - 16).coerceAtLeast(40)
            val maxWidth = spanX * 70
            val maxHeight = spanY * 70
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minWidth)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHeight)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxWidth)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxHeight)
            }
            view.updateAppWidgetSize(options, minWidth, minHeight, maxWidth, maxHeight)
            appWidgetManager?.updateAppWidgetOptions(appWidgetId, options)
        }
    }

    fun toWidgetCandidate(
        info: AppWidgetProviderInfo,
        gridSpec: GridSpec,
    ): WidgetProviderCandidate? = safeSystemCall {
        val ctx = context ?: return@safeSystemCall null
        val density = ctx.resources.displayMetrics.density.coerceAtLeast(1f)
        val pm = ctx.packageManager
        val providerComp = info.provider ?: return@safeSystemCall null
        val profile = info.profile
        val serial = userManager?.getSerialNumberForUser(profile) ?: 0L
        val appInfo = pm.getApplicationInfo(providerComp.packageName, 0)
        val appLabel = pm.getApplicationLabel(appInfo).toString()
        val widgetLabel = info.loadLabel(pm)?.toString().takeUnless { it.isNullOrBlank() } ?: appLabel
        val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.loadDescription(ctx)?.toString().orEmpty()
        } else {
            ""
        }

        val minWidthDp = (info.minWidth / density).toInt()
        val minHeightDp = (info.minHeight / density).toInt()
        val minResizeWidthDp = (info.minResizeWidth / density).toInt()
        val minResizeHeightDp = (info.minResizeHeight / density).toInt()

        val defaultSpanX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0) {
            info.targetCellWidth.coerceIn(1, gridSpec.columns)
        } else {
            WidgetSpanCalculator.dpToCells(minWidthDp, gridSpec.columns)
        }
        val defaultSpanY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellHeight > 0) {
            info.targetCellHeight.coerceIn(1, gridSpec.rows)
        } else {
            WidgetSpanCalculator.dpToCells(minHeightDp, gridSpec.rows)
        }

        val canResizeX = (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
        val canResizeY = (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0

        val minSpanX = if (canResizeX && minResizeWidthDp > 0) {
            WidgetSpanCalculator.dpToCells(minResizeWidthDp, gridSpec.columns)
        } else {
            defaultSpanX
        }
        val minSpanY = if (canResizeY && minResizeHeightDp > 0) {
            WidgetSpanCalculator.dpToCells(minResizeHeightDp, gridSpec.rows)
        } else {
            defaultSpanY
        }

        val maxSpanX = if (!canResizeX) {
            defaultSpanX
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.maxResizeWidth > 0) {
            WidgetSpanCalculator.dpToCells((info.maxResizeWidth / density).toInt(), gridSpec.columns)
                .coerceAtLeast(defaultSpanX)
        } else {
            gridSpec.columns
        }
        val maxSpanY = if (!canResizeY) {
            defaultSpanY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.maxResizeHeight > 0) {
            WidgetSpanCalculator.dpToCells((info.maxResizeHeight / density).toInt(), gridSpec.rows)
                .coerceAtLeast(defaultSpanY)
        } else {
            gridSpec.rows
        }

        WidgetProviderCandidate(
            provider = ComponentKey(
                packageName = providerComp.packageName,
                activityClass = providerComp.className,
                userSerial = serial,
            ),
            appLabel = appLabel,
            widgetLabel = widgetLabel,
            description = description,
            defaultSpanX = defaultSpanX,
            defaultSpanY = defaultSpanY,
            minSpanX = minSpanX.coerceAtMost(defaultSpanX),
            minSpanY = minSpanY.coerceAtMost(defaultSpanY),
            maxSpanX = maxSpanX.coerceAtLeast(defaultSpanX),
            maxSpanY = maxSpanY.coerceAtLeast(defaultSpanY),
            hasConfigurationActivity = info.configure != null,
        )
    }.getOrNull()

    open suspend fun listInstalledProviders(gridSpec: GridSpec): List<WidgetProviderCandidate> =
        withContext(dispatchers?.io ?: Dispatchers.IO) {
            val providers = safeSystemCall { appWidgetManager?.installedProviders.orEmpty() }
                .getOrNull()
                .orEmpty()

            providers.mapNotNull { info -> toWidgetCandidate(info, gridSpec) }
                .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.widgetLabel.lowercase() }))
        }

    private val previewCache = LruCache<String, Bitmap>(32)

    fun loadWidgetPreview(candidate: WidgetProviderCandidate): Bitmap? {
        val cacheKey = "preview_" + candidate.provider.asCompactKey()
        previewCache.get(cacheKey)?.let { return it }

        val ctx = context ?: return null
        val providers = safeSystemCall { appWidgetManager?.installedProviders.orEmpty() }
            .getOrNull()
            .orEmpty()
        val info = providers.firstOrNull {
            it.provider?.packageName == candidate.provider.packageName &&
                it.provider?.className == candidate.provider.activityClass
        } ?: return null

        val drawable = safeSystemCall {
            info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0)
        }.getOrNull() ?: return null

        return safeSystemCall {
            val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 280
            val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 140
            val scale = minOf(1f, 480f / maxOf(w, h))
            val targetW = (w * scale).toInt().coerceAtLeast(1)
            val targetH = (h * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, targetW, targetH)
            drawable.draw(canvas)
            previewCache.put(cacheKey, bitmap)
            bitmap
        }.getOrNull()
    }

    fun loadWidgetAppIcon(candidate: WidgetProviderCandidate): Bitmap? {
        val cacheKey = "icon_" + candidate.provider.packageName
        previewCache.get(cacheKey)?.let { return it }

        val ctx = context ?: return null
        val drawable = safeSystemCall {
            ctx.packageManager.getApplicationIcon(candidate.provider.packageName)
        }.getOrNull() ?: return null

        return safeSystemCall {
            val sizePx = 96
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            previewCache.put(cacheKey, bitmap)
            bitmap
        }.getOrNull()
    }
}
