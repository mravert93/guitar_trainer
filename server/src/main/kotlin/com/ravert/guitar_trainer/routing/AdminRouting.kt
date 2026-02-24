package com.ravert.guitar_trainer.routing

import com.ravert.guitar_trainer.db.LibraryRepository
import com.ravert.guitar_trainer.youtube.fetchLatestNonShortNonLiveUnder50Min
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import java.net.URLDecoder
import java.util.UUID
import kotlin.text.Charsets.UTF_8

@Serializable
data class CreateArtistRequest(
    val uuid: String? = null,
    val name: String,
    val imageUrl: String? = null
)

@Serializable
data class CreateAlbumRequest(
    val uuid: String? = null,
    val artistId: String,
    val name: String,
    val imageUrl: String? = null
)

@Serializable
data class DeleteRequest(
    val uuid: String,
)

@Serializable
data class CreateSongRequest(
    val uuid: String? = null,
    val artistId: String,
    val albumId: String,
    val name: String,
    val lengthSeconds: Int,
    val bpm: Int,
    val docUrl: String
)

@Serializable
data class LatestYoutubeVideoResponse(val videoId: String?)

fun Application.configureAdminRoutes(
    httpClient: HttpClient,
    repo: LibraryRepository
) {
    routing {
        route("/artists") {
            get {
                call.respond(repo.getArtists())
            }
            post {
                val req = call.receive<CreateArtistRequest>()
                call.respond(repo.addArtist(req.uuid, req.name, req.imageUrl))
            }
            delete {
                val req = call.receive<DeleteRequest>()
                repo.deleteArtists(UUID.fromString(req.uuid))
                call.respond(HttpStatusCode.OK)
            }
        }

        route("/albums") {
            get {
                call.respond(repo.getAlbums())
            }
            post {
                val req = call.receive<CreateAlbumRequest>()
                call.respond(repo.addAlbum(req.uuid, req.artistId, req.name, req.imageUrl))
            }
            delete {
                val req = call.receive<DeleteRequest>()
                repo.deleteAlbum(UUID.fromString(req.uuid))
                call.respond(HttpStatusCode.OK)
            }
        }

        route("/songs") {
            get {
                call.respond(repo.getSongs())
            }
            post {
                val req = call.receive<CreateSongRequest>()
                call.respond(
                    repo.addSong(
                        req.uuid,
                        req.artistId,
                        req.albumId,
                        req.name,
                        req.lengthSeconds,
                        req.bpm,
                        req.docUrl
                    )
                )
            }
            delete {
                val req = call.receive<DeleteRequest>()
                repo.deleteSong(UUID.fromString(req.uuid))
                call.respond(HttpStatusCode.OK)
            }
        }

        get("/songs/{id}") {
            val id = call.parameters["id"]!!
            val song = repo.getSongById(id)
            if (song == null) {
                call.respondText("Not found", status = io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(song)
            }
        }

        get("/youtube/latest") {
            val channelId = "UCBAJtmrwfVzbibgI-OsjzEg"
            val apiKey = "AIzaSyDbYWvzXEhAmn7FwCUc634ufsFoYkKaEak"
            val rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"

            val xml = httpClient.get(rssUrl).bodyAsText()

//            val videoId = Regex("<yt:videoId>([^<]+)</yt:videoId>")
//                .find(xml)
//                ?.groupValues
//                ?.getOrNull(1)
//
//            call.respond(LatestYoutubeVideoResponse(videoId))

            val videoId = fetchLatestNonShortNonLiveUnder50Min(
                http = httpClient,
                apiKey = apiKey,
                channelId = channelId
            )

            call.respond(LatestYoutubeVideoResponse(videoId))
        }

        get("/imageProxy") {
            val encoded = call.request.queryParameters["url"]
                ?: return@get call.respondText("Missing url", status = HttpStatusCode.BadRequest)

            // Your client sends it encoded; decode it back
            val targetUrl = URLDecoder.decode(encoded, UTF_8.name())

            try {
                val upstream: HttpResponse = httpClient.get(targetUrl) {
                    // Many CDNs behave better with “real” headers
                    header(HttpHeaders.UserAgent, "Mozilla/5.0 (Ktor Image Proxy)")
                    header(HttpHeaders.Accept, "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
                    // Sometimes helps with hotlink protection:
                    header(HttpHeaders.Referrer, targetUrl)

                }

                if (!upstream.status.isSuccess()) {
                    return@get call.respondText(
                        "Upstream failed: ${upstream.status}",
                        status = HttpStatusCode.BadGateway
                    )
                }

                val contentTypeHeader = upstream.headers[HttpHeaders.ContentType]
                val contentType = contentTypeHeader?.let { ContentType.parse(it) }
                    ?: ContentType.Application.OctetStream

                // CORS for browser fetch()
                call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=86400")

                // Stream bytes back (don’t JSON-encode, don’t content-negotiate)
                val bytes = upstream.readBytes()
                call.respondBytes(bytes, contentType)

            } catch (t: Throwable) {
                // If the browser cancels mid-request, you’ll often see connection reset/closed channel.
                // Don’t crash the server; just log and return.
                call.application.log.warn("imageProxy failed for $targetUrl: ${t::class.simpleName}: ${t.message}")
                // You can omit responding here if the channel is already closed
            }
        }

    }
}