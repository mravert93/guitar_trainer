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
    val tuning: String? = null,
    val capo: String? = null,
    val chords: String? = null,
    val technique: String? = null,
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
    val matchFields: List<String>,
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
            this[SongsTable.tuning] = it.tuning
            this[SongsTable.capo] = it.capo
            this[SongsTable.chords] = it.chords
            this[SongsTable.technique] = it.technique
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

    fun getSongs(): List<Song> = transaction {
        SongsTable
            .selectAll()
            .orderBy(SongsTable.name to SortOrder.ASC)
            .map { row ->
                row.toSong()
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
        tuning: String? = null,
        capo: String? = null,
        chords: String? = null,
        technique: String? = null,
    ): Song = transaction {
        if (uuid != null) {
            SongsTable.update({ SongsTable.id eq UUID.fromString(uuid)}) {
                it[SongsTable.artistId] = UUID.fromString(artistId)
                it[SongsTable.albumId] = UUID.fromString(albumId)
                it[SongsTable.name] = name
                it[SongsTable.lengthSeconds] = lengthSeconds
                it[SongsTable.bpm] = bpm
                it[SongsTable.docUrl] = docUrl
                it[SongsTable.tuning] = tuning
                it[SongsTable.capo] = capo
                it[SongsTable.chords] = chords
                it[SongsTable.technique] = technique
            }
            Song(uuid, artistId, albumId, name, lengthSeconds, bpm, docUrl, tuning, capo, chords, technique)
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
                it[SongsTable.tuning] = tuning
                it[SongsTable.capo] = capo
                it[SongsTable.chords] = chords
                it[SongsTable.technique] = technique
            }
            Song(id.toString(), artistId, albumId, name, lengthSeconds, bpm, docUrl, tuning, capo, chords, technique)
        }
    }

    fun getSongById(id: String): Song? = transaction {
        SongsTable
            .selectAll().where { SongsTable.id eq UUID.fromString(id) }
            .singleOrNull()
            ?.toSong()
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
        }
    }

    fun getSongTabDetails(): List<SongTabDetail> = transaction {
        SongsTable
            .join(ArtistsTable, JoinType.INNER, SongsTable.artistId, ArtistsTable.id)
            .selectAll()
            .orderBy(ArtistsTable.name to SortOrder.ASC, SongsTable.name to SortOrder.ASC)
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
                )
            }
    }

    fun search(query: String, limit: Int): List<LibrarySearchResult> = transaction {
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
                    matchFields = listOf("artistName"),
                )
            }

        val songResults = SongsTable
            .join(ArtistsTable, JoinType.INNER, SongsTable.artistId, ArtistsTable.id)
            .selectAll()
            .mapNotNull { row ->
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
        tuning = this[SongsTable.tuning],
        capo = this[SongsTable.capo],
        chords = this[SongsTable.chords],
        technique = this[SongsTable.technique],
    )
}

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
