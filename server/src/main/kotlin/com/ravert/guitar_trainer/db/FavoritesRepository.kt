package com.ravert.guitar_trainer.db

import com.ravert.guitar_trainer.guitartrainer.datamodels.Song
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

const val FreeFavoriteLimit = 3

data class FavoriteSongRecord(
    val song: Song,
    val artistName: String,
    val artistImageUrl: String?,
    val albumName: String?,
    val favoritedAt: Long,
)

enum class AddFavoriteResult {
    ADDED,
    ALREADY_FAVORITED,
    LIMIT_REACHED,
    SONG_NOT_FOUND,
}

class FavoritesRepository {
    fun listFavorites(userUuid: UUID): List<FavoriteSongRecord> = transaction {
        UserSongFavoritesTable
            .join(SongsTable, JoinType.INNER, UserSongFavoritesTable.songUuid, SongsTable.id)
            .join(ArtistsTable, JoinType.INNER, SongsTable.artistId, ArtistsTable.id)
            .join(AlbumsTable, JoinType.LEFT, SongsTable.albumId, AlbumsTable.id)
            .selectAll()
            .where { UserSongFavoritesTable.userUuid eq userUuid }
            .orderBy(UserSongFavoritesTable.createdAt to SortOrder.DESC)
            .map { it.toFavoriteSongRecord() }
    }

    fun addFavorite(
        userUuid: UUID,
        songUuid: UUID,
        hasPremium: Boolean,
        now: Long,
    ): AddFavoriteResult = transaction {
        // Serialize additions for this user so concurrent requests cannot exceed the free limit.
        UsersTable
            .selectAll()
            .where { UsersTable.uuid eq userUuid }
            .forUpdate()
            .singleOrNull()
            ?: return@transaction AddFavoriteResult.SONG_NOT_FOUND

        val songExists = !SongsTable
            .selectAll()
            .where { SongsTable.id eq songUuid }
            .limit(1)
            .empty()
        if (!songExists) return@transaction AddFavoriteResult.SONG_NOT_FOUND

        val alreadyFavorited = !UserSongFavoritesTable
            .selectAll()
            .where {
                (UserSongFavoritesTable.userUuid eq userUuid) and
                    (UserSongFavoritesTable.songUuid eq songUuid)
            }
            .limit(1)
            .empty()
        if (alreadyFavorited) return@transaction AddFavoriteResult.ALREADY_FAVORITED

        val favoriteCount = UserSongFavoritesTable
            .selectAll()
            .where { UserSongFavoritesTable.userUuid eq userUuid }
            .count()
        if (favoriteLimitReached(hasPremium, favoriteCount)) {
            return@transaction AddFavoriteResult.LIMIT_REACHED
        }

        UserSongFavoritesTable.insert {
            it[uuid] = UUID.randomUUID()
            it[UserSongFavoritesTable.userUuid] = userUuid
            it[UserSongFavoritesTable.songUuid] = songUuid
            it[createdAt] = now
        }
        AddFavoriteResult.ADDED
    }

    fun removeFavorite(userUuid: UUID, songUuid: UUID): Int = transaction {
        UserSongFavoritesTable.deleteWhere {
            (UserSongFavoritesTable.userUuid eq userUuid) and
                (UserSongFavoritesTable.songUuid eq songUuid)
        }
    }

    private fun ResultRow.toFavoriteSongRecord() = FavoriteSongRecord(
        song = Song(
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
        ),
        artistName = this[ArtistsTable.name],
        artistImageUrl = this[ArtistsTable.imageUrl],
        albumName = this.getOrNull(AlbumsTable.name),
        favoritedAt = this[UserSongFavoritesTable.createdAt],
    )
}

internal fun favoriteLimitReached(hasPremium: Boolean, favoriteCount: Long): Boolean =
    !hasPremium && favoriteCount >= FreeFavoriteLimit
