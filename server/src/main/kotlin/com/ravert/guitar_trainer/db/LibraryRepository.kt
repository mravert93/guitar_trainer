package com.ravert.guitar_trainer.db

import com.ravert.guitar_trainer.guitartrainer.datamodels.Album
import com.ravert.guitar_trainer.guitartrainer.datamodels.Artist
import com.ravert.guitar_trainer.guitartrainer.datamodels.Song
import com.ravert.guitar_trainer.import.SongTabDetail
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

data class NewArtist(
    val uuid: UUID = UUID.randomUUID(),
    val name: String,
    val imageUrl: String?,
)

data class NewAlbum(
    val uuid: UUID = UUID.randomUUID(),
    val artistUuid: UUID,
    val name: String,
    val imageUrl: String?,
)

data class NewSong(
    val uuid: UUID = UUID.randomUUID(),
    val artistUuid: UUID,
    val albumUuid: UUID,
    val name: String,
    val lengthSeconds: Int = -1,
    val bpm: Int = -1,
    val docUrl: String,
    val youtubeLink: String? = null,
    val tuning: String? = null,
    val capo: String? = null,
    val chords: String? = null,
    val technique: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val releaseAt: Long? = null,
)

@Serializable
data class LibrarySearchResult(
    val type: String,
    val uuid: String,
    val title: String,
    val subtitle: String?,
    val artistUuid: String?,
    val artistName: String?,
    val songUuid: String?,
    val songName: String?,
    val albumUuid: String?,
    val imageUrl: String?,
    val tuning: String?,
    val capo: String?,
    val chords: String?,
    val technique: String?,
    val createdAt: Long?,
    val updatedAt: Long?,
    val matchFields: List<String>,
)

data class NewestTabRecord(
    val song: Song,
    val artistName: String,
    val artistImageUrl: String?,
    val albumName: String?,
    val albumImageUrl: String?,
)

data class SongVideoRecord(
    val song: Song,
    val publicId: String?,
    val format: String?,
    val version: Long?,
    val durationSeconds: Double?,
)

class LibraryRepository {
    fun getArtists(): List<Artist> = transaction {
        ArtistsTable
            .selectAll()
            .orderBy(ArtistsTable.name to SortOrder.ASC)
            .map { row ->
                Artist(
                    uuid = row[ArtistsTable.id].toString(),
                    name = row[ArtistsTable.name],
                    image = row[ArtistsTable.imageUrl] ?: ""
                )
            }
    }

    fun addArtist(uuid: String?, name: String, imageUrl: String?): Artist = transaction {
        if (uuid != null) {
            ArtistsTable.update({ ArtistsTable.id eq UUID.fromString(uuid) }) {
                it[ArtistsTable.name] = name
                it[ArtistsTable.imageUrl] = imageUrl
            }
            Artist(uuid, name, imageUrl ?: "")
        } else {
            val id = UUID.randomUUID()
            ArtistsTable.insert {
                it[ArtistsTable.id] = id
                it[ArtistsTable.name] = name
                it[ArtistsTable.imageUrl] = imageUrl
            }
            Artist(id.toString(), name, imageUrl ?: "")
        }
    }

    fun batchInsertArtist(artists: List<NewArtist>) = transaction {
        ArtistsTable.batchInsert(artists) {
            this[ArtistsTable.id] = it.uuid
            this[ArtistsTable.name] = it.name
            this[ArtistsTable.imageUrl] = it.imageUrl
        }
    }

    fun batchInsertAlbum(albums: List<NewAlbum>) = transaction {
        AlbumsTable.batchInsert(albums) {
            this[AlbumsTable.id] = it.uuid
            this[AlbumsTable.artistId] = it.artistUuid
            this[AlbumsTable.name] = it.name
            this[AlbumsTable.imageUrl] = it.imageUrl
        }
    }

    fun batchInsertSong(songs: List<NewSong>) = transaction {
        SongsTable.batchInsert(songs) {
            this[SongsTable.id] = it.uuid
            this[SongsTable.name] = it.name
            this[SongsTable.artistId] = it.artistUuid
            this[SongsTable.albumId] = it.albumUuid
            this[SongsTable.bpm] = it.bpm
            this[SongsTable.lengthSeconds] = it.lengthSeconds
            this[SongsTable.docUrl] = it.docUrl
            this[SongsTable.youtubeLink] = it.youtubeLink
            this[SongsTable.tuning] = it.tuning
            this[SongsTable.capo] = it.capo
            this[SongsTable.chords] = it.chords
            this[SongsTable.technique] = it.technique
            this[SongsTable.createdAt] = it.createdAt
            this[SongsTable.updatedAt] = it.updatedAt
            this[SongsTable.releaseAt] = it.releaseAt
        }
    }

    fun findArtistByName(name: String): Artist? = transaction {
        val row = ArtistsTable
            .selectAll()
            .where { ArtistsTable.name eq name }
            .singleOrNull() ?: return@transaction null

        Artist(
            uuid = row[ArtistsTable.id].toString(),
            name = row[ArtistsTable.name],
            image = row[ArtistsTable.imageUrl] ?: ""
        )
    }

    fun getAlbums(): List<Album> = transaction {
        AlbumsTable
            .selectAll()
            .orderBy(AlbumsTable.name to SortOrder.ASC)
            .map { row ->
                Album(
                    uuid = row[AlbumsTable.id].toString(),
                    artistUuid = row[AlbumsTable.artistId].toString(),
                    name = row[AlbumsTable.name],
                    image = row[AlbumsTable.imageUrl] ?: ""
                )
            }
    }

    fun findAlbumByArtistAndName(artistId: UUID, albumName: String): Album? = transaction {
        val row = AlbumsTable
            .selectAll()
            .where { (AlbumsTable.artistId eq artistId) and (AlbumsTable.name eq albumName) }
            .singleOrNull() ?: return@transaction null

        Album(
            uuid = row[AlbumsTable.id].toString(),
            artistUuid = row[AlbumsTable.artistId].toString(),
            name = row[AlbumsTable.name],
            image = row[AlbumsTable.imageUrl] ?: ""
        )
    }

    fun addAlbum(uuid: String?, artistId: String, name: String, imageUrl: String?): Album = transaction {
        if (uuid != null) {
            AlbumsTable.update({ AlbumsTable.id eq UUID.fromString(uuid)}) {
                it[AlbumsTable.name] = name
                it[AlbumsTable.artistId] = UUID.fromString(artistId)
                it[AlbumsTable.imageUrl] = imageUrl
            }
            Album(uuid, artistId, name, imageUrl ?: "")
        } else {
            val id = UUID.randomUUID()
            AlbumsTable.insert {
                it[AlbumsTable.id] = id
                it[AlbumsTable.artistId] = UUID.fromString(artistId)
                it[AlbumsTable.name] = name
                it[AlbumsTable.imageUrl] = imageUrl
            }
            Album(id.toString(), artistId, name, imageUrl ?: "")
        }
    }

    fun deleteAlbum(albumId: UUID) = transaction {
        AlbumsTable.deleteWhere { AlbumsTable.id eq albumId }
    }

    fun deleteSong(songId: UUID) = transaction {
        SongsTable.deleteWhere { SongsTable.id eq songId }
    }

    fun deleteArtists(artistId: UUID) = transaction {
        ArtistsTable.deleteWhere { ArtistsTable.id eq artistId }
    }

    fun getSongs(includeUnreleased: Boolean = true, now: Long = System.currentTimeMillis()): List<Song> = transaction {
        SongsTable
            .selectAll()
            .orderBy(SongsTable.name to SortOrder.ASC)
            .map { it.toSong() }
            .filter { includeUnreleased || it.isPublicAt(now) }
    }

    fun getNewestTabs(createdSince: Long, limit: Int, now: Long = System.currentTimeMillis()): List<NewestTabRecord> = transaction {
        SongsTable
            .join(ArtistsTable, JoinType.INNER, SongsTable.artistId, ArtistsTable.id)
            .join(AlbumsTable, JoinType.LEFT, SongsTable.albumId, AlbumsTable.id)
            .selectAll()
            .where {
                (SongsTable.createdAt greaterEq createdSince) and
                    (SongsTable.releaseAt.isNull() or (SongsTable.releaseAt lessEq now))
            }
            .orderBy(SongsTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                NewestTabRecord(
                    song = row.toSong(),
                    artistName = row[ArtistsTable.name],
                    artistImageUrl = row[ArtistsTable.imageUrl],
                    albumName = row.getOrNull(AlbumsTable.name),
                    albumImageUrl = row.getOrNull(AlbumsTable.imageUrl),
                )
            }
    }

    fun getUpcomingTabs(now: Long = System.currentTimeMillis()): List<NewestTabRecord> = transaction {
        SongsTable
            .join(ArtistsTable, JoinType.INNER, SongsTable.artistId, ArtistsTable.id)
            .join(AlbumsTable, JoinType.LEFT, SongsTable.albumId, AlbumsTable.id)
            .selectAll()
            .where { SongsTable.releaseAt greater now }
            .orderBy(SongsTable.releaseAt to SortOrder.ASC)
            .map { row ->
                NewestTabRecord(
                    song = row.toSong(),
                    artistName = row[ArtistsTable.name],
                    artistImageUrl = row[ArtistsTable.imageUrl],
                    albumName = row.getOrNull(AlbumsTable.name),
                    albumImageUrl = row.getOrNull(AlbumsTable.imageUrl),
                )
            }
    }

    fun findSongByArtistAndName(artistId: UUID, songName: String): Song? = transaction {
        val row = SongsTable
            .selectAll()
            .where { (SongsTable.name eq songName) and (SongsTable.artistId eq artistId) }
            .singleOrNull() ?: return@transaction null

        row.toSong()
    }

    fun updateSong(songUuid: UUID, docUrl: String) {
        transaction {
            SongsTable.update({ SongsTable.id eq songUuid }) {
                it[SongsTable.docUrl] = docUrl
                it[SongsTable.updatedAt] = System.currentTimeMillis()
            }
        }
    }

    fun addSong(
        uuid: String?,
        artistId: String,
        albumId: String,
        name: String,
        lengthSeconds: Int,
        bpm: Int,
        docUrl: String,
        youtubeLink: String? = null,
        tuning: String? = null,
        capo: String? = null,
        chords: String? = null,
        technique: String? = null,
        releaseAt: Long? = null,
    ): Song = transaction {
        val now = System.currentTimeMillis()
        if (uuid != null) {
            val songUuid = UUID.fromString(uuid)
            SongsTable.update({ SongsTable.id eq songUuid}) {
                it[SongsTable.artistId] = UUID.fromString(artistId)
                it[SongsTable.albumId] = UUID.fromString(albumId)
                it[SongsTable.name] = name
                it[SongsTable.lengthSeconds] = lengthSeconds
                it[SongsTable.bpm] = bpm
                it[SongsTable.docUrl] = docUrl
                it[SongsTable.youtubeLink] = youtubeLink
                it[SongsTable.tuning] = tuning
                it[SongsTable.capo] = capo
                it[SongsTable.chords] = chords
                it[SongsTable.technique] = technique
                it[SongsTable.updatedAt] = now
            }
            SongsTable.selectAll()
                .where { SongsTable.id eq songUuid }
                .single()
                .toSong()
        } else {
            val id = UUID.randomUUID()
            SongsTable.insert {
                it[SongsTable.id] = id
                it[SongsTable.artistId] = UUID.fromString(artistId)
                it[SongsTable.albumId] = UUID.fromString(albumId)
                it[SongsTable.name] = name
                it[SongsTable.lengthSeconds] = lengthSeconds
                it[SongsTable.bpm] = bpm
                it[SongsTable.docUrl] = docUrl
                it[SongsTable.youtubeLink] = youtubeLink
                it[SongsTable.tuning] = tuning
                it[SongsTable.capo] = capo
                it[SongsTable.chords] = chords
                it[SongsTable.technique] = technique
                it[SongsTable.releaseAt] = releaseAt
                it[SongsTable.createdAt] = now
                it[SongsTable.updatedAt] = now
            }
            Song(
                uuid = id.toString(),
                artistUuid = artistId,
                albumUuid = albumId,
                name = name,
                lengthSeconds = lengthSeconds,
                bpm = bpm,
                docUrl = docUrl,
                youtubeLink = youtubeLink,
                tuning = tuning,
                capo = capo,
                chords = chords,
                technique = technique,
                createdAt = now,
                updatedAt = now,
                releaseAt = releaseAt,
            )
        }
    }

    fun getSongById(id: String): Song? = transaction {
        SongsTable
            .selectAll().where { SongsTable.id eq UUID.fromString(id) }
            .singleOrNull()
            ?.toSong()
    }

    fun findSongByDocUrl(docUrl: String): Song? = transaction {
        SongsTable
            .selectAll()
            .where { SongsTable.docUrl eq docUrl }
            .limit(1)
            .singleOrNull()
            ?.toSong()
    }

    fun findSongByGoogleDocId(docId: String): Song? = transaction {
        SongsTable
            .selectAll()
            .where { SongsTable.docUrl like "%/document/d/$docId%" }
            .limit(1)
            .singleOrNull()
            ?.toSong()
    }

    fun getSongVideo(songUuid: UUID): SongVideoRecord? = transaction {
        SongsTable
            .selectAll()
            .where { SongsTable.id eq songUuid }
            .singleOrNull()
            ?.toSongVideoRecord()
    }

    fun updateSongReleaseAt(songUuid: UUID, releaseAt: Long?): SongVideoRecord? = transaction {
        val updated = SongsTable.update({ SongsTable.id eq songUuid }) {
            it[SongsTable.releaseAt] = releaseAt
            it[SongsTable.updatedAt] = System.currentTimeMillis()
        }
        if (updated == 0) return@transaction null

        SongsTable.selectAll()
            .where { SongsTable.id eq songUuid }
            .single()
            .toSongVideoRecord()
    }

    fun registerSongVideo(
        songUuid: UUID,
        publicId: String,
        format: String,
        version: Long?,
        durationSeconds: Double?,
    ): SongVideoRecord? = transaction {
        val updated = SongsTable.update({ SongsTable.id eq songUuid }) {
            it[cloudinaryVideoPublicId] = publicId
            it[cloudinaryVideoFormat] = format
            it[cloudinaryVideoVersion] = version
            it[cloudinaryVideoDurationSeconds] = durationSeconds
            it[updatedAt] = System.currentTimeMillis()
        }
        if (updated == 0) return@transaction null

        SongsTable.selectAll()
            .where { SongsTable.id eq songUuid }
            .single()
            .toSongVideoRecord()
    }

    fun removeSongVideo(songUuid: UUID): SongVideoRecord? = transaction {
        val updated = SongsTable.update({ SongsTable.id eq songUuid }) {
            it[cloudinaryVideoPublicId] = null
            it[cloudinaryVideoFormat] = null
            it[cloudinaryVideoVersion] = null
            it[cloudinaryVideoDurationSeconds] = null
            it[updatedAt] = System.currentTimeMillis()
        }
        if (updated == 0) return@transaction null

        SongsTable.selectAll()
            .where { SongsTable.id eq songUuid }
            .single()
            .toSongVideoRecord()
    }

    fun updateSongTabMetadata(
        songUuid: UUID,
        tuning: String?,
        capo: String?,
        chords: String?,
        technique: String?,
    ): Int = transaction {
        SongsTable.update({ SongsTable.id eq songUuid }) {
            it[SongsTable.tuning] = tuning
            it[SongsTable.capo] = capo
            it[SongsTable.chords] = chords
            it[SongsTable.technique] = technique
            it[SongsTable.updatedAt] = System.currentTimeMillis()
        }
    }

    fun getSongTabDetails(
        includeUnreleased: Boolean = true,
        now: Long = System.currentTimeMillis(),
    ): List<SongTabDetail> = transaction {
        SongsTable
            .join(ArtistsTable, JoinType.INNER, SongsTable.artistId, ArtistsTable.id)
            .selectAll()
            .orderBy(ArtistsTable.name to SortOrder.ASC, SongsTable.name to SortOrder.ASC)
            .filter { includeUnreleased || it[SongsTable.releaseAt]?.let { releaseAt -> releaseAt <= now } != false }
            .map { row ->
                SongTabDetail(
                    songId = row[SongsTable.id].toString(),
                    artistId = row[ArtistsTable.id].toString(),
                    artistName = row[ArtistsTable.name],
                    songName = row[SongsTable.name],
                    tuning = row[SongsTable.tuning].orEmpty(),
                    capo = row[SongsTable.capo].orEmpty(),
                    chords = row[SongsTable.chords].orEmpty(),
                    technique = row[SongsTable.technique].orEmpty(),
                    createdAt = row[SongsTable.createdAt],
                    updatedAt = row[SongsTable.updatedAt],
                    releaseAt = row[SongsTable.releaseAt],
                    isEarlyAccess = row[SongsTable.releaseAt]?.let { it > now } == true,
                )
            }
    }

    fun search(
        query: String,
        limit: Int,
        includeUnreleased: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): List<LibrarySearchResult> = transaction {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) return@transaction emptyList()

        val artistResults = ArtistsTable
            .selectAll()
            .mapNotNull { row ->
                val artistName = row[ArtistsTable.name]
                if (!artistName.lowercase().contains(normalizedQuery)) return@mapNotNull null

                LibrarySearchResult(
                    type = "artist",
                    uuid = row[ArtistsTable.id].toString(),
                    title = artistName,
                    subtitle = "Artist",
                    artistUuid = row[ArtistsTable.id].toString(),
                    artistName = artistName,
                    songUuid = null,
                    songName = null,
                    albumUuid = null,
                    imageUrl = row[ArtistsTable.imageUrl],
                    tuning = null,
                    capo = null,
                    chords = null,
                    technique = null,
                    createdAt = null,
                    updatedAt = null,
                    matchFields = listOf("artistName"),
                )
            }

        val songResults = SongsTable
            .join(ArtistsTable, JoinType.INNER, SongsTable.artistId, ArtistsTable.id)
            .selectAll()
            .mapNotNull { row ->
                if (!includeUnreleased && !row.toSong().isPublicAt(now)) return@mapNotNull null
                val songName = row[SongsTable.name]
                val artistName = row[ArtistsTable.name]
                val tuning = row[SongsTable.tuning]
                val capo = row[SongsTable.capo]
                val chords = row[SongsTable.chords]
                val technique = row[SongsTable.technique]
                val matchFields = buildList {
                    if (songName.matchesSearch(normalizedQuery)) add("songName")
                    if (artistName.matchesSearch(normalizedQuery)) add("artistName")
                    if (tuning.matchesSearch(normalizedQuery)) add("tuning")
                    if (capo.matchesSearch(normalizedQuery)) add("capo")
                    if (chords.matchesSearch(normalizedQuery)) add("chords")
                    if (technique.matchesSearch(normalizedQuery)) add("technique")
                }
                if (matchFields.isEmpty()) return@mapNotNull null

                LibrarySearchResult(
                    type = "song",
                    uuid = row[SongsTable.id].toString(),
                    title = songName,
                    subtitle = artistName,
                    artistUuid = row[ArtistsTable.id].toString(),
                    artistName = artistName,
                    songUuid = row[SongsTable.id].toString(),
                    songName = songName,
                    albumUuid = row[SongsTable.albumId].toString(),
                    imageUrl = row[ArtistsTable.imageUrl],
                    tuning = tuning,
                    capo = capo,
                    chords = chords,
                    technique = technique,
                    createdAt = row[SongsTable.createdAt],
                    updatedAt = row[SongsTable.updatedAt],
                    matchFields = matchFields,
                )
            }

        (artistResults + songResults)
            .sortedWith(compareBy<LibrarySearchResult> { it.searchRank(normalizedQuery) }.thenBy { it.title.lowercase() })
            .take(limit)
    }

    private fun ResultRow.toSong() = Song(
        uuid = this[SongsTable.id].toString(),
        artistUuid = this[SongsTable.artistId].toString(),
        albumUuid = this[SongsTable.albumId].toString(),
        name = this[SongsTable.name],
        lengthSeconds = this[SongsTable.lengthSeconds],
        bpm = this[SongsTable.bpm],
        docUrl = this[SongsTable.docUrl],
        youtubeLink = this[SongsTable.youtubeLink],
        tuning = this[SongsTable.tuning],
        capo = this[SongsTable.capo],
        chords = this[SongsTable.chords],
        technique = this[SongsTable.technique],
        createdAt = this[SongsTable.createdAt],
        updatedAt = this[SongsTable.updatedAt],
        releaseAt = this[SongsTable.releaseAt],
        hasVideo = this[SongsTable.cloudinaryVideoPublicId] != null,
    )

    private fun ResultRow.toSongVideoRecord() = SongVideoRecord(
        song = toSong(),
        publicId = this[SongsTable.cloudinaryVideoPublicId],
        format = this[SongsTable.cloudinaryVideoFormat],
        version = this[SongsTable.cloudinaryVideoVersion],
        durationSeconds = this[SongsTable.cloudinaryVideoDurationSeconds],
    )
}

fun Song.isPublicAt(now: Long = System.currentTimeMillis()): Boolean =
    releaseAt?.let { it <= now } ?: true

private fun String?.matchesSearch(normalizedQuery: String): Boolean =
    this?.lowercase()?.contains(normalizedQuery) == true

private fun LibrarySearchResult.searchRank(normalizedQuery: String): Int {
    val title = title.lowercase()
    val artistName = artistName?.lowercase()
    return when {
        title == normalizedQuery -> 0
        type == "artist" && title.startsWith(normalizedQuery) -> 1
        type == "song" && title.startsWith(normalizedQuery) -> 2
        artistName == normalizedQuery -> 3
        title.contains(normalizedQuery) -> 4
        artistName?.contains(normalizedQuery) == true -> 5
        else -> 6
    }
}
