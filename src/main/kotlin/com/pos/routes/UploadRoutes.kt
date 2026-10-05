package com.pos.routes

import com.pos.analysis.DatasetAnalyzer
import com.pos.db.AnalysisRepository
import com.pos.model.AnalysisResult
import com.pos.model.DatasetLimits
import com.pos.parser.ParserRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.core.Input
import io.ktor.utils.io.core.readAvailable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant

private fun Input.readUpTo(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (!endOfInput && output.size() <= limit) {
        val read = readAvailable(chunk, 0, chunk.size)
        if (read <= 0) break
        output.write(chunk, 0, read)
    }
    return output.toByteArray()
}

fun Route.uploadRoutes() {
    post("/upload") {
        var fileName: String? = null
        var fileBytes: ByteArray? = null

        try {
            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                if (part is PartData.FileItem && fileBytes == null) {
                    fileName = part.originalFileName
                    fileBytes = part.provider().readUpTo(DatasetLimits.MAX_UPLOAD_BYTES.toInt() + 1)
                }
                part.dispose()
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Malformed upload request: ${e.message}"))
            return@post
        }

        val name = fileName
        val bytes = fileBytes
        if (name.isNullOrBlank() || bytes == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file was uploaded"))
            return@post
        }
        if (bytes.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Uploaded file is empty"))
            return@post
        }
        if (bytes.size > DatasetLimits.MAX_UPLOAD_BYTES) {
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                mapOf("error" to "File exceeds the ${DatasetLimits.MAX_UPLOAD_BYTES / (1024 * 1024)}MB upload limit"),
            )
            return@post
        }

        val parser = ParserRegistry.forFile(name)
        val dataset = parser.parse(name, ByteArrayInputStream(bytes))
        val bundle = DatasetAnalyzer.analyze(dataset)

        val fileType = name.substringAfterLast('.', "unknown").lowercase()
        val timestamp = Instant.now().toString()

        val uploadId = AnalysisRepository.save(
            fileName = name,
            fileType = fileType,
            rowCount = bundle.profile.rowCount,
            columnCount = bundle.profile.columnCount,
            profile = bundle.profile,
            issues = bundle.issues,
            suggestions = bundle.suggestions,
            timestamp = timestamp,
        )

        call.respond(
            AnalysisResult(
                uploadId = uploadId,
                fileName = name,
                uploadedAt = timestamp,
                rowCount = bundle.profile.rowCount,
                columnCount = bundle.profile.columnCount,
                columns = bundle.profile.columns,
                issues = bundle.issues,
                suggestions = bundle.suggestions,
                datasetWarnings = bundle.profile.warnings,
            ),
        )
    }
}
