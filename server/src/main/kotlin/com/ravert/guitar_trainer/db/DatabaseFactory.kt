package com.ravert.guitar_trainer.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI

object DatabaseFactory {
    fun init() {
        val cfg = dbConfigFromEnv()

        val config = HikariConfig().apply {
            jdbcUrl = cfg.jdbcUrl
            username = cfg.user
            password = cfg.password
            maximumPoolSize = (System.getenv("DB_MAX_POOL_SIZE") ?: "10").toInt()
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"

            // Helpful timeouts
            connectionTimeout = 10_000
            idleTimeout = 60_000
            maxLifetime = 30 * 60_000
        }
//        val config = HikariConfig().apply {
//            jdbcUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/guitar_trainer"
//            username = System.getenv("DB_USER") ?: "postgres"
//            password = System.getenv("DB_PASSWORD") ?: "postgres"
//            maximumPoolSize = 5
//            isAutoCommit = false
//            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
//        }
        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                ArtistsTable,
                AlbumsTable,
                SongsTable
            )
        }
    }
}

data class DbConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String
)

fun dbConfigFromEnv(): DbConfig {
    val raw = System.getenv("DATABASE_URL")
        ?: error("DATABASE_URL env var not set")

    val uri = URI(raw)
    val (user, pass) = (uri.userInfo ?: "").split(":", limit = 2).let {
        it[0] to (it.getOrNull(1) ?: "")
    }

    val jdbcUrl = buildString {
        append("jdbc:postgresql://")
        append(uri.host)
        if (uri.port != -1) append(":${uri.port}")
        append(uri.path) // includes /dbname
        // SSL flags (safe defaults; adjust if you know internal URL doesn’t need it)
        append("?sslmode=require")
    }

    return DbConfig(jdbcUrl, user, pass)
}

