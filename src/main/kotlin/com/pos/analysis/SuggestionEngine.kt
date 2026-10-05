package com.pos.analysis

import com.pos.model.AnalysisSuggestion
import com.pos.model.ColumnType
import com.pos.model.DataQualityIssue
import com.pos.model.DatasetProfile
import com.pos.model.SuggestionConfidence

object SuggestionEngine {

    fun suggest(profile: DatasetProfile, issues: List<DataQualityIssue>): List<AnalysisSuggestion> {
        val suggestions = mutableListOf<AnalysisSuggestion>()
        val columns = profile.columns

        val numericColumns = columns.filter { it.type == ColumnType.INTEGER || it.type == ColumnType.DECIMAL }
        val dateColumns = columns.filter { it.type == ColumnType.DATE || it.type == ColumnType.DATETIME }
        val categoricalColumns = columns.filter { it.type == ColumnType.CATEGORICAL }
        val freeTextColumns = columns.filter { it.type == ColumnType.FREE_TEXT }

        val smallSample = profile.rowCount < 30
        val sampleCaveat = "Sample size is small (${profile.rowCount} rows); results may be unreliable"
        fun caveats(vararg extra: String) = (extra.toList() + if (smallSample) listOf(sampleCaveat) else emptyList())

        if (dateColumns.isNotEmpty() && numericColumns.isNotEmpty()) {
            suggestions.add(
                AnalysisSuggestion(
                    type = "Time-series analysis",
                    rationale = "Dataset has a date/time column ('${dateColumns.first().name}') and numeric metric(s) " +
                        "(${numericColumns.joinToString(", ") { it.name }}) suitable for tracking trends over time",
                    confidence = if (numericColumns.size > 1) SuggestionConfidence.HIGH else SuggestionConfidence.MEDIUM,
                    caveats = caveats(),
                ),
            )
        }

        if (numericColumns.size >= 2) {
            suggestions.add(
                AnalysisSuggestion(
                    type = "Correlation analysis",
                    rationale = "${numericColumns.size} numeric columns present (${numericColumns.joinToString(", ") { it.name }}), " +
                        "suitable for examining pairwise relationships",
                    confidence = SuggestionConfidence.HIGH,
                    caveats = caveats(),
                ),
            )
        }

        val regressionTarget = numericColumns.lastOrNull { it.stats.distinctCount > 1 }
        val regressionPredictors = numericColumns.filter { it !== regressionTarget }
        if (regressionTarget != null && regressionPredictors.isNotEmpty()) {
            suggestions.add(
                AnalysisSuggestion(
                    type = "Regression",
                    rationale = "Numeric column '${regressionTarget.name}' could be predicted from other numeric column(s): " +
                        regressionPredictors.joinToString(", ") { it.name },
                    confidence = SuggestionConfidence.MEDIUM,
                    caveats = caveats(),
                ),
            )
        }

        val classificationTarget = categoricalColumns.firstOrNull { it.stats.distinctCount in 2..20 }
        if (classificationTarget != null && (numericColumns.isNotEmpty() || categoricalColumns.size > 1)) {
            val isImbalanced = issues.any {
                it.columnName == classificationTarget.name && it.category == "distribution" && it.message.contains("Class imbalance")
            }
            suggestions.add(
                AnalysisSuggestion(
                    type = "Classification",
                    rationale = "Categorical column '${classificationTarget.name}' has ${classificationTarget.stats.distinctCount} " +
                        "distinct values and could serve as a classification target",
                    confidence = if (isImbalanced) SuggestionConfidence.LOW else SuggestionConfidence.MEDIUM,
                    caveats = caveats(
                        *(if (isImbalanced) arrayOf("Target column '${classificationTarget.name}' is imbalanced — consider resampling or weighted metrics") else emptyArray()),
                    ),
                ),
            )
        }

        if (numericColumns.size >= 3 && regressionTarget == null) {
            suggestions.add(
                AnalysisSuggestion(
                    type = "Clustering",
                    rationale = "${numericColumns.size} numeric columns with no obvious target column suggest unsupervised grouping (e.g. k-means)",
                    confidence = SuggestionConfidence.MEDIUM,
                    caveats = caveats(),
                ),
            )
        }

        if (columns.isNotEmpty() && freeTextColumns.size.toDouble() / columns.size > 0.5) {
            suggestions.add(
                AnalysisSuggestion(
                    type = "Text analysis / NLP",
                    rationale = "Over half of the columns (${freeTextColumns.joinToString(", ") { it.name }}) are free text, " +
                        "which may not suit standard tabular modeling",
                    confidence = SuggestionConfidence.LOW,
                ),
            )
        }

        if (numericColumns.isEmpty() && categoricalColumns.isNotEmpty()) {
            suggestions.add(
                AnalysisSuggestion(
                    type = "Categorical frequency analysis",
                    rationale = "No numeric columns were found; regression and clustering are not applicable, " +
                        "but categorical frequency/association analysis is possible",
                    confidence = SuggestionConfidence.MEDIUM,
                ),
            )
        }

        val excludable = columns.filter { it.type == ColumnType.CONSTANT || it.type == ColumnType.EMPTY }
        if (excludable.isNotEmpty()) {
            suggestions.add(
                AnalysisSuggestion(
                    type = "Data preparation note",
                    rationale = "Column(s) ${excludable.joinToString(", ") { it.name }} are constant or empty and should be excluded from any modeling",
                    confidence = SuggestionConfidence.HIGH,
                ),
            )
        }

        if (suggestions.isEmpty()) {
            suggestions.add(
                AnalysisSuggestion(
                    type = "Exploratory analysis only",
                    rationale = "No strong patterns were detected to suggest a specific analysis type; consider manual exploratory data analysis",
                    confidence = SuggestionConfidence.LOW,
                    caveats = caveats(),
                ),
            )
        }

        return suggestions
    }
}
