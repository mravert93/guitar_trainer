package com.ravert.guitar_trainer.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        val config = HikariConfig().apply {
            jdbcUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/guitar_trainer"
            username = System.getenv("DB_USER") ?: "postgres"
            password = System.getenv("DB_PASSWORD") ?: "postgres"
            maximumPoolSize = 5
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }
        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)

        transaction {
//            SchemaUtils.drop(
//                ArtistsTable,
//                AlbumsTable,
//                SongsTable
//            )
            SchemaUtils.createMissingTablesAndColumns(
                ArtistsTable,
                AlbumsTable,
                SongsTable
            )
        }
    }
}
