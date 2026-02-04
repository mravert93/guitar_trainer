package com.ravert.guitar_trainer.import

import kotlinx.serialization.Serializable

@Serializable
data class AlbumLookupResults(
    val results: List<AppleMusicResult>
)

@Serializable
data class AppleMusicResult(
    val collectionName: String = "Unknown Album",
    val artworkUrl100: String = "",
)