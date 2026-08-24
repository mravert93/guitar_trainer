package com.ravert.guitar_trainer.routing

import com.ravert.guitar_trainer.cloudinary.CloudinaryService
import com.ravert.guitar_trainer.db.AuthRepository
import com.ravert.guitar_trainer.db.LibraryRepository
import com.ravert.guitar_trainer.db.NewestTabRecord
import com.ravert.guitar_trainer.db.SongVideoRecord
import com.ravert.guitar_trainer.db.isPublicAt
import com.ravert.guitar_trainer.guitartrainer.datamodels.Song
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class EarlyAccessTabDto(
    val song: Song,
    val artistName: String,
    val artistImageUrl: String? = null,
    val albumName: String? = null,
    val albumImageUrl: String? = null,
)

@Serializable
data class EarlyAccessTabsResponse(
    val tabs: List<EarlyAccessTabDto>,
)

@Serializable
data class VideoPlaybackResponse(
    val url: String,
    val format: String,
    val durationSeconds: Double? = null,
    val releaseAt: Long? = null,
)

@Serializable
data class CloudinaryVideoUploadSignatureResponse(
    val cloudName: String,
    val apiKey: String,
    val timestamp: Long,
    val signature: String,
    val publicId: String,
    val uploadUrl: String,
    val resourceType: String = "video",
    val type: String,
    val overwrite: Boolean = true,
    val invalidate: Boolean = true,
)

@Serializable
data class RegisterCloudinaryVideoRequest(
    val publicId: String,
    val format: String,
    val version: Long? = null,
    val durationSeconds: Double? = null,
)

@Serializable
data class UpdateSongReleaseRequest(
    val releaseAt: Long? = null,
)

@Serializable
data class SongEarlyAccessSettingsResponse(
    val songUuid: String,
    val releaseAt: Long? = null,
    val isEarlyAccess: Boolean,
    val videoPublicId: String? = null,
    val videoFormat: String? = null,
    val videoVersion: Long? = null,
    val videoDurationSeconds: Double? = null,
)

fun Application.configureEarlyAccessRoutes(
    authRepository: AuthRepository,
    libraryRepository: LibraryRepository,
    cloudinaryService: CloudinaryService?,
    httpClient: HttpClient,
) {
    routing {
        get("/early-access/tabs") {
            val tabs = libraryRepository.getUpcomingTabs().map(NewestTabRecord::toPublicEarlyAccessDto)
            call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=60")
            call.respond(EarlyAccessTabsResponse(tabs))
        }

        get("/songs/{songUuid}/video") {
            val songUuid = call.songUuidParameter()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid song UUID")
            val video = libraryRepository.getSongVideo(songUuid)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Song not found")
            if (!call.requireSongAccess(video.song, authRepository)) return@get

            val publicId = video.publicId
                ?: return@get call.respond(HttpStatusCode.NotFound, "This song does not have a video")
            val format = video.format
                ?: return@get call.respond(HttpStatusCode.Conflict, "Video format is missing")
            val cloudinary = cloudinaryService
                ?: return@get call.respond(HttpStatusCode.ServiceUnavailable, "Cloudinary is not configured")

            if (!video.song.isPublicAt()) {
                call.response.headers.append(HttpHeaders.CacheControl, "private, no-store")
            }
            call.respond(
                VideoPlaybackResponse(
                    url = cloudinary.authenticatedVideoUrl(publicId, format, video.version),
                    format = format,
                    durationSeconds = video.durationSeconds,
                    releaseAt = video.song.releaseAt,
                )
            )
        }

        get("/admin/songs/{songUuid}/early-access") {
            if (!call.hasAdminAuth()) {
                return@get call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }
            val songUuid = call.songUuidParameter()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid song UUID")
            val settings = libraryRepository.getSongVideo(songUuid)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Song not found")
            call.respond(settings.toAdminResponse())
        }

        patch("/admin/songs/{songUuid}/release") {
            if (!call.hasAdminAuth()) {
                return@patch call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }
            val songUuid = call.songUuidParameter()
                ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid song UUID")
            val request = call.receive<UpdateSongReleaseRequest>()
            val settings = libraryRepository.updateSongReleaseAt(songUuid, request.releaseAt)
                ?: return@patch call.respond(HttpStatusCode.NotFound, "Song not found")
            call.respond(settings.toAdminResponse())
        }

        post("/admin/songs/{songUuid}/video-upload-signature") {
            if (!call.hasAdminAuth()) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }
            val songUuid = call.songUuidParameter()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid song UUID")
            if (libraryRepository.getSongById(songUuid.toString()) == null) {
                return@post call.respond(HttpStatusCode.NotFound, "Song not found")
            }
            val cloudinary = cloudinaryService
                ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, "Cloudinary is not configured")
            val upload = cloudinary.createVideoUploadSignature(songUuid)

            call.respond(
                CloudinaryVideoUploadSignatureResponse(
                    cloudName = upload.cloudName,
                    apiKey = upload.apiKey,
                    timestamp = upload.timestamp,
                    signature = upload.signature,
                    publicId = upload.publicId,
                    uploadUrl = upload.uploadUrl,
                    type = upload.deliveryType,
                )
            )
        }

        post("/admin/songs/{songUuid}/video") {
            if (!call.hasAdminAuth()) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }
            val songUuid = call.songUuidParameter()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid song UUID")
            val request = call.receive<RegisterCloudinaryVideoRequest>()
            val cloudinary = cloudinaryService
                ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, "Cloudinary is not configured")
            if (request.publicId != cloudinary.expectedVideoPublicId(songUuid)) {
                return@post call.respond(HttpStatusCode.BadRequest, "Unexpected Cloudinary public ID")
            }
            val format = request.format.trim().lowercase()
            if (!CloudinaryFormatRegex.matches(format)) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid video format")
            }
            if (request.version != null && request.version <= 0) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid video version")
            }
            if (request.durationSeconds != null && request.durationSeconds < 0) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid video duration")
            }

            val settings = libraryRepository.registerSongVideo(
                songUuid = songUuid,
                publicId = request.publicId,
                format = format,
                version = request.version,
                durationSeconds = request.durationSeconds,
            ) ?: return@post call.respond(HttpStatusCode.NotFound, "Song not found")
            call.respond(settings.toAdminResponse())
        }

        delete("/admin/songs/{songUuid}/video") {
            if (!call.hasAdminAuth()) {
                return@delete call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }
            val songUuid = call.songUuidParameter()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid song UUID")
            val existing = libraryRepository.getSongVideo(songUuid)
                ?: return@delete call.respond(HttpStatusCode.NotFound, "Song not found")
            existing.publicId?.let { publicId ->
                val cloudinary = cloudinaryService
                    ?: return@delete call.respond(HttpStatusCode.ServiceUnavailable, "Cloudinary is not configured")
                val destroy = cloudinary.createVideoDestroySignature(publicId)
                val cloudinaryResponse = httpClient.submitForm(
                    url = destroy.destroyUrl,
                    formParameters = Parameters.build {
                        append("api_key", destroy.apiKey)
                        append("timestamp", destroy.timestamp.toString())
                        append("signature", destroy.signature)
                        append("public_id", destroy.publicId)
                        append("type", destroy.deliveryType)
                        append("invalidate", "true")
                    },
                )
                if (!cloudinaryResponse.status.isSuccess()) {
                    return@delete call.respond(
                        HttpStatusCode.BadGateway,
                        "Cloudinary could not delete the video: ${cloudinaryResponse.bodyAsText()}",
                    )
                }
            }
            val settings = libraryRepository.removeSongVideo(songUuid)
                ?: return@delete call.respond(HttpStatusCode.NotFound, "Song not found")
            call.respond(settings.toAdminResponse())
        }
    }
}

internal fun canAccessSong(releaseAt: Long?, hasPremium: Boolean, now: Long): Boolean =
    releaseAt == null || releaseAt <= now || hasPremium

internal suspend fun ApplicationCall.requireSongAccess(song: Song, authRepository: AuthRepository): Boolean {
    val now = nowMillis()
    if (canAccessSong(song.releaseAt, hasPremium = false, now = now) || hasAdminAuth()) return true

    val user = requireUser(authRepository)
    if (user == null) {
        respond(HttpStatusCode.Unauthorized, "Unauthorized")
        return false
    }
    if (!canAccessSong(song.releaseAt, authRepository.userHasPremium(user.uuid), now)) {
        respond(HttpStatusCode.Forbidden, "Premium access required")
        return false
    }
    return true
}

private fun NewestTabRecord.toPublicEarlyAccessDto() = EarlyAccessTabDto(
    song = song.copy(docUrl = ""),
    artistName = artistName,
    artistImageUrl = artistImageUrl,
    albumName = albumName,
    albumImageUrl = albumImageUrl,
)

private fun SongVideoRecord.toAdminResponse() = SongEarlyAccessSettingsResponse(
    songUuid = song.uuid,
    releaseAt = song.releaseAt,
    isEarlyAccess = !song.isPublicAt(),
    videoPublicId = publicId,
    videoFormat = format,
    videoVersion = version,
    videoDurationSeconds = durationSeconds,
)

private fun ApplicationCall.songUuidParameter(): UUID? =
    parameters["songUuid"]?.let {
        try {
            UUID.fromString(it)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

private val CloudinaryFormatRegex = Regex("^[a-z0-9]{2,10}$")
