package com.ravert.guitar_trainer.routing

import com.ravert.guitar_trainer.db.AuthRepository
import com.ravert.guitar_trainer.db.LibraryRepository
import com.ravert.guitar_trainer.db.NewAlbum
import com.ravert.guitar_trainer.db.NewArtist
import com.ravert.guitar_trainer.db.NewSong
import com.ravert.guitar_trainer.import.deezerLookupTrack
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.String

@Serializable
data class ImportResult(
    val artistsUpserted: Int,
    val songsUpserted: Int,
    val skippedRows: Int,
    val errors: List<String>
)

@Serializable
data class TabMetadataSyncResult(
    val source: String,
    val updatedSongs: Int,
    val skippedRows: Int,
    val missingSongs: Int,
    val missingRows: List<String> = emptyList(),
    val errors: List<String>,
)

fun Application.configureImportRoutes(
    httpClient: HttpClient,
    repo: LibraryRepository,
    authRepository: AuthRepository,
) {
    routing {
        post("/admin/importSheet") {
            val csvUrl = call.request.queryParameters["csvUrl"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing csvUrl")

            val csvText = httpClient.get(csvUrl).bodyAsText()

            val rows = parseCsv(csvText, initialDrop = 5)
            if (rows.isEmpty()) return@post call.respond(ImportResult(0, 0, 0, listOf("CSV empty")))

            // Normalize header names
            val header = rows.first().map { it.trim() }
            fun idx(name: String): Int = header.indexOfFirst { it.equals(name, ignoreCase = true) }

            val docIdx = idx("Document Link")
            val fileIdIdx = idx("File ID").takeIf { it >= 0 } ?: idx("File ID")
            val songIdx = idx("Song Title")
            val artistIdx = idx("Artist")

            if (docIdx < 0 || songIdx < 0 || artistIdx < 0) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "CSV must include columns: Song, Artist, Document Link. Found: $header"
                )
            }

            var artistsUpserted = 0
            var songsUpserted = 0
            var skipped = 0
            val errors = mutableListOf<String>()

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

                    val existingArtist = existingArtists[artistName.lowercase()]
                    if (existingArtist != null) {
                        val existingSong = existingSongs[existingArtist.uuid]?.firstOrNull { it.name == songName }
                        if (existingSong != null) {
                            // Both artist and song already exist in db, skip
                            skipped++
                            i++
                            continue
                        }
                    }

                    // Look up song / artist details
                    val lookupResult = deezerLookupTrack(
                        httpClient,
                        songName,
                        artistName,
                    )

                    val artistUuid = existingArtist?.uuid ?: run {
                        val alreadyCreatedNew = newArtists.firstOrNull { it.name.equals(artistName, ignoreCase = true) }
                        if (alreadyCreatedNew != null) {
                            alreadyCreatedNew.uuid.toString()
                        } else {
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
                    val existingSong = existingSongs[artistUuid]?.firstOrNull { it.name == songName }
                    if (existingSong == null) {
                        val album = lookupResult?.album?.title ?: "Unknown Album"
                        val artwork = lookupResult?.album?.cover_big ?: ""

                        val existingAlbumUuid: String = existingAlbums[artistUuid]
                            ?.firstOrNull { it.name == album }
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

        post("/admin/syncTabDetails") {
            if (!call.hasAdminAuth()) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }

            val syncResult = syncTabMetadataFromSpreadsheet(httpClient, repo)
            call.respond(syncResult)
        }

        get("/admin/tabDetails") {
            val user = call.requireUser(authRepository)
            val includeUnreleased = call.hasAdminAuth() ||
                (user != null && authRepository.userHasPremium(user.uuid))
            call.respond(repo.getSongTabDetails(includeUnreleased = includeUnreleased))
        }
    }
}

private suspend fun syncTabMetadataFromSpreadsheet(
    httpClient: HttpClient,
    repo: LibraryRepository,
): TabMetadataSyncResult {
    val csvUrl = "https://docs.google.com/spreadsheets/d/1vt7Ub1EiwC9uPWJxcDiKXkEVM6j3FMOPuNIatSDjUIE/export?format=csv&gid=0"

    val csvText = httpClient.get(csvUrl).bodyAsText()
    return syncTabMetadataFromCsv(csvText, repo, source = "google-sheet")
}

private fun syncTabMetadataFromCsv(
    csvText: String,
    repo: LibraryRepository,
    source: String,
): TabMetadataSyncResult {
    val rows = parseCsv(csvText, initialDrop = 0)
    if (rows.isEmpty()) {
        return TabMetadataSyncResult(
            source = source,
            updatedSongs = 0,
            skippedRows = 0,
            missingSongs = 0,
            errors = listOf("CSV empty"),
        )
    }

    // Normalize header names
    val header = rows.first().map { it.trim() }
    fun idx(name: String): Int = header.indexOfFirst { it.equals(name, ignoreCase = true) }

    val songIdx = idx("Song")
    val artistIdx = idx("Artist")
    val tuningIdx = idx("Tuning")
    val capoIdx = idx("Capo")
    val chordsIdx = idx("Chords")
    val techniqueIdx = idx("Technique")
    if (songIdx < 0 || artistIdx < 0 || tuningIdx < 0 || capoIdx < 0 || chordsIdx < 0 || techniqueIdx < 0) {
        return TabMetadataSyncResult(
            source = source,
            updatedSongs = 0,
            skippedRows = 0,
            missingSongs = 0,
            missingRows = emptyList(),
            errors = listOf("CSV must include columns: Song, Artist, Tuning, Capo, Chords, Technique. Found: $header"),
        )
    }

    // Existing data
    val existingArtists = repo.getArtists()
        .associateBy { it.name.normalizeArtistLookupText() }
    val existingSongs = repo.getSongs()
        .groupBy { it.artistUuid }
    val existingSongsByName = repo.getSongs()
        .groupBy { it.name.normalizeLookupText() }

    // Drop header
    val droppedRows = rows.drop(1)
    var updatedSongs = 0
    var skippedRows = 0
    var missingSongs = 0
    val missingRows = mutableListOf<String>()
    val errors = mutableListOf<String>()
    droppedRows.forEach { row ->
        try {
            val songName = row.getOrNull(songIdx)?.trim().orEmpty()
            val artistName = row.getOrNull(artistIdx)?.trim().orEmpty()
            val tuning = row.getOrNull(tuningIdx).normalizeOptionalText()
            val capo = row.getOrNull(capoIdx).normalizeOptionalText()
            val chords = row.getOrNull(chordsIdx).normalizeOptionalText()
            val technique = row.getOrNull(techniqueIdx).normalizeOptionalText()

            if (songName.isBlank() || artistName.isBlank()) {
                skippedRows++
                return@forEach
            }

            // Check if song / artist exist
            val artist = existingArtists[artistName.normalizeArtistLookupText()]
            val songId = findTabMetadataSongId(
                songName = songName,
                artistId = artist?.uuid,
                existingSongs = existingSongs,
                existingSongsByName = existingSongsByName,
            )

            if (songId == null) {
                missingSongs++
                if (missingRows.size < 50) {
                    missingRows += "$artistName - $songName"
                }
                return@forEach
            }

            updatedSongs += repo.updateSongTabMetadata(
                songUuid = UUID.fromString(songId),
                tuning = tuning,
                capo = capo,
                chords = chords,
                technique = technique,
            )
        } catch (t: Throwable) {
            errors += "Row failed: ${t.message ?: t}"
        }
    }

    return TabMetadataSyncResult(
        source = source,
        updatedSongs = updatedSongs,
        skippedRows = skippedRows,
        missingSongs = missingSongs,
        missingRows = missingRows,
        errors = errors,
    )
}

/**
 * Minimal CSV parser (handles quoted commas).
 * If you already have a CSV lib, use that instead.
 */
fun parseCsv(csv: String, initialDrop: Int): List<List<String>> {
    val lines = csv.split("\n").drop(initialDrop).map { it.trimEnd('\r') }.filter { it.isNotBlank() }
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

fun String?.normalizeOptionalText(): String? =
    this
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun findTabMetadataSongId(
    songName: String,
    artistId: String?,
    existingSongs: Map<String, List<com.ravert.guitar_trainer.guitartrainer.datamodels.Song>>,
    existingSongsByName: Map<String, List<com.ravert.guitar_trainer.guitartrainer.datamodels.Song>>,
): String? {
    val normalizedSongName = songName.normalizeLookupText()
    val artistMatch = artistId?.let {
        existingSongs[it]
            ?.firstOrNull { song -> song.name.normalizeLookupText() == normalizedSongName }
            ?.uuid
    }
    if (artistMatch != null) return artistMatch

    return existingSongsByName[normalizedSongName]
        ?.singleOrNull()
        ?.uuid
}

private fun String.normalizeLookupText(): String =
    trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")

private fun String.normalizeArtistLookupText(): String =
    normalizeLookupText()
        .replace("&", "and")
        .replace(Regex("\\by\\b"), "and")
        .replace(Regex("\\s+"), " ")
