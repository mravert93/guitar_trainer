package com.ravert.guitar_trainer.db

import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

data class MonthlyTabDownloadLinkRecord(
    val uuid: UUID,
    val monthKey: String,
    val publicToken: String,
    val publicUrl: String,
    val cutoffAt: Long,
    val createdAt: Long,
)

data class MonthlyTabDownloadEntry(
    val artistName: String,
    val songName: String,
    val docUrl: String,
)

class MonthlyTabDownloadLinksRepository {
    fun ensureLink(
        monthKey: String,
        cutoffAt: Long,
        appPublicUrl: String,
        createdAt: Long = System.currentTimeMillis(),
    ): MonthlyTabDownloadLinkRecord = transaction {
        findByMonthKey(monthKey)?.let { return@transaction it }

        val uuid = UUID.randomUUID()
        val token = generatePublicToken()
        val publicUrl = "${appPublicUrl.trimEnd('/')}/tab-downloads/$token"

        MonthlyTabDownloadLinksTable.insertIgnore {
            it[MonthlyTabDownloadLinksTable.uuid] = uuid
            it[MonthlyTabDownloadLinksTable.monthKey] = monthKey
            it[publicToken] = token
            it[MonthlyTabDownloadLinksTable.publicUrl] = publicUrl
            it[MonthlyTabDownloadLinksTable.cutoffAt] = cutoffAt
            it[MonthlyTabDownloadLinksTable.createdAt] = createdAt
        }

        findByMonthKey(monthKey) ?: error("Failed to create monthly tab link for $monthKey")
    }

    fun listLinks(): List<MonthlyTabDownloadLinkRecord> = transaction {
        MonthlyTabDownloadLinksTable
            .selectAll()
            .orderBy(MonthlyTabDownloadLinksTable.monthKey to SortOrder.DESC)
            .map(::toLinkRecord)
    }

    fun findByPublicToken(token: String): MonthlyTabDownloadLinkRecord? = transaction {
        MonthlyTabDownloadLinksTable
            .selectAll()
            .where { MonthlyTabDownloadLinksTable.publicToken eq token }
            .singleOrNull()
            ?.let(::toLinkRecord)
    }

    fun getEntries(cutoffAt: Long): List<MonthlyTabDownloadEntry> = transaction {
        (SongsTable innerJoin ArtistsTable)
            .select(ArtistsTable.name, SongsTable.name, SongsTable.docUrl)
            .where { SongsTable.createdAt lessEq cutoffAt }
            .orderBy(ArtistsTable.name to SortOrder.ASC, SongsTable.name to SortOrder.ASC)
            .map { row ->
                MonthlyTabDownloadEntry(
                    artistName = row[ArtistsTable.name],
                    songName = row[SongsTable.name],
                    docUrl = row[SongsTable.docUrl],
                )
            }
    }

    fun countEntries(cutoffAt: Long): Long = transaction {
        SongsTable
            .selectAll()
            .where { SongsTable.createdAt lessEq cutoffAt }
            .count()
    }

    private fun findByMonthKey(monthKey: String): MonthlyTabDownloadLinkRecord? =
        MonthlyTabDownloadLinksTable
            .selectAll()
            .where { MonthlyTabDownloadLinksTable.monthKey eq monthKey }
            .singleOrNull()
            ?.let(::toLinkRecord)

    private fun toLinkRecord(row: org.jetbrains.exposed.sql.ResultRow) = MonthlyTabDownloadLinkRecord(
        uuid = row[MonthlyTabDownloadLinksTable.uuid],
        monthKey = row[MonthlyTabDownloadLinksTable.monthKey],
        publicToken = row[MonthlyTabDownloadLinksTable.publicToken],
        publicUrl = row[MonthlyTabDownloadLinksTable.publicUrl],
        cutoffAt = row[MonthlyTabDownloadLinksTable.cutoffAt],
        createdAt = row[MonthlyTabDownloadLinksTable.createdAt],
    )
}

private val monthlyLinkSecureRandom = SecureRandom()

private fun generatePublicToken(): String {
    val bytes = ByteArray(32)
    monthlyLinkSecureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
