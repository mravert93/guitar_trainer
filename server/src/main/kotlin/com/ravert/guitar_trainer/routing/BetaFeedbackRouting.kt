package com.ravert.guitar_trainer.routing

import com.ravert.guitar_trainer.db.BetaFeedbackRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class BetaFeedbackRequest(
    val email: String? = null,
    val rating: String,
    val favoritePart: String = "",
    val bugReport: String = "",
    val featureRequest: String = "",
    val generalFeedback: String
)

fun Application.configureBetaFeedbackRoutes(
    repo: BetaFeedbackRepository,
) {
    routing {
        post("/beta-feedback") {
            val req = call.receive<BetaFeedbackRequest>()

            repo.addFeedback(req)

            call.respond(HttpStatusCode.OK, "Feedback submitted")
        }

        get("/admin/beta-feedback") {
            val allFeedback = repo.getAllBetaFeedback()

            call.respond(allFeedback)
        }
    }
}