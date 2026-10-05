package com.pos.analysis

import com.pos.model.AnalysisSuggestion
import com.pos.model.ColumnProfile
import com.pos.model.DataQualityIssue
import com.pos.model.DatasetProfile
import com.pos.model.ParsedDataset

data class AnalysisBundle(
    val profile: DatasetProfile,
    val issues: List<DataQualityIssue>,
    val suggestions: List<AnalysisSuggestion>,
)

object DatasetAnalyzer {

    fun analyze(dataset: ParsedDataset): AnalysisBundle {
        val classifications = dataset.columns.map { col ->
            ColumnClassifier.classify(col, dataset.rows.map { it[col.index] })
        }

        val columnProfiles = dataset.columns.mapIndexed { i, col ->
            val result = classifications[i]
            ColumnProfile(
                name = col.name,
                index = col.index,
                type = result.type,
                confidence = result.confidence,
                stats = result.stats,
            )
        }

        val profile = DatasetProfile(
            rowCount = dataset.rows.size,
            columnCount = dataset.columns.size,
            columns = columnProfiles,
            warnings = dataset.warnings,
        )

        val issues = IssueDetector.detect(dataset, classifications)
        val suggestions = SuggestionEngine.suggest(profile, issues)

        return AnalysisBundle(profile, issues, suggestions)
    }
}
