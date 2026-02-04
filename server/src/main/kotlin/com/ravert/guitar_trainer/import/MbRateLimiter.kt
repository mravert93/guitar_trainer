package com.ravert.guitar_trainer.import

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay

class MbRateLimiter(private val minDelayMs: Long = 2100L) {
    private var last = 0L
    suspend fun waitTurn() {
        val now = System.currentTimeMillis()
        val wait = (last + minDelayMs) - now
        if (wait > 0) delay(wait)
        last = System.currentTimeMillis()
    }
}

/**
 * NOTE:
 * - MusicBrainz rate limit ~ 1 req/sec. :contentReference[oaicite:3]{index=3}
 * - Use a descriptive User-Agent (they ask for this in their docs/practice).
 */
suspend fun enrichWithMusicBrainzAndCAA(
    http: HttpClient,
    limiter: MbRateLimiter,
    song: String,
    artist: String
): TrackEnrichment {
    val userAgent = "GuitarTrainer/0.1 (contact: you@example.com)"

    // 1) Search recording
    limiter.waitTurn()
    val searchQuery = """recording:"$song" AND artist:"$artist""""

    val search: MbRecordingSearchResponse =
        http.get("https://musicbrainz.org/ws/2/recording") {
            parameter("query", searchQuery)
            parameter("limit", "1")
            parameter("fmt", "json")
            header(HttpHeaders.UserAgent, userAgent)
            accept(ContentType.Application.Json)
        }.body()

    val hit = search.recordings.firstOrNull()
        ?: return TrackEnrichment(song, artist, null, null, null, null)

    // 2) Lookup recording -> releases (inc=releases)
    limiter.waitTurn()
    val lookup: MbRecordingLookupResponse =
        http.get("https://musicbrainz.org/ws/2/recording/${hit.id}") {
            parameter("inc", "releases")
            parameter("fmt", "json")
            header(HttpHeaders.UserAgent, userAgent)
            accept(ContentType.Application.Json)
        }.body()

    // 3) Pick a "best" release (simple heuristic)
    val bestRelease = lookup.releases
        .sortedWith(
            compareBy<MbRelease>(
                { it.status != "Official" },          // prefer Official
                { it.date.isNullOrBlank() },          // prefer dated
                { it.title.length }                   // arbitrary tie-breaker
            )
        )
        .firstOrNull()

    if (bestRelease == null) {
        return TrackEnrichment(song, artist, lookup.id, null, null, null)
    }

    // 4) Cover Art Archive by release MBID
    // CAA endpoint: /release/{mbid} returns JSON listing images. :contentReference[oaicite:4]{index=4}
    val coverArtUrl: String? = try {
        val caa: CaaReleaseResponse =
            http.get("https://coverartarchive.org/release/${bestRelease.id}") {
                // CAA is public; still send a UA
                header(HttpHeaders.UserAgent, userAgent)
                accept(ContentType.Application.Json)
            }.body()

        val front = caa.images.firstOrNull { it.front == true } ?: caa.images.firstOrNull()
        // pick a thumbnail that’s friendlier than full-size
        front?.thumbnails?.t500
            ?: front?.thumbnails?.large
            ?: front?.image
    } catch (_: Throwable) {
        null
    }

    return TrackEnrichment(
        song = song,
        artist = artist,
        recordingMbid = lookup.id,
        releaseMbid = bestRelease.id,
        releaseTitle = bestRelease.title,
        coverArtUrl = coverArtUrl
    )
}
