package com.ravert.guitar_trainer.import

import kotlinx.serialization.Serializable

@Serializable
data class DeezerSearchResponse(
    val data: List<DeezerTrack> = emptyList(),
    val total: Int = 0
)

@Serializable
data class DeezerTrack(
    val id: Long,
    val title: String,
    val duration: Int, // seconds
    val artist: DeezerArtist,
    val album: DeezerAlbum
)

@Serializable
data class DeezerArtist(
    val id: Long,
    val name: String,
    val picture: String? = null,
    val picture_small: String? = null,
    val picture_medium: String? = null,
    val picture_big: String? = null,
    val picture_xl: String? = null
)

@Serializable
data class DeezerAlbum(
    val id: Long,
    val title: String,
    val cover: String? = null,
    val cover_small: String? = null,
    val cover_medium: String? = null,
    val cover_big: String? = null,
    val cover_xl: String? = null
)
