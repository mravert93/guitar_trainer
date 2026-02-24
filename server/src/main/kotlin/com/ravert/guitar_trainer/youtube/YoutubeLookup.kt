package com.ravert.guitar_trainer.youtube

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

suspend fun fetchLatestNonShortNonLiveUnder50Min(
    http: HttpClient,
    apiKey: String,
    channelId: String
): String? {
    // 1) get latest items (IDs)
    val search = http.get("https://www.googleapis.com/youtube/v3/search") {
        parameter("part", "id")
        parameter("channelId", channelId)
        parameter("order", "date")
        parameter("maxResults", 25)
        parameter("type", "video")
        parameter("key", apiKey)
    }.body<SearchListResponse>()

    val ids = search.items.mapNotNull { it.id?.videoId }.distinct()
    if (ids.isEmpty()) return null

    // 2) get video details (duration + liveBroadcastContent)
    val details = http.get("https://www.googleapis.com/youtube/v3/videos") {
        parameter("part", "contentDetails,snippet")
        parameter("id", ids.joinToString(","))
        parameter("key", apiKey)
    }.body<VideosListResponse>()

    // We want the newest match, so iterate in the same order as search IDs
    val byId = details.items.associateBy { it.id }

    for (id in ids) {
        val v = byId[id] ?: continue

        // Exclude live/upcoming
        val live = v.snippet?.liveBroadcastContent
        if (live == "live" || live == "upcoming") continue

        val seconds = parseIso8601DurationToSeconds(v.contentDetails?.duration ?: continue)

        // Exclude shorts-ish (<= 60s) and long videos (>= 50 min)
        if (seconds <= 60) continue
        if (seconds >= 50 * 60) continue

        return id
    }

    return null
}

@Serializable
private data class SearchListResponse(val items: List<SearchItem> = emptyList())

@Serializable
private data class SearchItem(val id: SearchId? = null)

@Serializable
private data class SearchId(val videoId: String? = null)

@Serializable
private data class VideosListResponse(val items: List<VideoItem> = emptyList())

@Serializable
private data class VideoItem(
    val id: String,
    val snippet: VideoSnippet? = null,
    val contentDetails: ContentDetails? = null,
)

@Serializable
private data class VideoSnippet(
    val liveBroadcastContent: String? = null, // "none" | "live" | "upcoming"
)

@Serializable
private data class ContentDetails(
    val duration: String? = null, // ISO 8601 e.g. "PT4M13S"
)

/**
 * Handles formats like: PT59S, PT4M13S, PT1H2M3S, PT52M
 */
private fun parseIso8601DurationToSeconds(iso: String): Int {
    val r = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""")
    val m = r.matchEntire(iso) ?: return 0
    val h = m.groupValues[1].takeIf { it.isNotBlank() }?.toInt() ?: 0
    val min = m.groupValues[2].takeIf { it.isNotBlank() }?.toInt() ?: 0
    val s = m.groupValues[3].takeIf { it.isNotBlank() }?.toInt() ?: 0
    return h * 3600 + min * 60 + s
}