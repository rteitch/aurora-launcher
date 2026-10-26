package app.aurora.launcher.core.platform

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import app.aurora.launcher.core.common.AuroraDispatchers
import app.aurora.launcher.core.common.SystemResult
import app.aurora.launcher.core.common.safeSystemCall
import app.aurora.launcher.core.model.AppClassificationHints
import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.AppLibraryClassifier
import app.aurora.launcher.core.model.AppShortcutItem
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.ProfileBadge
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledCatalogSnapshot(
    val apps: List<AppEntry>,
    val isPrivateSpaceLocked: Boolean,
    val uninstalledOrHiddenPackages: Set<String> = emptySet(),
)

@Singleton
class LauncherAppsGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AuroraDispatchers,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    val catalogInvalidations: Flow<Set<String>> = callbackFlow {
        val mainHandler = Handler(Looper.getMainLooper())
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
                trySend(setOfNotNull(packageName))
            }

            override fun onPackageAdded(packageName: String?, user: UserHandle?) {
                trySend(emptySet())
            }

            override fun onPackageChanged(packageName: String?, user: UserHandle?) {
                trySend(emptySet())
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean,
            ) {
                trySend(emptySet())
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean,
            ) {
                if (replacing) {
                    trySend(emptySet())
                } else {
                    trySend(packageNames?.toSet().orEmpty())
                }
            }

            override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) {
                trySend(emptySet())
            }

            override fun onPackagesUnsuspended(packageNames: Array<out String>?, user: UserHandle?) {
                trySend(emptySet())
            }
        }

        safeSystemCall {
            launcherApps?.registerCallback(callback, mainHandler)
        }

        val profileFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                addAction(Intent.ACTION_PROFILE_ACCESSIBLE)
                addAction(Intent.ACTION_PROFILE_INACCESSIBLE)
            }
        }
        val profileReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(emptySet())
            }
        }
        ContextCompat.registerReceiver(
            context,
            profileReceiver,
            profileFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        awaitClose {
            safeSystemCall { launcherApps?.unregisterCallback(callback) }
            safeSystemCall { context.unregisterReceiver(profileReceiver) }
        }
    }

    suspend fun queryInstalledCatalog(): InstalledCatalogSnapshot = withContext(dispatchers.io) {
        val profiles = safeSystemCall { userManager?.userProfiles.orEmpty() }
            .getOrNull()
            .orEmpty()
            .ifEmpty { listOf(Process.myUserHandle()) }

        val entries = ArrayList<AppEntry>()
        var privateSpaceLocked = false

        for (profile in profiles) {
            val serial = safeSystemCall { userManager?.getSerialNumberForUser(profile) ?: 0L }.getOrNull() ?: 0L
            val badge = resolveProfileBadge(profile, serial)
            val quietMode = safeSystemCall { userManager?.isQuietModeEnabled(profile) == true }.getOrNull() ?: false

            // Private Space apps must not be indexed or displayed while the profile is locked (E10).
            if (badge == ProfileBadge.PRIVATE && quietMode) {
                privateSpaceLocked = true
                continue
            }

            val activityList = safeSystemCall {
                launcherApps?.getActivityList(null, profile).orEmpty()
            }.getOrNull().orEmpty()

            for (info in activityList) {
                val pkg = info.componentName.packageName
                if (pkg == context.packageName) continue
                entries += mapActivityToAppEntry(
                    info = info,
                    userSerial = serial,
                    profileBadge = badge,
                    isQuietModePaused = quietMode && badge == ProfileBadge.WORK,
                )
            }
        }

        InstalledCatalogSnapshot(
            apps = entries.sortedWith(
                compareBy<AppEntry> { it.sortKey }
                    .thenBy { it.profileBadge.ordinal }
                    .thenBy { it.key.packageName }
                    .thenBy { it.key.activityClass },
            ),
            isPrivateSpaceLocked = privateSpaceLocked,
        )
    }

    fun launchActivity(key: ComponentKey, sourceBounds: Rect? = null): SystemResult<Unit> {
        return safeSystemCall {
            val handle = resolveUserHandle(key.userSerial)
            val quietMode = userManager?.isQuietModeEnabled(handle) == true
            if (quietMode) {
                throw IllegalStateException("Profile is paused in quiet mode")
            }
            val component = ComponentName(key.packageName, key.activityClass)
            val options = if (sourceBounds != null && sourceBounds.width() > 0 && sourceBounds.height() > 0) {
                ActivityOptions.makeClipRevealAnimation(
                    android.view.View(context),
                    sourceBounds.left,
                    sourceBounds.top,
                    sourceBounds.width(),
                    sourceBounds.height(),
                ).toBundle()
            } else {
                null
            }
            launcherApps?.startMainActivity(component, handle, sourceBounds, options)
                ?: throw IllegalStateException("LauncherApps service unavailable")
        }
    }

    suspend fun queryAppShortcuts(key: ComponentKey): List<AppShortcutItem> = withContext(dispatchers.io) {
        val hasPerm = safeSystemCall { launcherApps?.hasShortcutHostPermission() == true }.getOrNull() ?: false
        if (!hasPerm) return@withContext emptyList()

        safeSystemCall {
            val handle = resolveUserHandle(key.userSerial)
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(key.packageName)
                setActivity(ComponentName(key.packageName, key.activityClass))
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
            }
            launcherApps?.getShortcuts(query, handle)
                .orEmpty()
                .filter { it.isEnabled }
                .sortedBy { it.rank }
                .take(4)
                .map { info ->
                    val shortText = info.shortLabel?.toString()?.takeIf { it.isNotBlank() }
                        ?: info.longLabel?.toString().orEmpty()
                    val longText = info.longLabel?.toString()?.takeIf { it.isNotBlank() } ?: shortText
                    AppShortcutItem(
                        id = info.id,
                        packageName = info.`package`,
                        userSerial = key.userSerial,
                        shortLabel = shortText,
                        longLabel = longText,
                        isDynamic = info.isDynamic,
                    )
                }
        }.getOrNull().orEmpty()
    }

    fun launchShortcut(
        packageName: String,
        shortcutId: String,
        userSerial: Long,
        sourceBounds: Rect? = null,
    ): SystemResult<Unit> {
        return safeSystemCall {
            val handle = resolveUserHandle(userSerial)
            launcherApps?.startShortcut(packageName, shortcutId, sourceBounds, null, handle)
                ?: throw IllegalStateException("LauncherApps service unavailable")
        }
    }

    fun openAppInfo(key: ComponentKey, sourceBounds: Rect? = null): SystemResult<Unit> {
        return safeSystemCall {
            val handle = resolveUserHandle(key.userSerial)
            val component = ComponentName(key.packageName, key.activityClass)
            if (launcherApps != null) {
                launcherApps.startAppDetailsActivity(component, handle, sourceBounds, null)
            } else {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", key.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    fun requestUninstall(key: ComponentKey): SystemResult<Unit> {
        return safeSystemCall {
            val intent = Intent(
                Intent.ACTION_DELETE,
                Uri.fromParts("package", key.packageName, null),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_USER, resolveUserHandle(key.userSerial))
            }
            context.startActivity(intent)
        }
    }

    private fun mapActivityToAppEntry(
        info: LauncherActivityInfo,
        userSerial: Long,
        profileBadge: ProfileBadge,
        isQuietModePaused: Boolean,
    ): AppEntry {
        val appInfo = info.applicationInfo
        val pkg = info.componentName.packageName
        val cls = info.componentName.className
        val label = info.label?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: pkg
        val sortKey = AppLibraryClassifier.buildSortKey(label)
        val isSystem = (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        val isSuspended = (appInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0 || !appInfo.enabled
        val category = appInfo.category.takeIf { it != ApplicationInfo.CATEGORY_UNDEFINED }

        val pkgInfo = safeSystemCall {
            context.packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        }.getOrNull()
        val permissions = pkgInfo?.requestedPermissions?.toSet().orEmpty()
        val installTime = info.firstInstallTime.takeIf { it > 0L } ?: (pkgInfo?.firstInstallTime ?: 0L)
        val updateTime = pkgInfo?.lastUpdateTime ?: installTime

        val hints = AppClassificationHints(
            handlesCameraIntent = permissions.contains(android.Manifest.permission.CAMERA),
            handlesMapsIntent = permissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION),
            handlesAudioVideoIntent = permissions.contains(android.Manifest.permission.RECORD_AUDIO),
        )
        val classifiedGroup = AppLibraryClassifier.classify(
            packageName = pkg,
            userOverride = null,
            systemCategory = category,
            hints = hints,
        )

        return AppEntry(
            key = ComponentKey(
                packageName = pkg,
                activityClass = cls,
                userSerial = userSerial,
            ),
            label = label,
            sortKey = sortKey,
            systemCategory = category,
            libraryGroup = classifiedGroup,
            isSystemApp = isSystem,
            isSuspended = isSuspended,
            isQuietModePaused = isQuietModePaused,
            profileBadge = profileBadge,
            installTimeEpochMs = installTime,
            updateTimeEpochMs = updateTime,
        )
    }

    private fun resolveProfileBadge(profile: UserHandle, serial: Long): ProfileBadge {
        if (profile == Process.myUserHandle() || serial == 0L) {
            return ProfileBadge.PERSONAL
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val userInfo = safeSystemCall { launcherApps?.getLauncherUserInfo(profile) }.getOrNull()
            when (userInfo?.userType) {
                UserManager.USER_TYPE_PROFILE_PRIVATE -> return ProfileBadge.PRIVATE
                UserManager.USER_TYPE_PROFILE_CLONE -> return ProfileBadge.CLONE
                UserManager.USER_TYPE_PROFILE_MANAGED -> return ProfileBadge.WORK
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val isManaged = safeSystemCall { userManager?.isManagedProfile == true }.getOrNull() ?: false
            if (isManaged) return ProfileBadge.WORK
        }
        return ProfileBadge.WORK
    }

    fun resolveUserHandle(userSerial: Long): UserHandle {
        return safeSystemCall { userManager?.getUserForSerialNumber(userSerial) }.getOrNull()
            ?: Process.myUserHandle()
    }
}
