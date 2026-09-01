package com.ravert.guitar_trainer.routing

import com.ravert.guitar_trainer.db.AuthRepository
import com.ravert.guitar_trainer.db.LibraryRepository
import com.ravert.guitar_trainer.db.isPublicAt
import com.ravert.guitar_trainer.youtube.fetchLatestNonShortNonLiveUnder50Min
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.Cookie
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
import java.net.URI
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
    val docUrl: String,
    val youtubeLink: String? = null,
    val tuning: String? = null,
    val capo: String? = null,
    val chords: String? = null,
    val technique: String? = null,
    val releaseAt: Long? = null,
)

@Serializable
data class LatestYoutubeVideoResponse(val videoId: String?)

@Serializable
data class AdminLoginRequest(
    val password: String,
)

@Serializable
data class SearchResponse(
    val query: String,
    val results: List<com.ravert.guitar_trainer.db.LibrarySearchResult>,
)

fun Application.configureAdminRoutes(
    httpClient: HttpClient,
    repo: LibraryRepository,
    authRepository: AuthRepository,
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
                val user = call.requireUser(authRepository)
                val includeUnreleased = call.hasAdminAuth() ||
                    (user != null && authRepository.userHasPremium(user.uuid))
                call.respond(repo.getSongs(includeUnreleased = includeUnreleased))
            }
            post {
                if (!call.hasAdminAuth()) {
                    return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                }
                val req = call.receive<CreateSongRequest>()
                call.respond(
                    repo.addSong(
                        req.uuid,
                        req.artistId,
                        req.albumId,
                        req.name,
                        req.lengthSeconds,
                        req.bpm,
                        req.docUrl,
                        req.youtubeLink.normalizeOptionalText(),
                        req.tuning.normalizeOptionalText(),
                        req.capo.normalizeOptionalText(),
                        req.chords.normalizeOptionalText(),
                        req.technique.normalizeOptionalText(),
                        req.releaseAt,
                    )
                )
            }
            delete {
                if (!call.hasAdminAuth()) {
                    return@delete call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                }
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
                if (!call.requireSongAccess(song, authRepository)) return@get
                call.respond(song)
            }
        }

        get("/search") {
            val query = call.request.queryParameters["q"]?.trim().orEmpty()
            val limit = call.request.queryParameters["limit"]
                ?.toIntOrNull()
                ?.coerceIn(1, 25)
                ?: 10

            if (query.length < 2) {
                return@get call.respond(SearchResponse(query = query, results = emptyList()))
            }

            val user = call.requireUser(authRepository)
            val includeUnreleased = call.hasAdminAuth() ||
                (user != null && authRepository.userHasPremium(user.uuid))
            call.respond(
                SearchResponse(
                    query = query,
                    results = repo.search(query, limit, includeUnreleased = includeUnreleased),
                )
            )
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

        get("/docProxy") {
            val encoded = call.request.queryParameters["url"]
                ?: return@get call.respondText("Missing url", status = HttpStatusCode.BadRequest)

            val requestedUrl = URLDecoder.decode(encoded, UTF_8.name())
            val song = googleDocId(requestedUrl)?.let(repo::findSongByGoogleDocId)
                ?: repo.findSongByDocUrl(requestedUrl)
            if (song != null && !call.requireSongAccess(song, authRepository)) return@get
            val cacheControl = if (song != null && !song.isPublicAt()) {
                "private, no-store"
            } else {
                "public, max-age=300"
            }
            val targets = googleDocFetchTargets(requestedUrl)
            if (targets == null) {
                return@get call.respondText("Unsupported doc url", status = HttpStatusCode.BadRequest)
            }

            try {
                var lastFailure = "Could not load Google Doc."

                for (target in targets) {
                    val upstream: HttpResponse = httpClient.get(target.url) {
                        header(HttpHeaders.UserAgent, "Mozilla/5.0 (Ktor Doc Proxy)")
                        header(HttpHeaders.Accept, "text/plain,*/*;q=0.8")
                    }

                    if (!isAllowedGoogleDocResponseHost(upstream.request.url.host, target)) {
                        lastFailure =
                            "Google Doc redirected to ${upstream.request.url.host}. Make sure the document is public or published to the web."
                        continue
                    }

                    val body = upstream.bodyAsText()
                    if (target.isPublishedDoc) {
                        val text = extractPublishedGoogleDocText(body)
                        if (text != null) {
                            call.response.headers.append(HttpHeaders.CacheControl, cacheControl)
                            return@get call.respondText(text, ContentType.Text.Plain)
                        }

                        lastFailure = "Could not extract text from published Google Doc."
                        continue
                    }

                    if (upstream.status == HttpStatusCode.Unauthorized || upstream.status == HttpStatusCode.Forbidden) {
                        lastFailure =
                            "Google Doc export is not publicly accessible. Share the document with anyone who has the link, or publish it to the web."
                        continue
                    }

                    if (!upstream.status.isSuccess()) {
                        lastFailure = "Upstream failed: ${upstream.status}"
                        continue
                    }

                    if (body.trimStart().startsWith("<")) {
                        lastFailure =
                            "Google returned HTML instead of text. Share the document with anyone who has the link, or publish it to the web."
                        continue
                    }

                    call.response.headers.append(HttpHeaders.CacheControl, cacheControl)
                    return@get call.respondText(body, ContentType.Text.Plain)
                }

                call.respondText(lastFailure, status = HttpStatusCode.BadGateway)
            } catch (t: Throwable) {
                call.application.log.warn("docProxy failed for $requestedUrl: ${t::class.simpleName}: ${t.message}")
                call.respondText("Doc proxy failed", status = HttpStatusCode.BadGateway)
            }
        }

        post("/admin/login") {
            val req = call.receive<AdminLoginRequest>()

            // Grab from env
            val adminPassword = System.getenv("ADMIN_PASSWORD")

            if (req.password != adminPassword) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Invalid Password")
            }

            call.response.cookies.append(
                Cookie(
                    name = "admin_auth",
                    value = "true",
                    path = "/",
                    httpOnly = true,
                    secure = true,
                    extensions = mapOf("SameSite" to "None")
                )
            )

            call.respond(HttpStatusCode.OK, "Logged In")
        }

        get("/admin/check-auth") {
            val cookie = call.request.cookies["admin_auth"]
            if (cookie == "true") {
                call.respond(HttpStatusCode.OK, "Authorized")
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }
        }

        post("/admin/logout") {
            call.response.cookies.append(
                Cookie(
                    name = "admin_auth",
                    value = "",
                    path = "/",
                    httpOnly = true,
                    secure = true,
                    maxAge = 0,
                    extensions = mapOf("SameSite" to "None")
                )
            )
            call.respond(HttpStatusCode.OK, "Logged Out")
        }
    }
}

private data class GoogleDocFetchTarget(
    val url: String,
    val isPublishedDoc: Boolean,
)

private fun googleDocFetchTargets(value: String): List<GoogleDocFetchTarget>? {
    val uri = try {
        URI(value)
    } catch (_: Exception) {
        return null
    }

    if (uri.scheme != "https" || uri.host != "docs.google.com") return null

    val docId = googleDocId(uri) ?: return null

    val pubTarget = GoogleDocFetchTarget(
        url = "https://docs.google.com/document/d/$docId/pub",
        isPublishedDoc = true,
    )
    val exportTarget = GoogleDocFetchTarget(
        url = "https://docs.google.com/document/d/$docId/export?format=txt",
        isPublishedDoc = false,
    )

    return when {
        uri.path.endsWith("/pub") -> listOf(pubTarget)
        uri.path.endsWith("/export") -> listOf(exportTarget)
        else -> listOf(pubTarget, exportTarget)
    }
}

private fun googleDocId(value: String): String? = try {
    googleDocId(URI(value))
} catch (_: Exception) {
    null
}

private fun googleDocId(uri: URI): String? {
    if (uri.scheme != "https" || uri.host != "docs.google.com") return null
    return Regex("^/document/d/([A-Za-z0-9_-]+)")
        .find(uri.path)
        ?.groupValues
        ?.getOrNull(1)
}

private fun isAllowedGoogleDocResponseHost(host: String, target: GoogleDocFetchTarget): Boolean {
    if (host == "docs.google.com") return true

    return !target.isPublishedDoc &&
        (host == "googleusercontent.com" || host.endsWith(".googleusercontent.com"))
}

private fun extractPublishedGoogleDocText(html: String): String? {
    val contentHtml = extractDocContentDiv(html)
        ?: extractContentsDiv(html)
        ?: return null

    return contentHtml
        .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), "")
        .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), "")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p\\s*>"), "\n")
        .replace(Regex("(?i)</div\\s*>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .decodeHtmlEntities()
        .lines()
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
        .joinToString("\n")
        .takeIf { it.isNotBlank() }
}

private fun extractDocContentDiv(html: String): String? {
    val docContentIndex = html.indexOf("doc-content")
    if (docContentIndex == -1) return null

    val divStart = html.lastIndexOf("<div", docContentIndex)
    if (divStart == -1) return null

    val divEnd = findMatchingDivEnd(html, divStart) ?: return null
    return html.substring(divStart, divEnd)
}

private fun extractContentsDiv(html: String): String? {
    val contentsStart = html.indexOf("<div id=\"contents\"")
    if (contentsStart == -1) return null

    val contentsEnd = findMatchingDivEnd(html, contentsStart) ?: return null
    return html.substring(contentsStart, contentsEnd)
}

private fun findMatchingDivEnd(html: String, divStart: Int): Int? {
    val divRegex = Regex("(?i)</?div\\b[^>]*>")
    var depth = 0
    for (match in divRegex.findAll(html, divStart)) {
        if (match.value.startsWith("</", ignoreCase = true)) {
            depth--
            if (depth == 0) return match.range.last + 1
        } else {
            depth++
        }
    }
    return null
}

private fun String.decodeHtmlEntities(): String {
    return replace("&nbsp;", " ")
        .replace("&#160;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
        }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
        }
}
