package com.pos.routes

import com.pos.db.AnalysisRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.historyRoutes() {
    get("/history") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
        call.respond(AnalysisRepository.listHistory(limit))
    }

    get("/history/{id}") {
        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid id"))
            return@get
        }
        val result = AnalysisRepository.findById(id)
        if (result == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "No analysis found for id $id"))
            return@get
        }
        call.respond(result)
    }
}
