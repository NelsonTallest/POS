package com.pos.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object Db {
    fun init(dbPath: String = "./data/pos.db") {
        File(dbPath).parentFile?.mkdirs()
        Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(Uploads, AnalysisResults)
        }
    }
}
