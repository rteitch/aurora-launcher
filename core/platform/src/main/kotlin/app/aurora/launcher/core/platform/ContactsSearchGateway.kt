package app.aurora.launcher.core.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import app.aurora.launcher.core.common.AuroraDispatchers
import app.aurora.launcher.core.common.SystemResult
import app.aurora.launcher.core.common.safeSystemCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ContactSearchResult(
    val contactId: Long,
    val displayName: String,
    val lookupUri: String,
)

data class QuickSettingShortcut(
    val id: String,
    val settingsAction: String,
    val keywords: List<String>,
)

open class ContactsAndSettingsSearchGateway(
    private val context: Context? = null,
    private val dispatchers: AuroraDispatchers? = null,
) {
    private val settingsShortcuts = listOf(
        QuickSettingShortcut("wifi", Settings.ACTION_WIFI_SETTINGS, listOf("wifi", "wi-fi", "internet", "jaringan")),
        QuickSettingShortcut("bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, listOf("bluetooth", "perangkat")),
        QuickSettingShortcut("display", Settings.ACTION_DISPLAY_SETTINGS, listOf("display", "layar", "brightness", "kecerahan")),
        QuickSettingShortcut("sound", Settings.ACTION_SOUND_SETTINGS, listOf("sound", "suara", "volume", "audio")),
        QuickSettingShortcut("battery", Intent.ACTION_POWER_USAGE_SUMMARY, listOf("battery", "baterai", "daya")),
        QuickSettingShortcut("storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, listOf("storage", "penyimpanan", "memori")),
        QuickSettingShortcut("apps", Settings.ACTION_APPLICATION_SETTINGS, listOf("apps", "aplikasi")),
        QuickSettingShortcut("security", Settings.ACTION_SECURITY_SETTINGS, listOf("security", "keamanan", "biometrik")),
    )

    open fun isContactsPermissionDeclaredInPackage(): Boolean {
        val ctx = context ?: return false
        val requested = safeSystemCall {
            @Suppress("DEPRECATION")
            val pkgInfo = ctx.packageManager.getPackageInfo(
                ctx.packageName,
                PackageManager.GET_PERMISSIONS,
            )
            pkgInfo.requestedPermissions?.toSet().orEmpty()
        }.getOrNull().orEmpty()
        return isContactsSupported(requested)
    }

    open fun hasContactsPermission(): Boolean {
        if (!isContactsPermissionDeclaredInPackage()) return false
        val ctx = context ?: return false
        return ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    open suspend fun searchContacts(query: String, limit: Int = 6): List<ContactSearchResult> =
        withContext(dispatchers?.io ?: kotlinx.coroutines.Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty() || !hasContactsPermission()) return@withContext emptyList()
            val ctx = context ?: return@withContext emptyList()

            safeSystemCall {
                val filterUri = Uri.withAppendedPath(
                    ContactsContract.Contacts.CONTENT_FILTER_URI,
                    Uri.encode(trimmed),
                )
                val projection = arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                )
                val results = ArrayList<ContactSearchResult>()
                ctx.contentResolver.query(
                    filterUri,
                    projection,
                    null,
                    null,
                    "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val keyIdx = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                    while (cursor.moveToNext() && results.size < limit) {
                        if (idIdx < 0 || keyIdx < 0 || nameIdx < 0) break
                        val id = cursor.getLong(idIdx)
                        val lookupKey = cursor.getString(keyIdx).orEmpty()
                        val name = cursor.getString(nameIdx)?.trim().orEmpty()
                        if (name.isNotEmpty() && lookupKey.isNotEmpty()) {
                            val uri = ContactsContract.Contacts.getLookupUri(id, lookupKey)?.toString()
                            if (!uri.isNullOrBlank()) {
                                results += ContactSearchResult(
                                    contactId = id,
                                    displayName = name,
                                    lookupUri = uri,
                                )
                            }
                        }
                    }
                }
                results
            }.getOrNull().orEmpty()
        }

    open fun matchSystemSettings(query: String): List<QuickSettingShortcut> {
        val trimmed = query.trim().lowercase()
        if (trimmed.length < 2) return emptyList()
        return settingsShortcuts.filter { item ->
            item.keywords.any { kw -> kw.contains(trimmed) || trimmed.contains(kw) }
        }
    }

    open fun openContact(lookupUri: String): SystemResult<Unit> {
        val ctx = context ?: return SystemResult.Failure("No context available")
        return safeSystemCall {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(lookupUri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        }
    }

    open fun openSystemSetting(action: String): SystemResult<Unit> {
        val ctx = context ?: return SystemResult.Failure("No context available")
        return safeSystemCall {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(intent)
            } else {
                ctx.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    companion object {
        fun isContactsSupported(requestedPermissions: Set<String>): Boolean {
            return Manifest.permission.READ_CONTACTS in requestedPermissions
        }
    }
}
