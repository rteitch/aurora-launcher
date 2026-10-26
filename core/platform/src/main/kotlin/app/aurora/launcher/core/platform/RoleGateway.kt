package app.aurora.launcher.core.platform

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import app.aurora.launcher.core.common.SystemResult
import app.aurora.launcher.core.common.safeSystemCall
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoleGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isDefaultHomeLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return safeSystemCall { roleManager.isRoleHeld(RoleManager.ROLE_HOME) }
                    .getOrNull() ?: false
            }
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = safeSystemCall {
            context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun createRequestDefaultHomeIntent(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return safeSystemCall {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                }.getOrNull()
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    fun openHomeLauncherChooser(): SystemResult<Unit> {
        return safeSystemCall {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
