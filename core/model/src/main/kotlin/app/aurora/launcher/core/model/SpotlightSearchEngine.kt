package app.aurora.launcher.core.model

import java.text.Normalizer
import java.util.Locale

data class QuickSettingAction(
    val id: String,
    val title: String,
    val subtitle: String,
    val keywords: List<String>,
)

data class ContactSearchResult(
    val id: Long,
    val displayName: String,
    val lookupUri: String,
)

data class SpotlightResults(
    val query: String,
    val suggestedApps: List<AppEntry>,
    val matchingApps: List<AppEntry>,
    val matchingSettings: List<QuickSettingAction>,
    val matchingContacts: List<ContactSearchResult>,
)

object SpotlightSearchEngine {
    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val WORD_SPLIT_REGEX = Regex("[\\s._\\-/]+|(?<=[a-z])(?=[A-Z])")

    fun search(
        query: String,
        apps: List<AppEntry>,
        quickSettings: List<QuickSettingAction> = emptyList(),
        contacts: List<ContactSearchResult> = emptyList(),
        maxSuggested: Int = 8,
    ): SpotlightResults {
        val visibleApps = apps.filter { !it.isHidden }
        val trimmed = query.trim()

        if (trimmed.isEmpty()) {
            val suggested = visibleApps
                .sortedWith(
                    compareByDescending<AppEntry> { it.launchCount }
                        .thenByDescending { it.lastLaunchEpochMs ?: 0L }
                        .thenBy { it.sortKey },
                )
                .take(maxSuggested)
            return SpotlightResults(
                query = "",
                suggestedApps = suggested,
                matchingApps = emptyList(),
                matchingSettings = emptyList(),
                matchingContacts = emptyList(),
            )
        }

        val normalizedQuery = normalizeText(trimmed)
        val scoredApps = visibleApps.mapNotNull { app ->
            val score = scoreMatch(normalizedQuery, app.label, app.launchCount)
            if (score > 0) app to score else null
        }
            .sortedWith(
                compareByDescending<Pair<AppEntry, Int>> { it.second }
                    .thenBy { it.first.sortKey },
            )
            .map { it.first }

        val matchedSettings = quickSettings.filter { action ->
            normalizeText(action.title).contains(normalizedQuery) ||
                action.keywords.any { normalizeText(it).contains(normalizedQuery) }
        }

        val matchedContacts = contacts.filter { contact ->
            scoreMatch(normalizedQuery, contact.displayName, 0) > 0
        }

        return SpotlightResults(
            query = trimmed,
            suggestedApps = emptyList(),
            matchingApps = scoredApps,
            matchingSettings = matchedSettings,
            matchingContacts = matchedContacts,
        )
    }

    fun scoreMatch(
        normalizedQuery: String,
        rawLabel: String,
        launchCount: Int = 0,
    ): Int {
        if (normalizedQuery.isEmpty()) return 0
        val normalizedLabel = normalizeText(rawLabel)
        if (normalizedLabel.isEmpty()) return 0

        val usageBoost = minOf(launchCount, 50)

        if (normalizedLabel == normalizedQuery) {
            return 1000 + usageBoost
        }
        if (normalizedLabel.startsWith(normalizedQuery)) {
            return 850 + usageBoost
        }

        val words = WORD_SPLIT_REGEX.split(rawLabel.trim())
            .map { normalizeText(it) }
            .filter { it.isNotEmpty() }

        if (words.any { it.startsWith(normalizedQuery) }) {
            return 720 + usageBoost
        }

        val initials = words.joinToString("") { it.take(1) }
        if (initials.length >= 2 && initials.startsWith(normalizedQuery)) {
            return 650 + usageBoost
        }

        if (normalizedLabel.contains(normalizedQuery)) {
            return 450 + usageBoost
        }

        return 0
    }

    fun normalizeText(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFKD)
        return DIACRITICS_REGEX.replace(decomposed, "").lowercase(Locale.ROOT).trim()
    }
}
