package com.ravert.guitar_trainer.routing

import com.ravert.guitar_trainer.db.AddFavoriteResult
import com.ravert.guitar_trainer.db.AuthRepository
import com.ravert.guitar_trainer.db.FavoriteSongRecord
import com.ravert.guitar_trainer.db.FavoritesRepository
import com.ravert.guitar_trainer.db.FreeFavoriteLimit
import com.ravert.guitar_trainer.guitartrainer.datamodels.Song
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class FavoriteSongDto(
    val song: Song,
    val artistName: String,
    val artistImageUrl: String? = null,
    val albumName: String? = null,
    val favoritedAt: Long,
)

@Serializable
data class FavoritesResponse(
    val favorites: List<FavoriteSongDto>,
    val favoriteCount: Int,
    val favoriteLimit: Int? = null,
    val remainingFavorites: Int? = null,
    val hasPremium: Boolean,
    val canAddMore: Boolean,
)

@Serializable
data class FavoriteLimitErrorResponse(
    val error: String = "favorite_limit_reached",
    val message: String = "Free accounts can save up to 3 favorites.",
    val favoriteCount: Int,
    val favoriteLimit: Int = FreeFavoriteLimit,
    val hasPremium: Boolean = false,
)

fun Application.configureFavoriteRoutes(
    authRepository: AuthRepository,
    favoritesRepository: FavoritesRepository,
) {
    routing {
        route("/favorites") {
            get {
                val user = call.requireUser(authRepository)
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, "Unauthorized")

                call.respond(favoritesResponse(authRepository, favoritesRepository, user.uuid))
            }

            post("/{songUuid}") {
                val user = call.requireUser(authRepository)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                val songUuid = call.parameters["songUuid"]?.toUuidOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid song UUID")
                val hasPremium = authRepository.userHasPremium(user.uuid)

                when (
                    favoritesRepository.addFavorite(
                        userUuid = user.uuid,
                        songUuid = songUuid,
                        hasPremium = hasPremium,
                        now = nowMillis(),
                    )
                ) {
                    AddFavoriteResult.ADDED -> call.respond(
                        HttpStatusCode.Created,
                        favoritesResponse(authRepository, favoritesRepository, user.uuid),
                    )

                    AddFavoriteResult.ALREADY_FAVORITED -> call.respond(
                        favoritesResponse(authRepository, favoritesRepository, user.uuid)
                    )

                    AddFavoriteResult.LIMIT_REACHED -> call.respond(
                        HttpStatusCode.Forbidden,
                        FavoriteLimitErrorResponse(
                            favoriteCount = favoritesRepository.listFavorites(user.uuid).size,
                        ),
                    )

                    AddFavoriteResult.SONG_NOT_FOUND -> call.respond(HttpStatusCode.NotFound, "Song not found")
                }
            }

            delete("/{songUuid}") {
                val user = call.requireUser(authRepository)
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                val songUuid = call.parameters["songUuid"]?.toUuidOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid song UUID")

                favoritesRepository.removeFavorite(user.uuid, songUuid)
                call.respond(favoritesResponse(authRepository, favoritesRepository, user.uuid))
            }
        }
    }
}

private suspend fun favoritesResponse(
    authRepository: AuthRepository,
    favoritesRepository: FavoritesRepository,
    userUuid: UUID,
): FavoritesResponse {
    val hasPremium = authRepository.userHasPremium(userUuid)
    val favorites = favoritesRepository.listFavorites(userUuid).map(FavoriteSongRecord::toDto)
    val remainingFavorites = if (hasPremium) null else (FreeFavoriteLimit - favorites.size).coerceAtLeast(0)
    return FavoritesResponse(
        favorites = favorites,
        favoriteCount = favorites.size,
        favoriteLimit = if (hasPremium) null else FreeFavoriteLimit,
        remainingFavorites = remainingFavorites,
        hasPremium = hasPremium,
        canAddMore = hasPremium || favorites.size < FreeFavoriteLimit,
    )
}

private fun FavoriteSongRecord.toDto() = FavoriteSongDto(
    song = song,
    artistName = artistName,
    artistImageUrl = artistImageUrl,
    albumName = albumName,
    favoritedAt = favoritedAt,
)

private fun String.toUuidOrNull(): UUID? = try {
    UUID.fromString(this)
} catch (_: IllegalArgumentException) {
    null
}
