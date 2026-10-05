package com.pos.analysis

import com.pos.model.CellType
import com.pos.model.CellValue
import com.pos.model.ColumnStats
import com.pos.model.ColumnType
import com.pos.model.ParsedColumn
import com.pos.model.ValueCount
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object ColumnClassifier {

    private val BOOLEAN_SETS = listOf(
        setOf("true", "false"),
        setOf("yes", "no"),
        setOf("y", "n"),
        setOf("0", "1"),
    )

    private data class DatePattern(val formatter: DateTimeFormatter, val hasTime: Boolean, val label: String)

    private val DATE_PATTERNS = listOf(
        DatePattern(DateTimeFormatter.ISO_LOCAL_DATE_TIME, true, "ISO-8601 datetime"),
        DatePattern(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"), true, "yyyy-MM-dd HH:mm:ss"),
        DatePattern(DateTimeFormatter.ISO_LOCAL_DATE, false, "ISO-8601 date"),
        DatePattern(DateTimeFormatter.ofPattern("yyyy-MM-dd"), false, "yyyy-MM-dd"),
        DatePattern(DateTimeFormatter.ofPattern("dd/MM/yyyy"), false, "dd/MM/yyyy"),
        DatePattern(DateTimeFormatter.ofPattern("MM/dd/yyyy"), false, "MM/dd/yyyy"),
        DatePattern(DateTimeFormatter.ofPattern("dd-MM-yyyy"), false, "dd-MM-yyyy"),
        DatePattern(DateTimeFormatter.ofPattern("dd-MMM-yyyy"), false, "dd-MMM-yyyy"),
    )

    private const val DATE_MATCH_THRESHOLD = 0.9
    private const val NUMERIC_MATCH_THRESHOLD = 0.95
    private const val IDENTIFIER_DISTINCT_RATIO = 0.98
    private const val CATEGORICAL_MAX_DISTINCT = 50
    private const val CATEGORICAL_MAX_RATIO = 0.2

    private val INTEGER_REGEX = Regex("^[+-]?\\d+$")
    private val DECIMAL_REGEX = Regex("^[+-]?\\d*\\.?\\d+([eE][+-]?\\d+)?$")

    data class ClassificationResult(
        val type: ColumnType,
        val confidence: Double,
        val stats: ColumnStats,
        /** Pattern label -> match count. Only populated for DATE/DATETIME columns. */
        val matchedDatePatterns: Map<String, Int> = emptyMap(),
        /** CellType name -> fraction of non-null values. Only populated when a column mixes native types. */
        val mixedTypeBreakdown: Map<String, Double> = emptyMap(),
    )

    fun classify(column: ParsedColumn, values: List<CellValue>): ClassificationResult {
        val total = values.size
        val nonNull = values.filter { !it.isNull }
        val missingCount = total - nonNull.size
        val missingPercent = if (total == 0) 0.0 else missingCount * 100.0 / total

        if (nonNull.isEmpty()) {
            return ClassificationResult(ColumnType.EMPTY, 1.0, ColumnStats(missingCount, missingPercent, 0))
        }

        val rawValues = nonNull.map { it.raw ?: "" }
        val distinctCount = rawValues.toSet().size

        if (distinctCount == 1) {
            return ClassificationResult(
                ColumnType.CONSTANT, 1.0,
                ColumnStats(missingCount, missingPercent, distinctCount, topValues = topValues(rawValues)),
            )
        }

        // LONG and DOUBLE are both "numeric" for mixed-type purposes; a column of whole and
        // fractional numbers from XLSX/JSON shouldn't be flagged as mixing types.
        val typeCounts = nonNull.groupingBy { typeGroup(it.type) }.eachCount()
        val mixedBreakdown = if (typeCounts.size > 1) {
            typeCounts.mapValues { it.value.toDouble() / nonNull.size }
        } else {
            emptyMap()
        }

        booleanConfidence(rawValues)?.let { confidence ->
            return ClassificationResult(
                ColumnType.BOOLEAN, confidence,
                ColumnStats(missingCount, missingPercent, distinctCount, topValues = topValues(rawValues)),
                mixedTypeBreakdown = mixedBreakdown,
            )
        }

        val ratio = distinctCount.toDouble() / nonNull.size
        if (ratio >= IDENTIFIER_DISTINCT_RATIO && looksLikeIdColumnName(column.name)) {
            return ClassificationResult(
                ColumnType.IDENTIFIER, ratio,
                ColumnStats(missingCount, missingPercent, distinctCount),
                mixedTypeBreakdown = mixedBreakdown,
            )
        }

        integerCheck(rawValues)?.let { (confidence, longs) ->
            return ClassificationResult(
                ColumnType.INTEGER, confidence,
                numericStats(longs.map { it.toDouble() }, missingCount, missingPercent, distinctCount),
                mixedTypeBreakdown = mixedBreakdown,
            )
        }

        decimalCheck(rawValues)?.let { (confidence, doubles) ->
            return ClassificationResult(
                ColumnType.DECIMAL, confidence,
                numericStats(doubles, missingCount, missingPercent, distinctCount),
                mixedTypeBreakdown = mixedBreakdown,
            )
        }

        dateCheck(rawValues)?.let { (type, confidence, patternCounts) ->
            return ClassificationResult(
                type, confidence,
                ColumnStats(missingCount, missingPercent, distinctCount, topValues = topValues(rawValues)),
                matchedDatePatterns = patternCounts,
                mixedTypeBreakdown = mixedBreakdown,
            )
        }

        if (ratio >= IDENTIFIER_DISTINCT_RATIO && looksLikeIdentifierShape(rawValues)) {
            return ClassificationResult(
                ColumnType.IDENTIFIER, ratio,
                ColumnStats(missingCount, missingPercent, distinctCount),
                mixedTypeBreakdown = mixedBreakdown,
            )
        }

        val avgLength = rawValues.map { it.length }.average()
        val isCategorical = (distinctCount <= CATEGORICAL_MAX_DISTINCT && ratio <= CATEGORICAL_MAX_RATIO) ||
            (avgLength < 20 && ratio <= 0.5)

        return if (isCategorical) {
            ClassificationResult(
                ColumnType.CATEGORICAL, 1.0 - ratio,
                ColumnStats(missingCount, missingPercent, distinctCount, topValues = topValues(rawValues)),
                mixedTypeBreakdown = mixedBreakdown,
            )
        } else {
            ClassificationResult(
                ColumnType.FREE_TEXT, 1.0,
                ColumnStats(missingCount, missingPercent, distinctCount, topValues = topValues(rawValues)),
                mixedTypeBreakdown = mixedBreakdown,
            )
        }
    }

    private fun booleanConfidence(rawValues: List<String>): Double? {
        val normalized = rawValues.map { it.trim().lowercase() }.toSet()
        if (normalized.size > 2) return null
        return if (BOOLEAN_SETS.any { normalized.isNotEmpty() && normalized.all { v -> v in it } }) 1.0 else null
    }

    private fun integerCheck(rawValues: List<String>): Pair<Double, List<Long>>? {
        val longs = mutableListOf<Long>()
        var matchCount = 0
        for (v in rawValues) {
            val trimmed = v.trim()
            if (INTEGER_REGEX.matches(trimmed)) {
                trimmed.toLongOrNull()?.let { longs.add(it); matchCount++ }
            }
        }
        val confidence = matchCount.toDouble() / rawValues.size
        return if (confidence >= NUMERIC_MATCH_THRESHOLD) confidence to longs else null
    }

    private fun decimalCheck(rawValues: List<String>): Pair<Double, List<Double>>? {
        val doubles = mutableListOf<Double>()
        var matchCount = 0
        for (v in rawValues) {
            val trimmed = v.trim()
            if (DECIMAL_REGEX.matches(trimmed)) {
                trimmed.toDoubleOrNull()?.let { doubles.add(it); matchCount++ }
            }
        }
        val confidence = matchCount.toDouble() / rawValues.size
        return if (confidence >= NUMERIC_MATCH_THRESHOLD) confidence to doubles else null
    }

    private fun dateCheck(rawValues: List<String>): Triple<ColumnType, Double, Map<String, Int>>? {
        val patternCounts = mutableMapOf<String, Int>()
        var hasTimeComponent = false
        var matchCount = 0
        for (v in rawValues) {
            val trimmed = v.trim()
            val matched = DATE_PATTERNS.firstOrNull { pattern ->
                try {
                    if (pattern.hasTime) LocalDateTime.parse(trimmed, pattern.formatter) else LocalDate.parse(trimmed, pattern.formatter)
                    true
                } catch (e: DateTimeParseException) {
                    false
                }
            }
            if (matched != null) {
                matchCount++
                patternCounts.merge(matched.label, 1, Int::plus)
                if (matched.hasTime) hasTimeComponent = true
            }
        }
        val confidence = matchCount.toDouble() / rawValues.size
        if (confidence < DATE_MATCH_THRESHOLD) return null
        return Triple(if (hasTimeComponent) ColumnType.DATETIME else ColumnType.DATE, confidence, patternCounts)
    }

    private fun looksLikeIdColumnName(columnName: String): Boolean {
        val name = columnName.trim().lowercase()
        return name == "id" || name.endsWith("id") || name.endsWith("_id") || name.endsWith("key") || name.contains("uuid")
    }

    private fun typeGroup(type: CellType): String = when (type) {
        CellType.LONG, CellType.DOUBLE -> "NUMERIC"
        else -> type.name
    }

    /** Identifiers/codes (e.g. UUIDs) are short, whitespace-free tokens of fairly consistent length — unlike prose. */
    private fun looksLikeIdentifierShape(rawValues: List<String>): Boolean {
        val trimmed = rawValues.map { it.trim() }
        val noWhitespace = trimmed.all { it.none { ch -> ch.isWhitespace() } }
        val uniformLength = trimmed.map { it.length }.distinct().size <= 2
        val shortEnough = trimmed.map { it.length }.average() <= 40.0
        return noWhitespace && uniformLength && shortEnough
    }

    private fun numericStats(values: List<Double>, missingCount: Int, missingPercent: Double, distinctCount: Int): ColumnStats {
        val sorted = values.sorted()
        val mean = values.average()
        val variance = if (values.size > 1) values.sumOf { (it - mean) * (it - mean) } / (values.size - 1) else 0.0
        return ColumnStats(
            missingCount = missingCount,
            missingPercent = missingPercent,
            distinctCount = distinctCount,
            min = sorted.firstOrNull(),
            max = sorted.lastOrNull(),
            mean = mean,
            stdDev = Math.sqrt(variance),
        )
    }

    private fun topValues(rawValues: List<String>, limit: Int = 10): List<ValueCount> =
        rawValues.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(limit)
            .map { ValueCount(it.key, it.value) }
}
