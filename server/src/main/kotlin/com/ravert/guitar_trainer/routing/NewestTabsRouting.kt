package com.ravert.guitar_trainer.routing

import com.ravert.guitar_trainer.db.LibraryRepository
import com.ravert.guitar_trainer.db.NewestTabRecord
import com.ravert.guitar_trainer.guitartrainer.datamodels.Song
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

private const val NewestTabsLimit = 3
private const val NewestTabsWindowDays = 7
private const val MillisPerDay = 24L * 60L * 60L * 1000L

@Serializable
data class NewestTabDto(
    val song: Song,
    val artistName: String,
    val artistImageUrl: String? = null,
    val albumName: String? = null,
    val albumImageUrl: String? = null,
)

@Serializable
data class NewestTabsResponse(
    val tabs: List<NewestTabDto>,
    val windowStart: Long,
    val windowDays: Int = NewestTabsWindowDays,
)

fun Application.configureNewestTabsRoutes(repository: LibraryRepository) {
    routing {
        get("/songs/new") {
            val windowStart = System.currentTimeMillis() - (NewestTabsWindowDays * MillisPerDay)
            val tabs = repository
                .getNewestTabs(createdSince = windowStart, limit = NewestTabsLimit)
                .map(NewestTabRecord::toDto)

            call.respond(
                NewestTabsResponse(
                    tabs = tabs,
                    windowStart = windowStart,
                )
            )
        }
    }
}

private fun NewestTabRecord.toDto() = NewestTabDto(
    song = song,
    artistName = artistName,
    artistImageUrl = artistImageUrl,
    albumName = albumName,
    albumImageUrl = albumImageUrl,
)
