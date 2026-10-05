package com.pos.db

import org.jetbrains.exposed.sql.Table

object Uploads : Table("uploads") {
    val id = integer("id").autoIncrement()
    val fileName = text("file_name")
    val fileType = varchar("file_type", 16)
    val rowCount = integer("row_count")
    val columnCount = integer("column_count")
    val uploadedAt = text("uploaded_at")
    override val primaryKey = PrimaryKey(id)
}

object AnalysisResults : Table("analysis_results") {
    val id = integer("id").autoIncrement()
    val uploadId = integer("upload_id").references(Uploads.id)
    val profileJson = text("profile_json")
    val issuesJson = text("issues_json")
    val suggestionsJson = text("suggestions_json")
    val createdAt = text("created_at")
    override val primaryKey = PrimaryKey(id)
}
