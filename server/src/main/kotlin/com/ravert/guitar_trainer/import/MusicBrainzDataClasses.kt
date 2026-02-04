package com.ravert.guitar_trainer.import

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MbRecordingSearchResponse(
    val recordings: List<MbRecordingSearchHit> = emptyList()
)

@Serializable
data class MbRecordingSearchHit(
    val id: String,
    val title: String,
    val score: Int? = null,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit>? = null
)

@Serializable
data class MbArtistCredit(
    val name: String
)

// Recording lookup includes releases when you use inc=releases
@Serializable
data class MbRecordingLookupResponse(
    val id: String,
    val title: String,
    val releases: List<MbRelease> = emptyList()
)

@Serializable
data class MbRelease(
    val id: String,
    val title: String,
    val status: String? = null,               // e.g. "Official"
    val date: String? = null                  // e.g. "2025-04-18"
)

@Serializable
data class CaaReleaseResponse(
    val images: List<CaaImage> = emptyList()
)

@Serializable
data class CaaImage(
    val image: String,                        // full-size image URL
    val thumbnails: CaaThumbnails? = null,
    val front: Boolean? = null
)

@Serializable
data class CaaThumbnails(
    val small: String? = null,
    val large: String? = null,
    @SerialName("250") val t250: String? = null,
    @SerialName("500") val t500: String? = null,
    @SerialName("1200") val t1200: String? = null
)

data class TrackEnrichment(
    val song: String,
    val artist: String,
    val recordingMbid: String?,
    val releaseMbid: String?,
    val releaseTitle: String?,
    val coverArtUrl: String?,     // choose a thumbnail size you like
    val source: String = "MusicBrainz + Cover Art Archive"
)
