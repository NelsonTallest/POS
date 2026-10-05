package com.pos

import com.pos.db.Db
import com.pos.plugins.configureRouting
import com.pos.plugins.configureSerialization
import com.pos.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging

fun main() {
    Db.init()
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    install(CallLogging)
    configureSerialization()
    configureStatusPages()
    configureRouting()
}
