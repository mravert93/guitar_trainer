package com.ravert.guitar_trainer.routing

import com.ravert.guitar_trainer.db.LibraryRepository
import com.ravert.guitar_trainer.db.NewAlbum
import com.ravert.guitar_trainer.db.NewArtist
import com.ravert.guitar_trainer.db.NewSong
import com.ravert.guitar_trainer.guitartrainer.datamodels.Album
import com.ravert.guitar_trainer.import.AlbumLookupResults
import com.ravert.guitar_trainer.import.MbRateLimiter
import com.ravert.guitar_trainer.import.deezerLookupTrack
import com.ravert.guitar_trainer.import.enrichWithMusicBrainzAndCAA
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.http.content.file
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class ImportResult(
    val artistsUpserted: Int,
    val songsUpserted: Int,
    val skippedRows: Int,
    val errors: List<String>
)

fun Application.configureImportRoutes(
    httpClient: HttpClient,
    repo: LibraryRepository,
) {
    routing {
        post("/admin/importSheet") {
            val csvUrl = call.request.queryParameters["csvUrl"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing csvUrl")

            val csvText = httpClient.get(csvUrl).bodyAsText()

            val rows = parseCsv(csvText)
            if (rows.isEmpty()) return@post call.respond(ImportResult(0, 0, 0, listOf("CSV empty")))

            // Normalize header names
            val header = rows.first().map { it.trim() }
            fun idx(name: String): Int = header.indexOfFirst { it.equals(name, ignoreCase = true) }

            val docIdx = idx("Document Link").takeIf { it >= 0 } ?: idx("Document link")
            val fileIdIdx = idx("File ID").takeIf { it >= 0 } ?: idx("File ID")
            val songIdx = idx("Song Title")
            val artistIdx = idx("Artist")

            if (docIdx == null || songIdx < 0 || artistIdx < 0) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "CSV must include columns: Song, Artist, Document Link. Found: $header"
                )
            }

            var artistsUpserted = 0
            var songsUpserted = 0
            var skipped = 0
            val errors = mutableListOf<String>()

            // cache artistName -> artistUuid (avoid DB lookups)
            val artistCache = mutableMapOf<String, String>()

            // Existing data
            val existingArtists = repo.getArtists()
                .associateBy { it.name.lowercase() }
            val existingAlbums = repo.getAlbums()
                .groupBy { it.artistUuid }
            val existingSongs = repo.getSongs()
                .groupBy { it.artistUuid }

            // New data to insert/upsert into database
            val newArtists = arrayListOf<NewArtist>()
            val newAlbums = hashMapOf<String, ArrayList<NewAlbum>>()
            val newSongs = arrayListOf<NewSong>()

            val droppedRows = rows.drop(1)
            var i = 0
            while (i < droppedRows.size) {
                val r = droppedRows[i]
                try {
                    val songName = r.getOrNull(songIdx)?.trim().orEmpty()
                    var artistName = r.getOrNull(artistIdx)?.trim().orEmpty()
                    val fileId = r.getOrNull(fileIdIdx)?.trim().orEmpty()
                    val urlPrefix = "https://docs.google.com/document/d/$fileId"

                    if (songName.isBlank() || artistName.isBlank() || urlPrefix.isBlank()
                        || artistName.contains("(In Progress)") || artistName.contains("(IN PROGRESS)")) {
                        skipped++
                        i++
                        continue
                    }

                    if (artistName.contains(" & ")) {
                        artistName = artistName.split(" & ").first()
                    }

                    val existingArtist = existingArtists[artistName]
                    if (existingArtist != null) {
                        val existingSong = existingSongs[existingArtist.uuid]?.firstOrNull { it.name == songName }
                        if (existingSong != null) {
                            // Both artist and song already exist in db, skip
                            continue
                        }
                    }

                    // Look up song / artist details
                    val lookupResult = deezerLookupTrack(
                        httpClient,
                        songName,
                        artistName,
                    )

                    val artistKey = artistName.lowercase()
                    val artistUuid = artistCache.getOrPut(artistKey) {
                        // Upsert by name in DB; return uuid
                        val existing = existingArtists[artistName]
                        if (existing != null) existing.uuid
                        else {
                            val created = NewArtist(
                                name = artistName,
                                imageUrl = lookupResult?.artist?.picture_big,
                            )
                            newArtists.add(created)
                            artistsUpserted++
                            created.uuid.toString()
                        }
                    }

                    // Upsert song by (artistUuid + songName) or by docUrl (your choice)
                    val existingSong = existingSongs[artistUuid]?.first { it.name == songName }
                    if (existingSong == null) {
                        val album = lookupResult?.album?.title ?: "Unknown Album"
                        val artwork = lookupResult?.album?.cover_big ?: ""

                        val existingAlbumUuid: String = existingAlbums[artistUuid]
                            ?.first { it.name == album }
                            ?.uuid
                            ?: run {
                                val newlyAddedAlbum = newAlbums[artistUuid]?.firstOrNull { it.name == album }

                                if (newlyAddedAlbum != null) {
                                    newlyAddedAlbum.uuid.toString()
                                } else {
                                    val created = NewAlbum(
                                        artistUuid = UUID.fromString(artistUuid),
                                        name = album,
                                        imageUrl = artwork
                                    )

                                    if (newAlbums.containsKey(artistUuid)) {
                                        newAlbums[artistUuid]!!.add(created)
                                    } else {
                                        newAlbums[artistUuid] = arrayListOf(created)
                                    }
                                    created.uuid.toString()
                                }
                            }

                        val createdSong = NewSong(
                            artistUuid = UUID.fromString(artistUuid),
                            albumUuid = UUID.fromString(existingAlbumUuid),
                            name = songName,
                            docUrl = urlPrefix
                        )
                        newSongs.add(createdSong)
                        songsUpserted++
                    }
                    i++
                } catch (t: Throwable) {
                    if (t.message?.contains("Rate limit") == true) {
                        delay(1000)
                    } else {
                        errors += "Row ${i + 2}: ${t.message ?: t}"
                        i++
                    }
                }
            }

            // Okay now do all the inserts
            repo.batchInsertArtist(newArtists)
            repo.batchInsertAlbum(newAlbums.values.flatten())
            repo.batchInsertSong(newSongs)

            call.respond(ImportResult(artistsUpserted, songsUpserted, skipped, errors))
        }
    }
}

/**
 * Minimal CSV parser (handles quoted commas).
 * If you already have a CSV lib, use that instead.
 */
fun parseCsv(csv: String): List<List<String>> {
    val lines = csv.split("\n").drop(5).map { it.trimEnd('\r') }.filter { it.isNotBlank() }
    return lines.map { line ->
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    // double quote escape
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    out += sb.toString()
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        out
    }
}
