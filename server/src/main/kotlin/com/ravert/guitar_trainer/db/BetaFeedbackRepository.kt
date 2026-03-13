package com.ravert.guitar_trainer.db

import com.ravert.guitar_trainer.routing.BetaFeedbackRequest
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import java.util.UUID
import kotlin.String

@Serializable
data class BetaFeedback(
    val id: String,
    val createdAt: Long,
    val email: String? = null,
    val rating: String,
    val favoritePart: String = "",
    val bugReport: String = "",
    val featureRequest: String = "",
    val generalFeedback: String
)

class BetaFeedbackRepository {
    fun getAllBetaFeedback(): List<BetaFeedback> = transaction {
        BetaFeedbackTable
            .selectAll()
            .orderBy(BetaFeedbackTable.created_at to SortOrder.DESC)
            .map { row ->
                BetaFeedback(
                    id = row[BetaFeedbackTable.id].toString(),
                    createdAt = row[BetaFeedbackTable.created_at],
                    email = row[BetaFeedbackTable.email],
                    rating = row[BetaFeedbackTable.rating],
                    favoritePart = row[BetaFeedbackTable.favorite_part],
                    bugReport = row[BetaFeedbackTable.bug_report],
                    featureRequest = row[BetaFeedbackTable.feature_request],
                    generalFeedback = row[BetaFeedbackTable.general_feedback],
                )
            }
    }

    fun addFeedback(feedback: BetaFeedbackRequest) = transaction {
        val id  = UUID.randomUUID()
        val now = Date()
        BetaFeedbackTable.insert {
            it[BetaFeedbackTable.id] = id
            it[BetaFeedbackTable.created_at] = now.time
            it[BetaFeedbackTable.email] = feedback.email
            it[BetaFeedbackTable.rating] = feedback.rating
            it[BetaFeedbackTable.favorite_part] = feedback.favoritePart
            it[BetaFeedbackTable.bug_report] = feedback.bugReport
            it[BetaFeedbackTable.feature_request] = feedback.featureRequest
            it[BetaFeedbackTable.general_feedback] = feedback.generalFeedback
        }
    }
}