package com.ravert.guitar_trainer.guitartrainer.datamodels

// Core events stay the same
enum class StrumDirection { DOWN, UP }

data class StrumEvent(
    val chord: String?,          // chord active at this moment (if any)
    val direction: StrumDirection,
    val timeMs: Long,
    val index: Int               // column index in ASCII line
)

data class ChordEvent(
    val label: String,
    val timeMs: Long
)

data class LyricLine(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

// ONE thing you pass to the UI
data class PlaybackSong(
    val title: String,
    val bpm: Int,
    val durationMs: Long,
    val chords: List<ChordEvent>,
    val strums: List<StrumEvent>,
    val lyrics: List<LyricLine> = emptyList(),
    // optional, but nice to have if you ever want to re-render/edit
    val rawChordLine: String? = null,
    val rawStrumLine: String? = null
)

