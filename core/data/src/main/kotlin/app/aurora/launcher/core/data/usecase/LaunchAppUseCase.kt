package app.aurora.launcher.core.data.usecase

import app.aurora.launcher.core.common.SystemResult
import app.aurora.launcher.core.data.repository.AppCatalogRepository
import app.aurora.launcher.core.data.repository.LauncherSettingsRepository
import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.AppShortcutItem
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.WorkspaceItem
import app.aurora.launcher.core.platform.LauncherAppsGateway
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LaunchTarget {
    data class Component(val key: ComponentKey) : LaunchTarget
    data class Pinned(val item: WorkspaceItem.PinnedShortcut) : LaunchTarget
    data class Dynamic(val shortcut: AppShortcutItem) : LaunchTarget

    val packageName: String
        get() = when (this) {
            is Component -> key.packageName
            is Pinned -> item.packageName
            is Dynamic -> shortcut.packageName
        }
}

sealed interface LaunchAppResult {
    data object Success : LaunchAppResult
    data class Suspended(val packageName: String) : LaunchAppResult
    data class BiometricRequired(
        val target: LaunchTarget,
        val appLabel: String,
    ) : LaunchAppResult
    data class Failed(val message: String) : LaunchAppResult
}

@Singleton
class LaunchAppUseCase @Inject constructor(
    private val appCatalogRepository: AppCatalogRepository,
    private val settingsRepository: LauncherSettingsRepository,
    private val launcherAppsGateway: LauncherAppsGateway,
) {
    suspend fun prepareLaunch(
        target: LaunchTarget,
        bypassBiometric: Boolean = false,
    ): LaunchAppResult {
        val settings = settingsRepository.settings.first()
        val allApps = appCatalogRepository.allApps.first()
        val entry = allApps.firstOrNull { it.key.packageName == target.packageName }

        if (entry != null && (entry.isSuspended || entry.isQuietModePaused)) {
            return LaunchAppResult.Suspended(target.packageName)
        }

        if (!bypassBiometric && settings.biometricAppLockEnabled && entry?.isLocked == true) {
            val label = entry.label.ifBlank { target.packageName }
            return LaunchAppResult.BiometricRequired(target, label)
        }

        return executeLaunch(target, entry)
    }

    suspend fun executeLaunch(
        target: LaunchTarget,
        cachedEntry: AppEntry? = null,
    ): LaunchAppResult {
        val result = when (target) {
            is LaunchTarget.Component -> launcherAppsGateway.launchActivity(target.key)
            is LaunchTarget.Pinned -> launcherAppsGateway.launchShortcut(
                packageName = target.item.packageName,
                shortcutId = target.item.shortcutId,
                userSerial = target.item.userSerial,
            )
            is LaunchTarget.Dynamic -> launcherAppsGateway.launchShortcut(
                packageName = target.shortcut.packageName,
                shortcutId = target.shortcut.id,
                userSerial = target.shortcut.userSerial,
            )
        }

        return when (result) {
            is SystemResult.Success -> {
                val compKey = when (target) {
                    is LaunchTarget.Component -> target.key
                    is LaunchTarget.Pinned -> cachedEntry?.key
                        ?: ComponentKey(target.item.packageName, "", target.item.userSerial)
                    is LaunchTarget.Dynamic -> cachedEntry?.key
                        ?: ComponentKey(target.shortcut.packageName, "", target.shortcut.userSerial)
                }
                appCatalogRepository.recordAppLaunch(compKey)
                LaunchAppResult.Success
            }
            is SystemResult.Failure -> {
                LaunchAppResult.Failed(result.reason)
            }
        }
    }
}
