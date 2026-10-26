package app.aurora.launcher.core.model

import java.text.Normalizer
import java.util.Locale

data class AppClassificationHints(
    val handlesCameraIntent: Boolean = false,
    val handlesAudioVideoIntent: Boolean = false,
    val handlesBrowserIntent: Boolean = false,
    val handlesMapsIntent: Boolean = false,
    val handlesShoppingIntent: Boolean = false,
)

object AppLibraryClassifier {
    // Mirrors android.content.pm.ApplicationInfo category constants without importing Android SDK
    const val CATEGORY_UNDEFINED: Int = -1
    const val CATEGORY_GAME: Int = 0
    const val CATEGORY_AUDIO: Int = 1
    const val CATEGORY_VIDEO: Int = 2
    const val CATEGORY_IMAGE: Int = 3
    const val CATEGORY_SOCIAL: Int = 4
    const val CATEGORY_NEWS: Int = 5
    const val CATEGORY_MAPS: Int = 6
    const val CATEGORY_PRODUCTIVITY: Int = 7
    const val CATEGORY_ACCESSIBILITY: Int = 8

    private val exactPackageMap: Map<String, LibraryGroup> = mapOf(
        "com.whatsapp" to LibraryGroup.SOCIAL,
        "com.whatsapp.w4b" to LibraryGroup.SOCIAL,
        "org.telegram.messenger" to LibraryGroup.SOCIAL,
        "com.instagram.android" to LibraryGroup.SOCIAL,
        "com.facebook.katana" to LibraryGroup.SOCIAL,
        "com.facebook.orca" to LibraryGroup.SOCIAL,
        "com.twitter.android" to LibraryGroup.SOCIAL,
        "com.discord" to LibraryGroup.SOCIAL,
        "com.linecorp.LGGRTHN" to LibraryGroup.SOCIAL,
        "jp.naver.line.android" to LibraryGroup.SOCIAL,
        "com.google.android.apps.messaging" to LibraryGroup.SOCIAL,
        "com.google.android.dialer" to LibraryGroup.UTILITIES,
        "com.android.dialer" to LibraryGroup.UTILITIES,
        "com.android.contacts" to LibraryGroup.SOCIAL,
        "com.google.android.contacts" to LibraryGroup.SOCIAL,
        "com.google.android.youtube" to LibraryGroup.ENTERTAINMENT,
        "com.spotify.music" to LibraryGroup.ENTERTAINMENT,
        "com.netflix.mediaclient" to LibraryGroup.ENTERTAINMENT,
        "com.zhiliaoapp.musically" to LibraryGroup.ENTERTAINMENT,
        "com.ss.android.ugc.trill" to LibraryGroup.ENTERTAINMENT,
        "com.google.android.apps.photos" to LibraryGroup.PHOTO_VIDEO,
        "com.android.camera" to LibraryGroup.PHOTO_VIDEO,
        "com.android.camera2" to LibraryGroup.PHOTO_VIDEO,
        "com.google.android.GoogleCamera" to LibraryGroup.PHOTO_VIDEO,
        "com.google.android.gm" to LibraryGroup.PRODUCTIVITY_FINANCE,
        "com.google.android.calendar" to LibraryGroup.PRODUCTIVITY_FINANCE,
        "com.google.android.apps.docs" to LibraryGroup.PRODUCTIVITY_FINANCE,
        "com.google.android.keep" to LibraryGroup.PRODUCTIVITY_FINANCE,
        "com.microsoft.office.outlook" to LibraryGroup.PRODUCTIVITY_FINANCE,
        "id.dana" to LibraryGroup.PRODUCTIVITY_FINANCE,
        "ovo.id" to LibraryGroup.PRODUCTIVITY_FINANCE,
        "com.bca" to LibraryGroup.PRODUCTIVITY_FINANCE,
        "src.com.bni" to LibraryGroup.PRODUCTIVITY_FINANCE,
        "id.co.bri.brimo" to LibraryGroup.PRODUCTIVITY_FINANCE,
        "com.shopee.id" to LibraryGroup.SHOPPING,
        "com.tokopedia.tkpd" to LibraryGroup.SHOPPING,
        "com.lazada.android" to LibraryGroup.SHOPPING,
        "blibli.mobile.commerce" to LibraryGroup.SHOPPING,
        "com.gojek.app" to LibraryGroup.TRAVEL,
        "com.grabtaxi.passenger" to LibraryGroup.TRAVEL,
        "com.traveloka.android" to LibraryGroup.TRAVEL,
        "com.google.android.apps.maps" to LibraryGroup.TRAVEL,
        "com.waze" to LibraryGroup.TRAVEL,
        "com.android.settings" to LibraryGroup.UTILITIES,
        "com.android.vending" to LibraryGroup.UTILITIES,
        "com.android.chrome" to LibraryGroup.UTILITIES,
        "org.mozilla.firefox" to LibraryGroup.UTILITIES,
        "com.google.android.calculator" to LibraryGroup.UTILITIES,
        "com.google.android.deskclock" to LibraryGroup.UTILITIES,
        "com.android.deskclock" to LibraryGroup.UTILITIES,
        "com.google.android.apps.nbu.files" to LibraryGroup.UTILITIES,
    )

    fun classify(
        packageName: String,
        userOverride: LibraryGroup? = null,
        systemCategory: Int? = null,
        hints: AppClassificationHints = AppClassificationHints(),
    ): LibraryGroup {
        if (userOverride != null) return userOverride

        when (systemCategory) {
            CATEGORY_GAME -> return LibraryGroup.GAMES
            CATEGORY_SOCIAL -> return LibraryGroup.SOCIAL
            CATEGORY_AUDIO, CATEGORY_VIDEO, CATEGORY_NEWS -> return LibraryGroup.ENTERTAINMENT
            CATEGORY_IMAGE -> return LibraryGroup.PHOTO_VIDEO
            CATEGORY_MAPS -> return LibraryGroup.TRAVEL
            CATEGORY_PRODUCTIVITY -> return LibraryGroup.PRODUCTIVITY_FINANCE
            CATEGORY_ACCESSIBILITY -> return LibraryGroup.UTILITIES
        }

        val normalizedPkg = packageName.lowercase(Locale.ROOT)
        exactPackageMap[packageName]?.let { return it }

        when {
            normalizedPkg.contains(".game") || normalizedPkg.startsWith("com.supercell.") ||
                normalizedPkg.startsWith("com.roblox.") || normalizedPkg.startsWith("com.miHoYo.") ->
                return LibraryGroup.GAMES

            normalizedPkg.contains("camera") || normalizedPkg.contains("gallery") ||
                normalizedPkg.contains("photo") || normalizedPkg.contains("videoeditor") ->
                return LibraryGroup.PHOTO_VIDEO

            normalizedPkg.contains("bank") || normalizedPkg.contains("wallet") ||
                normalizedPkg.contains("finance") || normalizedPkg.contains("notes") ||
                normalizedPkg.contains("calendar") || normalizedPkg.contains("office") ||
                normalizedPkg.contains("mail") ->
                return LibraryGroup.PRODUCTIVITY_FINANCE

            normalizedPkg.contains("shop") || normalizedPkg.contains("store") && !normalizedPkg.contains("vending") ||
                normalizedPkg.contains("ecommerce") ->
                return LibraryGroup.SHOPPING

            normalizedPkg.contains("maps") || normalizedPkg.contains("transit") ||
                normalizedPkg.contains("flight") || normalizedPkg.contains("travel") ->
                return LibraryGroup.TRAVEL

            normalizedPkg.contains("music") || normalizedPkg.contains("player") ||
                normalizedPkg.contains("podcast") || normalizedPkg.contains("radio") ->
                return LibraryGroup.ENTERTAINMENT

            normalizedPkg.contains("chat") || normalizedPkg.contains("messenger") ||
                normalizedPkg.contains("social") ->
                return LibraryGroup.SOCIAL

            normalizedPkg.contains("settings") || normalizedPkg.contains("calculator") ||
                normalizedPkg.contains("clock") || normalizedPkg.contains("file") ||
                normalizedPkg.contains("browser") || normalizedPkg.contains("chrome") ->
                return LibraryGroup.UTILITIES
        }

        return when {
            hints.handlesCameraIntent -> LibraryGroup.PHOTO_VIDEO
            hints.handlesMapsIntent -> LibraryGroup.TRAVEL
            hints.handlesShoppingIntent -> LibraryGroup.SHOPPING
            hints.handlesAudioVideoIntent -> LibraryGroup.ENTERTAINMENT
            hints.handlesBrowserIntent -> LibraryGroup.UTILITIES
            else -> LibraryGroup.OTHER
        }
    }

    /**
     * Normalizes Unicode diacritics and leading punctuation/emoji so app lists sort and group reliably (E33).
     */
    fun buildSortKey(label: String, locale: Locale = Locale.getDefault()): String {
        val decomposed = Normalizer.normalize(label.trim(), Normalizer.Form.NFKD)
        val withoutMarks = DIACRITICS_REGEX.replace(decomposed, "")
        val strippedLeadingSymbols = withoutMarks.dropWhile { ch ->
            !Character.isLetterOrDigit(ch)
        }
        val effective = strippedLeadingSymbols.ifEmpty { withoutMarks }
        return effective.lowercase(locale)
    }

    fun sectionInitial(label: String, locale: Locale = Locale.getDefault()): String {
        val key = buildSortKey(label, locale)
        val first = key.firstOrNull() ?: return "#"
        return if (first.isLetter()) {
            first.toString().uppercase(locale)
        } else {
            "#"
        }
    }

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
}
