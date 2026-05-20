package com.ravert.guitar_trainer.routing

import com.ravert.guitar_trainer.db.AuthRepository
import com.ravert.guitar_trainer.db.YoutubeMemberRecord
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.text.Charsets.UTF_8

private const val YoutubeMembersScope = "https://www.googleapis.com/auth/youtube.channel-memberships.creator"

@Serializable
data class YoutubeSyncResponse(
    val success: Boolean,
    val memberCount: Int,
)

@Serializable
data class YoutubeOAuthUrlRequest(
    val redirectUri: String,
)

@Serializable
data class YoutubeOAuthUrlResponse(
    val url: String,
)

@Serializable
data class YoutubeOAuthCodeRequest(
    val code: String,
    val redirectUri: String,
)

@Serializable
data class YoutubeOAuthTokenResponse(
    val refreshToken: String?,
)

@Serializable
private data class GoogleTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
)

@Serializable
private data class GoogleAuthCodeTokenResponse(
    @SerialName("refresh_token")
    val refreshToken: String? = null,
)

@Serializable
private data class YoutubeMembersResponse(
    val items: List<YoutubeMemberItem> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
private data class YoutubeMemberItem(
    val snippet: YoutubeMemberSnippet? = null,
)

@Serializable
private data class YoutubeMemberSnippet(
    val memberDetails: YoutubeMemberDetails? = null,
    val membershipsDetails: YoutubeMembershipsDetails? = null,
)

@Serializable
private data class YoutubeMemberDetails(
    val channelId: String? = null,
    val channelUrl: String? = null,
    val displayName: String? = null,
    val profileImageUrl: String? = null,
)

@Serializable
private data class YoutubeMembershipsDetails(
    val highestAccessibleLevelDisplayName: String? = null,
    val membershipsDuration: YoutubeMembershipsDuration? = null,
)

@Serializable
private data class YoutubeMembershipsDuration(
    val memberSince: String? = null,
)

fun Application.configureYoutubeMemberRoutes(
    authRepository: AuthRepository,
    httpClient: HttpClient,
) {
    routing {
        post("/admin/youtube/sync-members") {
            if (!call.hasAdminAuth()) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }

            val memberCount = try {
                syncYoutubeMembers(authRepository, httpClient)
            } catch (e: IllegalStateException) {
                return@post call.respond(HttpStatusCode.InternalServerError, e.message ?: "YouTube sync failed")
            }

            call.respond(YoutubeSyncResponse(success = true, memberCount = memberCount))
        }

        post("/admin/youtube/oauth-url") {
            if (!call.hasAdminAuth()) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }

            val req = call.receive<YoutubeOAuthUrlRequest>()
            val clientId = System.getenv("YOUTUBE_CLIENT_ID")
                ?: return@post call.respond(HttpStatusCode.InternalServerError, "YOUTUBE_CLIENT_ID is not configured")

            call.respond(YoutubeOAuthUrlResponse(url = buildYoutubeOAuthUrl(clientId, req.redirectUri)))
        }

        post("/admin/youtube/exchange-code") {
            if (!call.hasAdminAuth()) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }

            val req = call.receive<YoutubeOAuthCodeRequest>()
            val refreshToken = try {
                exchangeYoutubeOAuthCode(httpClient, req.code, req.redirectUri)
            } catch (e: IllegalStateException) {
                return@post call.respond(HttpStatusCode.InternalServerError, e.message ?: "YouTube OAuth failed")
            }
            call.respond(YoutubeOAuthTokenResponse(refreshToken = refreshToken))
        }
    }

    scheduleNightlyYoutubeSync(authRepository, httpClient)
}

suspend fun syncYoutubeMembers(
    authRepository: AuthRepository,
    httpClient: HttpClient,
): Int {
    val accessToken = fetchYoutubeAccessToken(httpClient)
    val members = fetchYoutubeMembers(httpClient, accessToken)
        .distinctBy { it.normalizedDisplayName }
    val now = nowMillis()

    authRepository.upsertYoutubeMembers(members, now)
    authRepository.grantYoutubePremiumForCurrentMatches(members, now)
    authRepository.deactivateYoutubeEntitlementsMissingFromSync(
        seenYoutubeChannelIds = members.mapNotNull { it.youtubeChannelId }.toSet(),
        now = now,
    )

    return members.size
}

private suspend fun fetchYoutubeAccessToken(httpClient: HttpClient): String {
    return youtubeAccessTokenOrNull(httpClient)
        ?: throw IllegalStateException("YouTube OAuth env vars are not configured or token refresh failed")
}

private fun buildYoutubeOAuthUrl(clientId: String, redirectUri: String): String {
    fun encode(value: String): String = URLEncoder.encode(value, UTF_8.name())

    return "https://accounts.google.com/o/oauth2/v2/auth" +
        "?client_id=${encode(clientId)}" +
        "&redirect_uri=${encode(redirectUri)}" +
        "&response_type=code" +
        "&access_type=offline" +
        "&prompt=consent" +
        "&scope=${encode(YoutubeMembersScope)}"
}

private suspend fun exchangeYoutubeOAuthCode(httpClient: HttpClient, code: String, redirectUri: String): String? {
    val clientId = System.getenv("YOUTUBE_CLIENT_ID")
        ?: throw IllegalStateException("YOUTUBE_CLIENT_ID is not configured")
    val clientSecret = System.getenv("YOUTUBE_CLIENT_SECRET")
        ?: throw IllegalStateException("YOUTUBE_CLIENT_SECRET is not configured")

    val response = httpClient.post("https://oauth2.googleapis.com/token") {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("code", code)
                    append("redirect_uri", redirectUri)
                    append("grant_type", "authorization_code")
                }
            )
        )
    }

    if (!response.status.isSuccess()) {
        throw IllegalStateException("YouTube OAuth code exchange failed: ${response.bodyAsText()}")
    }

    return response.body<GoogleAuthCodeTokenResponse>().refreshToken
}

private suspend fun fetchYoutubeMembers(httpClient: HttpClient, accessToken: String): List<YoutubeMemberRecord> {
    val members = mutableListOf<YoutubeMemberRecord>()
    var pageToken: String? = null

    do {
        val response = httpClient.get("https://youtube.googleapis.com/youtube/v3/members") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("part", "snippet")
            parameter("mode", "all_current")
            parameter("maxResults", "1000")
            pageToken?.let { parameter("pageToken", it) }
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException("YouTube members.list failed: ${response.bodyAsText()}")
        }

        val page = response.body<YoutubeMembersResponse>()
        members += page.items.mapNotNull { item ->
            val displayName = item.snippet?.memberDetails?.displayName
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            YoutubeMemberRecord(
                youtubeChannelId = item.snippet.memberDetails.channelId,
                displayName = displayName,
                normalizedDisplayName = normalizeYoutubeName(displayName),
                profileImageUrl = item.snippet.memberDetails.profileImageUrl,
                membershipLevelName = item.snippet.membershipsDetails?.highestAccessibleLevelDisplayName,
                memberSince = parseIsoInstantMillis(item.snippet.membershipsDetails?.membershipsDuration?.memberSince),
            )
        }
        pageToken = page.nextPageToken
    } while (pageToken != null)

    return members
}

private fun Application.scheduleNightlyYoutubeSync(
    authRepository: AuthRepository,
    httpClient: HttpClient,
) {
    val envAllowsSync = System.getenv("YOUTUBE_SYNC_ENABLED") == "true" ||
        System.getenv("ENVIRONMENT") == "production" ||
        System.getenv("KTOR_ENV") == "production" ||
        System.getenv("RENDER") == "true"
    val hasYoutubeConfig = listOf(
        "YOUTUBE_CLIENT_ID",
        "YOUTUBE_CLIENT_SECRET",
        "YOUTUBE_REFRESH_TOKEN",
        "YOUTUBE_CHANNEL_ID",
    ).all { !System.getenv(it).isNullOrBlank() }

    if (!envAllowsSync || !hasYoutubeConfig) return

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    monitor.subscribe(ApplicationStopped) {
        scope.cancel()
    }

    scope.launch {
        delay(millisUntilNextNightlyRun())
        while (isActive) {
            try {
                val memberCount = syncYoutubeMembers(authRepository, httpClient)
                log.info("YouTube member sync completed: $memberCount members")
            } catch (t: Throwable) {
                log.warn("YouTube member sync failed: ${t::class.simpleName}: ${t.message}")
            }
            delay(24L * 60L * 60L * 1000L)
        }
    }
}

private fun millisUntilNextNightlyRun(): Long {
    val zone = ZoneId.systemDefault()
    val now = ZonedDateTime.now(zone)
    var next = now.withHour(3).withMinute(0).withSecond(0).withNano(0)
    if (!next.isAfter(now)) {
        next = next.plusDays(1)
    }
    return java.time.Duration.between(now, next).toMillis()
}

private fun parseIsoInstantMillis(value: String?): Long? =
    value?.let {
        try {
            Instant.parse(it).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
