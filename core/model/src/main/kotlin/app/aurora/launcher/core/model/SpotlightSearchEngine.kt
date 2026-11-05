package app.aurora.launcher.core.model

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToLong

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

data class CalculationResult(
    val query: String,
    val result: String,
    val formattedDisplay: String,
)

data class SpotlightResults(
    val query: String,
    val suggestedApps: List<AppEntry>,
    val matchingApps: List<AppEntry>,
    val matchingSettings: List<QuickSettingAction>,
    val matchingContacts: List<ContactSearchResult>,
    val calculationResult: CalculationResult? = null,
)

object SpotlightSearchEngine {
    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val WORD_SPLIT_REGEX = Regex("[\\s._\\-/]+|(?<=[a-z])(?=[A-Z])")

    private val UNIT_CONVERSION_REGEX = Regex(
        """^([+-]?[0-9]+(?:\.[0-9]+)?)\s*([a-zA-Z°]+)\s+(?:to|ke|in|dalam|as|->|=)\s+([a-zA-Z°]+)$""",
        RegexOption.IGNORE_CASE,
    )

    private val PERCENT_OF_REGEX = Regex(
        """^([0-9]+(?:\.[0-9]+)?)\s*%\s*(?:of|dari|\*)\s*([0-9]+(?:\.[0-9]+)?)$""",
        RegexOption.IGNORE_CASE,
    )

    private val PERCENT_ADD_SUB_REGEX = Regex(
        """^([0-9]+(?:\.[0-9]+)?)\s*([+\-])\s*([0-9]+(?:\.[0-9]+)?)\s*%$""",
    )

    private enum class UnitCategory { LENGTH, MASS, TEMPERATURE }

    private data class UnitDef(
        val primarySymbol: String,
        val category: UnitCategory,
        val toBaseRatio: Double = 1.0,
    )

    private val UNIT_MAP = mapOf(
        // Length (base: meter)
        "km" to UnitDef("km", UnitCategory.LENGTH, 1000.0),
        "kilometer" to UnitDef("km", UnitCategory.LENGTH, 1000.0),
        "kilometers" to UnitDef("km", UnitCategory.LENGTH, 1000.0),
        "kilometre" to UnitDef("km", UnitCategory.LENGTH, 1000.0),
        "m" to UnitDef("m", UnitCategory.LENGTH, 1.0),
        "meter" to UnitDef("m", UnitCategory.LENGTH, 1.0),
        "meters" to UnitDef("m", UnitCategory.LENGTH, 1.0),
        "cm" to UnitDef("cm", UnitCategory.LENGTH, 0.01),
        "centimeter" to UnitDef("cm", UnitCategory.LENGTH, 0.01),
        "centimeters" to UnitDef("cm", UnitCategory.LENGTH, 0.01),
        "sentimeter" to UnitDef("cm", UnitCategory.LENGTH, 0.01),
        "mm" to UnitDef("mm", UnitCategory.LENGTH, 0.001),
        "millimeter" to UnitDef("mm", UnitCategory.LENGTH, 0.001),
        "millimeters" to UnitDef("mm", UnitCategory.LENGTH, 0.001),
        "milimeter" to UnitDef("mm", UnitCategory.LENGTH, 0.001),
        "mi" to UnitDef("mi", UnitCategory.LENGTH, 1609.344),
        "mil" to UnitDef("mi", UnitCategory.LENGTH, 1609.344),
        "mile" to UnitDef("mi", UnitCategory.LENGTH, 1609.344),
        "miles" to UnitDef("mi", UnitCategory.LENGTH, 1609.344),
        "yd" to UnitDef("yd", UnitCategory.LENGTH, 0.9144),
        "yard" to UnitDef("yd", UnitCategory.LENGTH, 0.9144),
        "yards" to UnitDef("yd", UnitCategory.LENGTH, 0.9144),
        "ft" to UnitDef("ft", UnitCategory.LENGTH, 0.3048),
        "foot" to UnitDef("ft", UnitCategory.LENGTH, 0.3048),
        "feet" to UnitDef("ft", UnitCategory.LENGTH, 0.3048),
        "kaki" to UnitDef("ft", UnitCategory.LENGTH, 0.3048),
        "in" to UnitDef("in", UnitCategory.LENGTH, 0.0254),
        "inch" to UnitDef("in", UnitCategory.LENGTH, 0.0254),
        "inches" to UnitDef("in", UnitCategory.LENGTH, 0.0254),
        "inci" to UnitDef("in", UnitCategory.LENGTH, 0.0254),

        // Mass (base: gram)
        "kg" to UnitDef("kg", UnitCategory.MASS, 1000.0),
        "kilogram" to UnitDef("kg", UnitCategory.MASS, 1000.0),
        "kilograms" to UnitDef("kg", UnitCategory.MASS, 1000.0),
        "g" to UnitDef("g", UnitCategory.MASS, 1.0),
        "gram" to UnitDef("g", UnitCategory.MASS, 1.0),
        "grams" to UnitDef("g", UnitCategory.MASS, 1.0),
        "mg" to UnitDef("mg", UnitCategory.MASS, 0.001),
        "milligram" to UnitDef("mg", UnitCategory.MASS, 0.001),
        "milligrams" to UnitDef("mg", UnitCategory.MASS, 0.001),
        "miligram" to UnitDef("mg", UnitCategory.MASS, 0.001),
        "t" to UnitDef("t", UnitCategory.MASS, 1_000_000.0),
        "ton" to UnitDef("t", UnitCategory.MASS, 1_000_000.0),
        "tons" to UnitDef("t", UnitCategory.MASS, 1_000_000.0),
        "tonne" to UnitDef("t", UnitCategory.MASS, 1_000_000.0),
        "tonnes" to UnitDef("t", UnitCategory.MASS, 1_000_000.0),
        "lb" to UnitDef("lbs", UnitCategory.MASS, 453.59237),
        "lbs" to UnitDef("lbs", UnitCategory.MASS, 453.59237),
        "pound" to UnitDef("lbs", UnitCategory.MASS, 453.59237),
        "pounds" to UnitDef("lbs", UnitCategory.MASS, 453.59237),
        "pon" to UnitDef("lbs", UnitCategory.MASS, 453.59237),
        "oz" to UnitDef("oz", UnitCategory.MASS, 28.349523125),
        "ounce" to UnitDef("oz", UnitCategory.MASS, 28.349523125),
        "ounces" to UnitDef("oz", UnitCategory.MASS, 28.349523125),
        "ons" to UnitDef("oz", UnitCategory.MASS, 28.349523125),

        // Temperature
        "c" to UnitDef("°C", UnitCategory.TEMPERATURE),
        "°c" to UnitDef("°C", UnitCategory.TEMPERATURE),
        "celsius" to UnitDef("°C", UnitCategory.TEMPERATURE),
        "celcius" to UnitDef("°C", UnitCategory.TEMPERATURE),
        "f" to UnitDef("°F", UnitCategory.TEMPERATURE),
        "°f" to UnitDef("°F", UnitCategory.TEMPERATURE),
        "fahrenheit" to UnitDef("°F", UnitCategory.TEMPERATURE),
        "k" to UnitDef("K", UnitCategory.TEMPERATURE),
        "kelvin" to UnitDef("K", UnitCategory.TEMPERATURE),
    )

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
                calculationResult = null,
            )
        }

        val calculationResult = evaluateCalculationOrConversion(trimmed)

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
            calculationResult = calculationResult,
        )
    }

    fun evaluateCalculationOrConversion(query: String): CalculationResult? {
        val trimmed = query.trim()
        if (trimmed.length < 2) return null

        val unitResult = evaluateUnitConversion(trimmed)
        if (unitResult != null) return unitResult

        val percentResult = evaluatePercentageExpression(trimmed)
        if (percentResult != null) return percentResult

        return evaluateArithmeticExpression(trimmed)
    }

    fun evaluateUnitConversion(query: String): CalculationResult? {
        val trimmed = query.trim()
        val match = UNIT_CONVERSION_REGEX.find(trimmed)
            ?: run {
                val directMatch = Regex("""^([+-]?[0-9]+(?:\.[0-9]+)?)\s*([a-zA-Z°]+)\s+([a-zA-Z°]+)$""").find(trimmed)
                if (directMatch != null) {
                    val u1 = directMatch.groupValues[2].lowercase(Locale.ROOT)
                    val u2 = directMatch.groupValues[3].lowercase(Locale.ROOT)
                    if (UNIT_MAP.containsKey(u1) && UNIT_MAP.containsKey(u2)) directMatch else null
                } else null
            }
            ?: return null

        val rawVal = match.groupValues[1].toDoubleOrNull() ?: return null
        val fromKey = match.groupValues[2].lowercase(Locale.ROOT)
        val toKey = match.groupValues[3].lowercase(Locale.ROOT)

        val fromUnit = UNIT_MAP[fromKey] ?: return null
        val toUnit = UNIT_MAP[toKey] ?: return null
        if (fromUnit.category != toUnit.category) return null

        val resultValue = when (fromUnit.category) {
            UnitCategory.TEMPERATURE -> {
                val inCelsius = when (fromUnit.primarySymbol) {
                    "°C" -> rawVal
                    "°F" -> (rawVal - 32.0) * 5.0 / 9.0
                    "K" -> rawVal - 273.15
                    else -> rawVal
                }
                when (toUnit.primarySymbol) {
                    "°C" -> inCelsius
                    "°F" -> (inCelsius * 9.0 / 5.0) + 32.0
                    "K" -> inCelsius + 273.15
                    else -> inCelsius
                }
            }
            UnitCategory.LENGTH, UnitCategory.MASS -> {
                val inBase = rawVal * fromUnit.toBaseRatio
                inBase / toUnit.toBaseRatio
            }
        }

        val formattedResult = formatNumber(resultValue)
        val resultStr = "$formattedResult ${toUnit.primarySymbol}"
        val displayStr = "${formatNumber(rawVal)} ${fromUnit.primarySymbol} = $resultStr"
        return CalculationResult(
            query = trimmed,
            result = resultStr,
            formattedDisplay = displayStr,
        )
    }

    fun evaluatePercentageExpression(query: String): CalculationResult? {
        val trimmed = query.trim()
        val percentOfMatch = PERCENT_OF_REGEX.find(trimmed)
        if (percentOfMatch != null) {
            val percent = percentOfMatch.groupValues[1].toDoubleOrNull() ?: return null
            val total = percentOfMatch.groupValues[2].toDoubleOrNull() ?: return null
            val resultVal = (percent / 100.0) * total
            val formattedResult = formatNumber(resultVal)
            return CalculationResult(
                query = trimmed,
                result = formattedResult,
                formattedDisplay = "${formatNumber(percent)}% of ${formatNumber(total)} = $formattedResult",
            )
        }

        val addSubMatch = PERCENT_ADD_SUB_REGEX.find(trimmed)
        if (addSubMatch != null) {
            val base = addSubMatch.groupValues[1].toDoubleOrNull() ?: return null
            val op = addSubMatch.groupValues[2]
            val percent = addSubMatch.groupValues[3].toDoubleOrNull() ?: return null
            val delta = base * (percent / 100.0)
            val resultVal = if (op == "+") base + delta else base - delta
            val formattedResult = formatNumber(resultVal)
            return CalculationResult(
                query = trimmed,
                result = formattedResult,
                formattedDisplay = "${formatNumber(base)} $op ${formatNumber(percent)}% = $formattedResult",
            )
        }

        return null
    }

    fun evaluateArithmeticExpression(query: String): CalculationResult? {
        val trimmed = query.trim()
        if (trimmed.length > 100) return null

        val sanitized = trimmed
            .replace('×', '*')
            .replace('÷', '/')
            .replace('x', '*')
            .replace('X', '*')
            .replace(" ", "")

        val hasDigits = sanitized.any { it.isDigit() }
        val hasOperator = sanitized.any { it == '+' || it == '-' || it == '*' || it == '/' || it == '^' || it == '%' }
        if (!hasDigits || !hasOperator) return null

        // Reject plain numbers with unary signs (e.g. phone codes "+62", negative numbers "-5")
        if (sanitized.matches(Regex("""^[+-]?[0-9]+(?:\.[0-9]+)?$"""))) {
            return null
        }

        if (!sanitized.all { it.isDigit() || it == '.' || it == '+' || it == '-' || it == '*' || it == '/' || it == '^' || it == '%' || it == '(' || it == ')' }) {
            return null
        }

        return try {
            val parser = SimpleExprParser(sanitized)
            val resultVal = parser.parse()
            if (resultVal.isInfinite() || resultVal.isNaN()) null
            else {
                val formatted = formatNumber(resultVal)
                CalculationResult(
                    query = trimmed,
                    result = formatted,
                    formattedDisplay = "$trimmed = $formatted",
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun formatNumber(value: Double): String {
        if (value.isInfinite() || value.isNaN()) return ""
        val symbols = DecimalFormatSymbols(Locale.US)
        val df = DecimalFormat("0.####", symbols).apply {
            isGroupingUsed = false
            maximumFractionDigits = 4
            minimumFractionDigits = 0
        }
        val formatted = df.format(value)
        return if (formatted == "-0") "0" else formatted
    }

    private class SimpleExprParser(private val src: String) {
        private var pos = 0
        private var depth = 0
        private val maxDepth = 32

        fun parse(): Double {
            val result = parseExpression()
            if (pos < src.length) throw IllegalArgumentException("Unexpected character at $pos")
            return result
        }

        private fun parseExpression(): Double {
            var value = parseTerm()
            while (pos < src.length) {
                val op = src[pos]
                if (op != '+' && op != '-') break
                pos++
                val next = parseTerm()
                value = if (op == '+') value + next else value - next
            }
            return value
        }

        private fun parseTerm(): Double {
            var value = parsePower()
            while (pos < src.length) {
                val op = src[pos]
                if (op != '*' && op != '/' && op != '%') break
                pos++
                val next = parsePower()
                value = when (op) {
                    '*' -> value * next
                    '/' -> {
                        if (next == 0.0) throw ArithmeticException("Division by zero")
                        value / next
                    }
                    '%' -> value % next
                    else -> value
                }
            }
            return value
        }

        private fun parsePower(): Double {
            val base = parseFactor()
            if (pos < src.length && src[pos] == '^') {
                pos++
                val exp = parsePower()
                return base.pow(exp)
            }
            return base
        }

        private fun parseFactor(): Double {
            if (pos >= src.length) throw IllegalArgumentException("Unexpected end")
            val ch = src[pos]
            if (ch == '+') {
                pos++
                return parseFactor()
            }
            if (ch == '-') {
                pos++
                return -parseFactor()
            }
            if (ch == '(') {
                pos++
                depth++
                if (depth > maxDepth) throw IllegalArgumentException("Expression nested too deeply")
                val value = parseExpression()
                if (pos >= src.length || src[pos] != ')') throw IllegalArgumentException("Missing )")
                pos++
                depth--
                return value
            }
            val start = pos
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) {
                pos++
            }
            if (start == pos) throw IllegalArgumentException("Expected number at $pos")
            return src.substring(start, pos).toDouble()
        }
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
