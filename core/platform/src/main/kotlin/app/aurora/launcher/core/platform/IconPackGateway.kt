package app.aurora.launcher.core.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import app.aurora.launcher.core.common.AuroraDispatchers
import app.aurora.launcher.core.common.safeSystemCall
import app.aurora.launcher.core.model.ComponentKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class IconPackCandidate(
    val packageName: String,
    val label: String,
)

internal object IconPackXmlParser {
    private val componentRegex = Regex("""ComponentInfo\{([^/]+)/([^}]+)\}""")

    fun parseComponentAttr(componentAttr: String?): Pair<String, String>? {
        if (componentAttr.isNullOrBlank()) return null
        val match = componentRegex.find(componentAttr) ?: return null
        val pkg = match.groupValues[1].trim()
        val rawCls = match.groupValues[2].trim()
        if (pkg.isEmpty() || rawCls.isEmpty()) return null
        val fullCls = if (rawCls.startsWith(".")) "$pkg$rawCls" else rawCls
        return pkg to fullCls
    }

    fun parseAppFilter(parser: XmlPullParser): Map<String, String> {
        val mapping = HashMap<String, String>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                val parsed = parseComponentAttr(component)
                if (parsed != null && !drawable.isNullOrBlank()) {
                    mapping["${parsed.first}/${parsed.second}"] = drawable
                }
            }
            eventType = parser.next()
        }
        return mapping
    }
}

open class IconPackGateway(
    private val context: Context? = null,
    private val dispatchers: AuroraDispatchers? = null,
) {
    private val parsedPacks = ConcurrentHashMap<String, Map<String, String>>()

    open suspend fun discoverIconPacks(): List<IconPackCandidate> =
        withContext(dispatchers?.io ?: kotlinx.coroutines.Dispatchers.IO) {
            val ctx = context ?: return@withContext emptyList()
            val pm = ctx.packageManager
            val actions = listOf(
                "org.adw.launcher.THEMES",
                "com.novalauncher.THEME",
                "com.gau.go.launcherex.theme",
            )
            val discovered = LinkedHashMap<String, IconPackCandidate>()
            for (action in actions) {
                val resolved = safeSystemCall {
                    pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
                }.getOrNull().orEmpty()
                for (info in resolved) {
                    val pkg = info.activityInfo?.packageName ?: continue
                    if (pkg == ctx.packageName || discovered.containsKey(pkg)) continue
                    val label = safeSystemCall { info.loadLabel(pm).toString() }.getOrNull() ?: pkg
                    discovered[pkg] = IconPackCandidate(packageName = pkg, label = label)
                }
            }
            discovered.values.sortedBy { it.label.lowercase() }
        }

    suspend fun loadCustomDrawable(
        packPackage: String,
        key: ComponentKey,
    ): Drawable? = withContext(dispatchers?.io ?: kotlinx.coroutines.Dispatchers.IO) {
        val ctx = context ?: return@withContext null
        val mapping = parsedPacks[packPackage] ?: loadPackMapping(packPackage).also {
            parsedPacks[packPackage] = it
        }
        val drawableName = mapping["${key.packageName}/${key.activityClass}"] ?: return@withContext null
        safeSystemCall {
            val packRes: Resources = ctx.packageManager.getResourcesForApplication(packPackage)
            val resId = packRes.getIdentifier(drawableName, "drawable", packPackage)
            if (resId == 0) null else ResourcesCompat.getDrawable(packRes, resId, null)
        }.getOrNull()
    }

    fun clearCache() {
        parsedPacks.clear()
    }

    private fun loadPackMapping(packPackage: String): Map<String, String> {
        val ctx = context ?: return emptyMap()
        return safeSystemCall {
            val packRes = ctx.packageManager.getResourcesForApplication(packPackage)
            val xmlId = packRes.getIdentifier("appfilter", "xml", packPackage)
            if (xmlId != 0) {
                packRes.getXml(xmlId).use { parser ->
                    return@safeSystemCall IconPackXmlParser.parseAppFilter(parser)
                }
            }
            packRes.assets.open("appfilter.xml").use { stream ->
                val factory = XmlPullParserFactory.newInstance().apply {
                    isNamespaceAware = false
                }
                val parser = factory.newPullParser()
                parser.setInput(stream, Charsets.UTF_8.name())
                IconPackXmlParser.parseAppFilter(parser)
            }
        }.getOrNull().orEmpty()
    }
}
