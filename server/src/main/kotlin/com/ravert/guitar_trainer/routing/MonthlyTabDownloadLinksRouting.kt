package com.ravert.guitar_trainer.routing

import com.ravert.guitar_trainer.db.MonthlyTabDownloadLinkRecord
import com.ravert.guitar_trainer.db.MonthlyTabDownloadLinksRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val DefaultMonthlyLinksTimeZone = "America/Phoenix"

@Serializable
data class MonthlyTabDownloadSongDto(
    val songName: String,
    val docUrl: String,
)

@Serializable
data class MonthlyTabDownloadArtistDto(
    val artistName: String,
    val songs: List<MonthlyTabDownloadSongDto>,
)

@Serializable
data class MonthlyTabDownloadPageResponse(
    val monthKey: String,
    val cutoffAt: Long,
    val tabCount: Int,
    val artists: List<MonthlyTabDownloadArtistDto>,
)

@Serializable
data class AdminMonthlyTabDownloadLinkDto(
    val uuid: String,
    val monthKey: String,
    val publicUrl: String,
    val cutoffAt: Long,
    val createdAt: Long,
    val tabCount: Long,
)

@Serializable
data class AdminMonthlyTabDownloadLinksResponse(
    val links: List<AdminMonthlyTabDownloadLinkDto>,
)

data class MonthlyLinkWindow(
    val monthKey: String,
    val cutoffAt: Long,
    val nextMonthAt: Long,
)

fun Application.configureMonthlyTabDownloadLinkRoutes(
    repository: MonthlyTabDownloadLinksRepository,
) {
    routing {
        get("/tab-download-links/{token}") {
            val token = call.parameters["token"]
                ?.takeIf { it.length in 32..128 }
                ?: return@get call.respond(HttpStatusCode.NotFound, "Link not found")
            val link = repository.findByPublicToken(token)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Link not found")
            val entries = repository.getEntries(link.cutoffAt)
            val artists = entries
                .groupBy { it.artistName }
                .map { (artistName, songs) ->
                    MonthlyTabDownloadArtistDto(
                        artistName = artistName,
                        songs = songs.map { song ->
                            MonthlyTabDownloadSongDto(
                                songName = song.songName,
                                docUrl = song.docUrl,
                            )
                        },
                    )
                }

            call.respond(
                MonthlyTabDownloadPageResponse(
                    monthKey = link.monthKey,
                    cutoffAt = link.cutoffAt,
                    tabCount = entries.size,
                    artists = artists,
                )
            )
        }

        get("/admin/tab-download-links") {
            if (!call.hasAdminAuth()) {
                return@get call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }

            call.respond(
                AdminMonthlyTabDownloadLinksResponse(
                    links = repository.listLinks().map { it.toAdminDto(repository) },
                )
            )
        }

        post("/admin/tab-download-links/generate") {
            if (!call.hasAdminAuth()) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }
            val appPublicUrl = System.getenv("APP_PUBLIC_URL")
                ?.takeIf { it.isNotBlank() }
                ?: return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    "APP_PUBLIC_URL is not configured",
                )
            val window = currentMonthlyLinkWindow()
            val link = repository.ensureLink(
                monthKey = window.monthKey,
                cutoffAt = window.cutoffAt,
                appPublicUrl = appPublicUrl,
            )
            call.respond(link.toAdminDto(repository))
        }
    }

    scheduleMonthlyTabDownloadLinks(repository)
}

fun currentMonthlyLinkWindow(
    now: Instant = Instant.now(),
    zone: ZoneId = monthlyLinksZone(),
): MonthlyLinkWindow {
    val current = now.atZone(zone)
    val monthStart = current.withDayOfMonth(1).toLocalDate().atStartOfDay(zone)
    val nextMonth = monthStart.plusMonths(1)
    return MonthlyLinkWindow(
        monthKey = monthStart.format(DateTimeFormatter.ofPattern("yyyy-MM")),
        cutoffAt = monthStart.toInstant().toEpochMilli(),
        nextMonthAt = nextMonth.toInstant().toEpochMilli(),
    )
}

private fun Application.scheduleMonthlyTabDownloadLinks(
    repository: MonthlyTabDownloadLinksRepository,
) {
    val envAllowsScheduling = System.getenv("MONTHLY_TAB_LINKS_ENABLED") == "true" ||
        System.getenv("ENVIRONMENT") == "production" ||
        System.getenv("KTOR_ENV") == "production" ||
        System.getenv("RENDER") == "true"
    val appPublicUrl = System.getenv("APP_PUBLIC_URL")?.takeIf { it.isNotBlank() }
    if (!envAllowsScheduling || appPublicUrl == null) return

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    monitor.subscribe(ApplicationStopped) { scope.cancel() }

    scope.launch {
        while (isActive) {
            ensureScheduledLink(repository, currentMonthlyLinkWindow(), appPublicUrl)
            delay(millisUntilNextMonthlyLinkCheck())
        }
    }
}

private fun millisUntilNextMonthlyLinkCheck(
    now: Instant = Instant.now(),
    zone: ZoneId = monthlyLinksZone(),
): Long {
    val current = now.atZone(zone)
    var next = current.plusDays(1).toLocalDate().atStartOfDay(zone).plusMinutes(5)
    if (!next.isAfter(current)) next = next.plusDays(1)
    return (next.toInstant().toEpochMilli() - now.toEpochMilli()).coerceAtLeast(1_000L)
}

private fun Application.ensureScheduledLink(
    repository: MonthlyTabDownloadLinksRepository,
    window: MonthlyLinkWindow,
    appPublicUrl: String,
) {
    try {
        val link = repository.ensureLink(
            monthKey = window.monthKey,
            cutoffAt = window.cutoffAt,
            appPublicUrl = appPublicUrl,
        )
        log.info("Monthly tab download link ready for ${link.monthKey}")
    } catch (t: Throwable) {
        log.warn("Monthly tab download link generation failed: ${t::class.simpleName}: ${t.message}")
    }
}

private fun monthlyLinksZone(): ZoneId = ZoneId.of(
    System.getenv("MONTHLY_TAB_LINK_TIME_ZONE")
        ?.takeIf { it.isNotBlank() }
        ?: DefaultMonthlyLinksTimeZone
)

private fun MonthlyTabDownloadLinkRecord.toAdminDto(
    repository: MonthlyTabDownloadLinksRepository,
) = AdminMonthlyTabDownloadLinkDto(
    uuid = uuid.toString(),
    monthKey = monthKey,
    publicUrl = publicUrl,
    cutoffAt = cutoffAt,
    createdAt = createdAt,
    tabCount = repository.countEntries(cutoffAt),
)
