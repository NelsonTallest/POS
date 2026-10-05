package com.pos.db

import com.pos.model.AnalysisResult
import com.pos.model.AnalysisSuggestion
import com.pos.model.DataQualityIssue
import com.pos.model.DatasetProfile
import com.pos.model.UploadSummary
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object AnalysisRepository {
    private val json = Json { ignoreUnknownKeys = true }

    fun save(
        fileName: String,
        fileType: String,
        rowCount: Int,
        columnCount: Int,
        profile: DatasetProfile,
        issues: List<DataQualityIssue>,
        suggestions: List<AnalysisSuggestion>,
        timestamp: String,
    ): Long = transaction {
        val uploadId = Uploads.insert {
            it[Uploads.fileName] = fileName
            it[Uploads.fileType] = fileType
            it[Uploads.rowCount] = rowCount
            it[Uploads.columnCount] = columnCount
            it[Uploads.uploadedAt] = timestamp
        }[Uploads.id]

        AnalysisResults.insert {
            it[AnalysisResults.uploadId] = uploadId
            it[profileJson] = json.encodeToString(profile)
            it[issuesJson] = json.encodeToString(issues)
            it[suggestionsJson] = json.encodeToString(suggestions)
            it[createdAt] = timestamp
        }

        uploadId.toLong()
    }

    fun listHistory(limit: Int = 100): List<UploadSummary> = transaction {
        Uploads.selectAll()
            .orderBy(Uploads.id, SortOrder.DESC)
            .limit(limit)
            .map {
                UploadSummary(
                    id = it[Uploads.id].toLong(),
                    fileName = it[Uploads.fileName],
                    uploadedAt = it[Uploads.uploadedAt],
                    rowCount = it[Uploads.rowCount],
                    columnCount = it[Uploads.columnCount],
                )
            }
    }

    fun findById(id: Long): AnalysisResult? = transaction {
        val uploadRow = Uploads.selectAll().where { Uploads.id eq id.toInt() }.singleOrNull() ?: return@transaction null
        val resultRow = AnalysisResults.selectAll().where { AnalysisResults.uploadId eq id.toInt() }.singleOrNull()
            ?: return@transaction null

        val profile = json.decodeFromString<DatasetProfile>(resultRow[AnalysisResults.profileJson])
        AnalysisResult(
            uploadId = id,
            fileName = uploadRow[Uploads.fileName],
            uploadedAt = uploadRow[Uploads.uploadedAt],
            rowCount = uploadRow[Uploads.rowCount],
            columnCount = uploadRow[Uploads.columnCount],
            columns = profile.columns,
            issues = json.decodeFromString<List<DataQualityIssue>>(resultRow[AnalysisResults.issuesJson]),
            suggestions = json.decodeFromString<List<AnalysisSuggestion>>(resultRow[AnalysisResults.suggestionsJson]),
            datasetWarnings = profile.warnings,
        )
    }
}
