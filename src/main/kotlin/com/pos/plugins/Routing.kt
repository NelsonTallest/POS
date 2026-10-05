package com.pos.plugins

import com.pos.routes.historyRoutes
import com.pos.routes.uploadRoutes
import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        staticResources("/", "static")
        route("/api") {
            uploadRoutes()
            historyRoutes()
        }
    }
}
