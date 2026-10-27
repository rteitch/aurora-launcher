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

open class RoleGateway(
    private val context: Context? = null,
) {
    open fun isDefaultHomeLauncher(): Boolean {
        val ctx = context ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = ctx.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return safeSystemCall { roleManager.isRoleHeld(RoleManager.ROLE_HOME) }
                    .getOrNull() ?: false
            }
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = safeSystemCall {
            ctx.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()
        return resolved?.activityInfo?.packageName == ctx.packageName
    }

    open fun createRequestDefaultHomeIntent(): Intent? {
        val ctx = context ?: return Intent(Settings.ACTION_HOME_SETTINGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = ctx.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return safeSystemCall {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                }.getOrNull()
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    open fun openHomeLauncherChooser(): SystemResult<Unit> {
        val ctx = context ?: return SystemResult.Failure("No context")
        return safeSystemCall {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(intent)
            } else {
                ctx.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
