package com.ravert.guitar_trainer.import

import kotlinx.serialization.Serializable

@Serializable
data class SongTabDetail(
    val songId: String?,
    val artistName: String,
    val songName: String,
    val tuning: String,
    val capo: String,
    val chords: String,
    val technique: String
)
