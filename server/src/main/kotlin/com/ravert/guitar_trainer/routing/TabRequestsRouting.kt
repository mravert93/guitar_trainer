package com.ravert.guitar_trainer.routing

import com.ravert.guitar_trainer.db.AuthRepository
import com.ravert.guitar_trainer.db.TabRequestRecord
import com.ravert.guitar_trainer.db.TabRequestSort
import com.ravert.guitar_trainer.db.TabRequestStatusCompleted
import com.ravert.guitar_trainer.db.TabRequestStatusInProgress
import com.ravert.guitar_trainer.db.TabRequestStatusRequested
import com.ravert.guitar_trainer.db.TabRequestStatuses
import com.ravert.guitar_trainer.db.TabRequestVoteResult
import com.ravert.guitar_trainer.db.TabRequestsRepository
import com.ravert.guitar_trainer.db.UpdateTabRequestResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import java.util.UUID

private const val MaxRequestNameLength = 255
private const val MaxRequestDetailsLength = 1_000

@Serializable
data class CreateTabRequestRequest(
    val artistName: String,
    val songName: String,
    val details: String? = null,
)

@Serializable
data class UpdateTabRequestRequest(
    val status: String,
    val completedSongUuid: String? = null,
)

@Serializable
data class PublicTabRequestDto(
    val uuid: String,
    val artistName: String,
    val songName: String,
    val details: String? = null,
    val status: String,
    val completedSongUuid: String? = null,
    val voteCount: Int,
    val currentUserHasVoted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class AdminTabRequestDto(
    val uuid: String,
    val requestedByUserUuid: String? = null,
    val requesterEmail: String? = null,
    val artistName: String,
    val songName: String,
    val details: String? = null,
    val status: String,
    val completedSongUuid: String? = null,
    val voteCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
)

@Serializable
data class TabRequestsResponse(
    val requests: List<PublicTabRequestDto>,
    val total: Int,
    val limit: Int,
    val offset: Int,
    val sort: String,
)

@Serializable
data class AdminTabRequestsResponse(
    val requests: List<AdminTabRequestDto>,
    val total: Int,
    val limit: Int,
    val offset: Int,
    val sort: String,
    val status: String,
)

@Serializable
data class SubmitTabRequestResponse(
    val request: PublicTabRequestDto,
    val created: Boolean,
)

@Serializable
data class TabRequestResponse(
    val request: PublicTabRequestDto,
)

@Serializable
data class AdminTabRequestResponse(
    val request: AdminTabRequestDto,
)

@Serializable
data class TabRequestErrorResponse(
    val error: String,
    val message: String,
    val completedSongUuid: String? = null,
)

fun Application.configureTabRequestRoutes(
    authRepository: AuthRepository,
    tabRequestsRepository: TabRequestsRepository,
) {
    routing {
        route("/tab-requests") {
            get {
                val currentUser = call.requireUser(authRepository)
                val pagination = call.request.queryParameters.pagination()
                val sort = call.request.queryParameters["sort"].toTabRequestSort()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "sort must be popular, newest, or oldest")
                val status = call.request.queryParameters["status"]?.trim()?.takeIf { it.isNotBlank() }
                if (status != null && status !in setOf(TabRequestStatusRequested, TabRequestStatusInProgress)) {
                    return@get call.respond(HttpStatusCode.BadRequest, "status must be requested or in_progress")
                }

                val requests = tabRequestsRepository.listRequests(
                    currentUserUuid = currentUser?.uuid,
                    includeCompleted = false,
                    status = status,
                    search = call.request.queryParameters["q"],
                    sort = sort,
                )
                call.respond(
                    TabRequestsResponse(
                        requests = requests.page(pagination).map(TabRequestRecord::toPublicDto),
                        total = requests.size,
                        limit = pagination.limit,
                        offset = pagination.offset,
                        sort = sort.apiValue,
                    )
                )
            }

            post {
                val user = call.requireUser(authRepository)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                val body = call.receive<CreateTabRequestRequest>()
                val artistName = body.artistName.trim()
                val songName = body.songName.trim()
                val details = body.details?.trim()?.takeIf { it.isNotBlank() }
                val validationError = validateTabRequest(artistName, songName, details)
                if (validationError != null) {
                    return@post call.respond(HttpStatusCode.BadRequest, validationError)
                }

                val result = tabRequestsRepository.submitRequest(
                    userUuid = user.uuid,
                    artistName = artistName,
                    songName = songName,
                    details = details,
                    now = nowMillis(),
                )
                val request = tabRequestsRepository.findRequest(result.requestUuid, user.uuid)
                    ?: return@post call.respond(HttpStatusCode.InternalServerError, "Request could not be loaded")
                if (result.completed) {
                    return@post call.respond(
                        HttpStatusCode.Conflict,
                        TabRequestErrorResponse(
                            error = "tab_request_completed",
                            message = "This tab request has already been completed.",
                            completedSongUuid = request.completedSongUuid?.toString(),
                        ),
                    )
                }

                call.respond(
                    if (result.created) HttpStatusCode.Created else HttpStatusCode.OK,
                    SubmitTabRequestResponse(request.toPublicDto(), result.created),
                )
            }

            post("/{requestUuid}/vote") {
                val user = call.requireUser(authRepository)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                val requestUuid = call.parameters["requestUuid"].toUuidOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid request UUID")

                when (tabRequestsRepository.addVote(requestUuid, user.uuid, nowMillis())) {
                    TabRequestVoteResult.REQUEST_NOT_FOUND ->
                        return@post call.respond(HttpStatusCode.NotFound, "Tab request not found")
                    TabRequestVoteResult.REQUEST_COMPLETED ->
                        return@post call.respond(
                            HttpStatusCode.Conflict,
                            TabRequestErrorResponse(
                                error = "tab_request_completed",
                                message = "Completed requests cannot receive votes.",
                            ),
                        )
                    TabRequestVoteResult.ADDED,
                    TabRequestVoteResult.ALREADY_VOTED -> Unit
                }

                val request = tabRequestsRepository.findRequest(requestUuid, user.uuid)
                    ?: return@post call.respond(HttpStatusCode.NotFound, "Tab request not found")
                call.respond(TabRequestResponse(request.toPublicDto()))
            }

            delete("/{requestUuid}/vote") {
                val user = call.requireUser(authRepository)
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                val requestUuid = call.parameters["requestUuid"].toUuidOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid request UUID")
                val existing = tabRequestsRepository.findRequest(requestUuid, user.uuid)
                    ?: return@delete call.respond(HttpStatusCode.NotFound, "Tab request not found")

                tabRequestsRepository.removeVote(requestUuid, user.uuid)
                val request = tabRequestsRepository.findRequest(existing.uuid, user.uuid)
                    ?: return@delete call.respond(HttpStatusCode.NotFound, "Tab request not found")
                call.respond(TabRequestResponse(request.toPublicDto()))
            }
        }

        get("/admin/tab-requests") {
            if (!call.hasAdminAuth()) {
                return@get call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }
            val pagination = call.request.queryParameters.pagination()
            val sort = call.request.queryParameters["sort"].toTabRequestSort(default = TabRequestSort.NEWEST)
                ?: return@get call.respond(HttpStatusCode.BadRequest, "sort must be popular, newest, or oldest")
            val requestedStatus = call.request.queryParameters["status"]?.trim()?.takeIf { it.isNotBlank() } ?: "all"
            if (requestedStatus !in TabRequestStatuses + setOf("all", "active")) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "status must be all, active, requested, in_progress, or completed",
                )
            }

            val requests = tabRequestsRepository.listRequests(
                currentUserUuid = null,
                includeCompleted = requestedStatus !in setOf("active", TabRequestStatusRequested, TabRequestStatusInProgress),
                status = requestedStatus.takeIf { it in TabRequestStatuses },
                search = call.request.queryParameters["q"],
                sort = sort,
            )
            call.respond(
                AdminTabRequestsResponse(
                    requests = requests.page(pagination).map(TabRequestRecord::toAdminDto),
                    total = requests.size,
                    limit = pagination.limit,
                    offset = pagination.offset,
                    sort = sort.apiValue,
                    status = requestedStatus,
                )
            )
        }

        patch("/admin/tab-requests/{requestUuid}") {
            if (!call.hasAdminAuth()) {
                return@patch call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }
            val requestUuid = call.parameters["requestUuid"].toUuidOrNull()
                ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid request UUID")
            val body = call.receive<UpdateTabRequestRequest>()
            if (body.status !in TabRequestStatuses) {
                return@patch call.respond(
                    HttpStatusCode.BadRequest,
                    "status must be requested, in_progress, or completed",
                )
            }
            val completedSongUuid = body.completedSongUuid.toUuidOrNull()
            if (body.completedSongUuid != null && completedSongUuid == null) {
                return@patch call.respond(HttpStatusCode.BadRequest, "Invalid completed song UUID")
            }
            if (body.status != TabRequestStatusCompleted && completedSongUuid != null) {
                return@patch call.respond(
                    HttpStatusCode.BadRequest,
                    "completedSongUuid can only be set when status is completed",
                )
            }

            when (
                tabRequestsRepository.updateRequest(
                    requestUuid = requestUuid,
                    status = body.status,
                    completedSongUuid = completedSongUuid,
                    now = nowMillis(),
                )
            ) {
                UpdateTabRequestResult.REQUEST_NOT_FOUND ->
                    return@patch call.respond(HttpStatusCode.NotFound, "Tab request not found")
                UpdateTabRequestResult.COMPLETED_SONG_NOT_FOUND ->
                    return@patch call.respond(HttpStatusCode.NotFound, "Completed song not found")
                UpdateTabRequestResult.UPDATED -> Unit
            }

            val request = tabRequestsRepository.findRequest(requestUuid, null)
                ?: return@patch call.respond(HttpStatusCode.NotFound, "Tab request not found")
            call.respond(AdminTabRequestResponse(request.toAdminDto()))
        }
    }
}

private data class Pagination(val limit: Int, val offset: Int)

private fun io.ktor.http.Parameters.pagination() = Pagination(
    limit = this["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50,
    offset = this["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
)

private fun <T> List<T>.page(pagination: Pagination): List<T> =
    drop(pagination.offset).take(pagination.limit)

private fun String?.toTabRequestSort(default: TabRequestSort = TabRequestSort.POPULAR): TabRequestSort? =
    when (this?.trim()?.lowercase()?.takeIf { it.isNotBlank() }) {
        null -> default
        "popular" -> TabRequestSort.POPULAR
        "newest" -> TabRequestSort.NEWEST
        "oldest" -> TabRequestSort.OLDEST
        else -> null
    }

private val TabRequestSort.apiValue: String
    get() = name.lowercase()

private fun validateTabRequest(artistName: String, songName: String, details: String?): String? = when {
    artistName.isBlank() -> "Artist name is required"
    artistName.length > MaxRequestNameLength -> "Artist name must be 255 characters or fewer"
    songName.isBlank() -> "Song name is required"
    songName.length > MaxRequestNameLength -> "Song name must be 255 characters or fewer"
    details != null && details.length > MaxRequestDetailsLength -> "Details must be 1000 characters or fewer"
    else -> null
}

private fun TabRequestRecord.toPublicDto() = PublicTabRequestDto(
    uuid = uuid.toString(),
    artistName = artistName,
    songName = songName,
    details = details,
    status = status,
    completedSongUuid = completedSongUuid?.toString(),
    voteCount = voteCount,
    currentUserHasVoted = currentUserHasVoted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun TabRequestRecord.toAdminDto() = AdminTabRequestDto(
    uuid = uuid.toString(),
    requestedByUserUuid = requestedByUserUuid?.toString(),
    requesterEmail = requesterEmail,
    artistName = artistName,
    songName = songName,
    details = details,
    status = status,
    completedSongUuid = completedSongUuid?.toString(),
    voteCount = voteCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
)

private fun String?.toUuidOrNull(): UUID? = try {
    this?.let(UUID::fromString)
} catch (_: IllegalArgumentException) {
    null
}
