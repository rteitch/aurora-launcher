package app.aurora.launcher.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * DeviceAdminReceiver used solely to lock the screen upon user gesture (e.g. double tap on wallpaper).
 * This eliminates any requirement for AccessibilityService, complying with Google Play Protect policies.
 */
class AuroraDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
