package app.aurora.launcher.core.platform

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.aurora.launcher.core.common.SystemResult
import app.aurora.launcher.core.common.safeSystemCall
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class RawBadgeEntry(
    val packageName: String,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    val number: Int,
)

@Singleton
class NotificationBadgeStore @Inject constructor() {
    private val _countsByPackage = MutableStateFlow<Map<String, Int>>(emptyMap())
    val countsByPackage: StateFlow<Map<String, Int>> = _countsByPackage.asStateFlow()

    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    private val _listenerRevokedNoticePending = MutableStateFlow(false)
    val listenerRevokedNoticePending: StateFlow<Boolean> = _listenerRevokedNoticePending.asStateFlow()

    fun onConnected(entries: List<RawBadgeEntry>) {
        _listenerConnected.value = true
        _listenerRevokedNoticePending.value = false
        _countsByPackage.value = aggregateBadgeCounts(entries)
    }

    fun onNotificationsUpdated(entries: List<RawBadgeEntry>) {
        if (!_listenerConnected.value) _listenerConnected.value = true
        _countsByPackage.value = aggregateBadgeCounts(entries)
    }

    // Revoking NotificationListener access clears in-memory counts immediately and flags a one-time notice (E28, section 11.3).
    fun onDisconnected(permissionStillGranted: Boolean) {
        _listenerConnected.value = false
        _countsByPackage.value = emptyMap()
        if (!permissionStillGranted) {
            _listenerRevokedNoticePending.value = true
        }
    }

    fun consumeRevokedNotice() {
        _listenerRevokedNoticePending.value = false
    }

    companion object {
        fun aggregateBadgeCounts(entries: List<RawBadgeEntry>): Map<String, Int> {
            val result = HashMap<String, Int>()
            for (entry in entries) {
                if (entry.isOngoing || entry.isGroupSummary) continue
                val increment = entry.number.coerceAtLeast(1)
                result[entry.packageName] = (result[entry.packageName] ?: 0) + increment
            }
            return result
        }
    }
}

enum class BadgeServiceCapability {
    UNSUPPORTED_IN_BUILD,
    PERMISSION_REQUIRED,
    GRANTED,
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationBadgeEntryPoint {
    fun badgeStore(): NotificationBadgeStore
    fun badgeGateway(): NotificationBadgeGateway
}

@Singleton
class NotificationBadgeGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Query PackageManager by action intent rather than class literal so R8 strips the service in sideload builds.
    fun readCapability(): BadgeServiceCapability {
        val declaredServices = safeSystemCall {
            val query = Intent("android.service.notification.NotificationListenerService")
                .setPackage(context.packageName)
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(query, 0)
                .mapNotNull { it.serviceInfo?.name }
        }.getOrNull().orEmpty()
        val enabled = safeSystemCall {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        }.getOrNull().orEmpty()
        return resolveCapability(
            packageName = context.packageName,
            declaredServiceNames = declaredServices,
            enabledListenersColonSeparated = enabled,
        )
    }

    fun isNotificationAccessGranted(): Boolean {
        return readCapability() == BadgeServiceCapability.GRANTED
    }

    fun openNotificationListenerSettings(): SystemResult<Unit> {
        return safeSystemCall {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    companion object {
        fun resolveCapability(
            packageName: String,
            declaredServiceNames: List<String>,
            enabledListenersColonSeparated: String,
        ): BadgeServiceCapability {
            if (declaredServiceNames.isEmpty()) return BadgeServiceCapability.UNSUPPORTED_IN_BUILD
            val enabledTokens = enabledListenersColonSeparated
                .split(':')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            val isGranted = declaredServiceNames.any { serviceClassName ->
                val resolvedName = if (serviceClassName.startsWith(".")) {
                    "$packageName$serviceClassName"
                } else {
                    serviceClassName
                }
                val full = "$packageName/$resolvedName"
                val short = if (resolvedName.startsWith("$packageName.")) {
                    "$packageName/${resolvedName.removePrefix(packageName)}"
                } else {
                    full
                }
                full in enabledTokens || short in enabledTokens
            }
            return if (isGranted) BadgeServiceCapability.GRANTED else BadgeServiceCapability.PERMISSION_REQUIRED
        }
    }
}

class AuroraNotificationListenerService : NotificationListenerService() {

    private val entryPoint: NotificationBadgeEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            NotificationBadgeEntryPoint::class.java,
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        entryPoint.badgeStore().onConnected(snapshotActiveEntries())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        entryPoint.badgeStore().onNotificationsUpdated(snapshotActiveEntries())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        entryPoint.badgeStore().onNotificationsUpdated(snapshotActiveEntries())
    }

    override fun onListenerDisconnected() {
        val stillGranted = entryPoint.badgeGateway().isNotificationAccessGranted()
        entryPoint.badgeStore().onDisconnected(permissionStillGranted = stillGranted)
        super.onListenerDisconnected()
    }

    // Only package name, ongoing/group flags, and badge number are read; notification text/PII is never accessed (section 11.3).
    private fun snapshotActiveEntries(): List<RawBadgeEntry> {
        val active = safeSystemCall { activeNotifications?.toList().orEmpty() }.getOrNull().orEmpty()
        return active.mapNotNull { sbn ->
            val pkg = sbn.packageName ?: return@mapNotNull null
            if (pkg == packageName) return@mapNotNull null
            val flags = sbn.notification?.flags ?: 0
            val ongoing = (flags and Notification.FLAG_ONGOING_EVENT) != 0
            val groupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0
            val number = sbn.notification?.number ?: 1
            RawBadgeEntry(
                packageName = pkg,
                isOngoing = ongoing,
                isGroupSummary = groupSummary,
                number = number,
            )
        }
    }
}
