package app.aurora.launcher

import android.appwidget.AppWidgetManager
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.SystemClock
import android.os.UserManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import app.aurora.launcher.core.common.safeSystemCall
import app.aurora.launcher.core.data.repository.LauncherSettingsRepository
import app.aurora.launcher.core.data.repository.WorkspaceRepository
import app.aurora.launcher.core.designsystem.AuroraLauncherTheme
import app.aurora.launcher.core.designsystem.R
import app.aurora.launcher.core.model.AppWidgetId
import app.aurora.launcher.core.platform.AppWidgetHostManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PinRequestActivity : AppCompatActivity() {

    companion object {
        private var lastAcceptedElapsedMs: Long = 0L
        private const val MIN_PIN_INTERVAL_MS = 1_200L
    }

    @Inject
    lateinit var workspaceRepository: WorkspaceRepository

    @Inject
    lateinit var settingsRepository: LauncherSettingsRepository

    @Inject
    lateinit var appWidgetHostManager: AppWidgetHostManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Block tapjacking overlays on external pin confirmation prompts (section 11.3).
        window.decorView.filterTouchesWhenObscured = true

        val launcherApps = getSystemService(LauncherApps::class.java)
        val userManager = getSystemService(UserManager::class.java)
        val pinRequest = safeSystemCall { launcherApps?.getPinItemRequest(intent) }.getOrNull()

        val now = SystemClock.elapsedRealtime()
        if (pinRequest == null || !pinRequest.isValid || (now - lastAcceptedElapsedMs) < MIN_PIN_INTERVAL_MS) {
            Toast.makeText(this, getString(R.string.pin_request_invalid), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val shortcutInfo = pinRequest.shortcutInfo
        val widgetInfo = if (pinRequest.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET) {
            safeSystemCall { pinRequest.getAppWidgetProviderInfo(this) }.getOrNull()
        } else {
            null
        }
        val itemLabel = shortcutInfo?.shortLabel?.toString()
            ?: shortcutInfo?.longLabel?.toString()
            ?: widgetInfo?.loadLabel(packageManager)
            ?: getString(R.string.aurora_app_name)

        setContent {
            AuroraLauncherTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text(stringResource(R.string.pin_request_title)) },
                    text = { Text(stringResource(R.string.pin_request_body, itemLabel)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                lifecycleScope.launch {
                                    lastAcceptedElapsedMs = SystemClock.elapsedRealtime()
                                    val config = resources.configuration
                                    val isTablet = config.smallestScreenWidthDp >= 600 || config.screenWidthDp >= 600
                                    val settings = settingsRepository.settings.first()
                                    val gridSpec = settings.effectiveGridSpec(isTabletViewport = isTablet)

                                    when {
                                        shortcutInfo != null -> {
                                            val accepted = safeSystemCall { pinRequest.accept() }.getOrNull() ?: false
                                            if (accepted) {
                                                val serial = userManager?.getSerialNumberForUser(shortcutInfo.userHandle) ?: 0L
                                                workspaceRepository.pinShortcut(
                                                    shortcutId = shortcutInfo.id,
                                                    packageName = shortcutInfo.`package`,
                                                    userSerial = serial,
                                                    label = itemLabel,
                                                    gridSpec = gridSpec,
                                                )
                                            }
                                        }
                                        widgetInfo != null -> {
                                            val allocatedId = appWidgetHostManager.allocateWidgetId()
                                            if (allocatedId > 0) {
                                                val extras = Bundle().apply {
                                                    putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId)
                                                }
                                                val accepted = safeSystemCall { pinRequest.accept(extras) }.getOrNull() ?: false
                                                val candidate = appWidgetHostManager.toWidgetCandidate(widgetInfo, gridSpec)
                                                if (accepted && candidate != null) {
                                                    val placed = workspaceRepository.placeWidget(
                                                        candidate = candidate,
                                                        appWidgetId = AppWidgetId(allocatedId),
                                                        preferredPageId = null,
                                                        gridSpec = gridSpec,
                                                        needsConfig = candidate.hasConfigurationActivity,
                                                    )
                                                    if (placed == null) {
                                                        appWidgetHostManager.deleteWidgetId(allocatedId)
                                                    }
                                                } else {
                                                    appWidgetHostManager.deleteWidgetId(allocatedId)
                                                }
                                            }
                                        }
                                    }
                                    finish()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.pin_request_accept))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { finish() }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                )
            }
        }
    }
}
