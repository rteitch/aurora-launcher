package app.aurora.launcher.core.platform

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.UserManager
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
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max

internal object WidgetSpanCalculator {
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

@Singleton
class AppWidgetHostManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AuroraDispatchers,
) : DefaultLifecycleObserver {

    companion object {
        const val HOST_ID = 1024
    }

    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val userManager: UserManager? = context.getSystemService(UserManager::class.java)
    private val appWidgetHost = object : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?,
        ): AppWidgetHostView {
            return SafeAppWidgetHostView(context, errorLabel = defaultErrorLabel)
        }
    }

    @Volatile
    private var defaultErrorLabel: String = "Widget unavailable"

    fun setDefaultErrorLabel(label: String) {
        defaultErrorLabel = label
    }

    override fun onStart(owner: LifecycleOwner) {
        safeSystemCall { appWidgetHost.startListening() }
    }

    override fun onStop(owner: LifecycleOwner) {
        safeSystemCall { appWidgetHost.stopListening() }
    }

    fun allocateWidgetId(): Int {
        return safeSystemCall { appWidgetHost.allocateAppWidgetId() }.getOrNull() ?: -1
    }

    fun deleteWidgetId(appWidgetId: Int) {
        if (appWidgetId <= 0) return
        safeSystemCall { appWidgetHost.deleteAppWidgetId(appWidgetId) }
    }

    fun bindWidgetIfAllowed(
        appWidgetId: Int,
        candidate: WidgetProviderCandidate,
    ): Boolean {
        return safeSystemCall {
            val provider = ComponentName(candidate.provider.packageName, candidate.provider.activityClass)
            val handle = userManager?.getUserForSerialNumber(candidate.provider.userSerial)
                ?: android.os.Process.myUserHandle()
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, handle, provider, null)
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
        return safeSystemCall { appWidgetManager.getAppWidgetInfo(appWidgetId) }.getOrNull()
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
            val view = appWidgetHost.createView(activityContext, appWidgetId, info)
            (view as? SafeAppWidgetHostView) ?: SafeAppWidgetHostView(activityContext, errorText).apply {
                setAppWidget(appWidgetId, info)
            }
        }.getOrNull()
    }

    fun toWidgetCandidate(
        info: AppWidgetProviderInfo,
        gridSpec: GridSpec,
    ): WidgetProviderCandidate? = safeSystemCall {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val pm = context.packageManager
        val providerComp = info.provider ?: return@safeSystemCall null
        val profile = info.profile
        val serial = userManager?.getSerialNumberForUser(profile) ?: 0L
        val appInfo = pm.getApplicationInfo(providerComp.packageName, 0)
        val appLabel = pm.getApplicationLabel(appInfo).toString()
        val widgetLabel = info.loadLabel(pm)?.toString().takeUnless { it.isNullOrBlank() } ?: appLabel
        val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.loadDescription(context)?.toString().orEmpty()
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

    suspend fun listInstalledProviders(gridSpec: GridSpec): List<WidgetProviderCandidate> =
        withContext(dispatchers.io) {
            val providers = safeSystemCall { appWidgetManager.installedProviders.orEmpty() }
                .getOrNull()
                .orEmpty()

            providers.mapNotNull { info -> toWidgetCandidate(info, gridSpec) }
                .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.widgetLabel.lowercase() }))
        }
}
