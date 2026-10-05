package com.pos.model

import kotlinx.serialization.Serializable

@Serializable
data class AnalysisResult(
    val uploadId: Long,
    val fileName: String,
    val uploadedAt: String,
    val rowCount: Int,
    val columnCount: Int,
    val columns: List<ColumnProfile>,
    val issues: List<DataQualityIssue>,
    val suggestions: List<AnalysisSuggestion>,
    val datasetWarnings: List<String> = emptyList(),
)

@Serializable
data class UploadSummary(
    val id: Long,
    val fileName: String,
    val uploadedAt: String,
    val rowCount: Int,
    val columnCount: Int,
)
