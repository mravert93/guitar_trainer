package com.ravert.guitar_trainer.guitartrainer.parser

import com.ravert.guitar_trainer.guitartrainer.datamodels.ChordEvent
import com.ravert.guitar_trainer.guitartrainer.datamodels.LyricLine
import com.ravert.guitar_trainer.guitartrainer.datamodels.PlaybackSong
import com.ravert.guitar_trainer.guitartrainer.datamodels.StrumDirection
import com.ravert.guitar_trainer.guitartrainer.datamodels.StrumEvent

expect suspend fun fetchDocHtml(url: String): String

fun sanitizeForMonospace(raw: String): String {
    return buildString(raw.length) {
        for (ch in raw) {
            append(
                when (ch) {
                    '\r' -> '\n'        // normalize line endings
                    // turn all “special” spaces into normal spaces
                    '\u00A0', '\u2007', '\u202F' -> ' '
                    // collapse tabs to a fixed number of spaces (Google Docs LOVES tabs)
                    '\t' -> ' '        // or repeat(' ', 4) if you prefer
                    // normalize weird dash characters to ASCII '-'
                    '–', '—', '-', '−' -> '-'
                    else -> {
                        // keep basic printable ASCII, blank out anything exotic
                        if (ch.code in 32..126) ch else ' '
                    }
                }
            )
        }
    }
}


fun normalizeDocText(raw: String): String {
    return raw
        .replace("\r\n", "\n")    // Windows → Unix
        .replace('\r', '\n')
        .replace('\t', ' ')       // or "    " if you prefer a wider tab
        // if you suspect non-breaking spaces:
        .replace('\u00A0', ' ')
}

// Internal helper for chord positions
data class ChordToken(
    val chord: String,
    val startIndex: Int
)

fun parseChordLine(chordLine: String): List<ChordToken> {
    val tokens = mutableListOf<ChordToken>()
    val line = chordLine

    var i = 0
    while (i < line.length) {
        if (line[i].isWhitespace()) {
            i++
        } else {
            val start = i
            val sb = StringBuilder()
            while (i < line.length && !line[i].isWhitespace()) {
                sb.append(line[i])
                i++
            }
            tokens += ChordToken(chord = sb.toString(), startIndex = start)
        }
    }

    return tokens.sortedBy { it.startIndex }
}

fun currentChordAtIndex(tokens: List<ChordToken>, index: Int): String? {
    if (tokens.isEmpty()) return null
    var current: ChordToken? = null
    for (t in tokens) {
        if (t.startIndex <= index) {
            current = t
        } else break
    }
    return current?.chord
}

/**
 * Parse a single ASCII chord + strum block into a PlaybackSong.
 *
 * Assumptions:
 * - rawChordLine and rawStrumLine are monospaced-aligned.
 * - EVERY character (including spaces) is one eighth note.
 *   => index i = i * 0.5 beats
 */
fun parseAsciiPlaybackSong(
    title: String,
    rawChordLine: String,
    rawStrumLine: String,
    bpm: Int
): PlaybackSong {
    val maxLenTmp = maxOf(rawChordLine.length, rawStrumLine.length)
    val chordLineTmp = rawChordLine.padEnd(maxLenTmp, ' ').trimStart()
    val strumLineTmp = rawStrumLine.padEnd(maxLenTmp, ' ').trimStart()

    // Let's build these strings from the beginning
    val chordLineStrBuilder = StringBuilder()
    val strumLineStrBuilder = StringBuilder()
    for (index in 0 until chordLineTmp.length) {
        val chordChar = chordLineTmp[index]
        val strumChar = strumLineTmp[index]

        if (index < chordLineTmp.length - 1) {
            if (chordChar == ' ' && chordLineTmp[index + 1] != ' ') {
                // Don't add anything
                continue
            }
            // If the next strum is U and the one prior was D but we're at a space - add a new space
            // Ex. D UDU --> should be --> D  UDU to keep with eight note pattern
            if (index > 0 && strumChar == ' ' && strumLineTmp[index - 1] == 'D' && strumLineTmp[index + 1] == 'U') {
                chordLineStrBuilder.append(' ')
                strumLineStrBuilder.append(' ')
            }
        }

        chordLineStrBuilder.append(chordChar)
        strumLineStrBuilder.append(strumChar)
    }

    val chordLine = chordLineStrBuilder.toString()
    val strumLine = strumLineStrBuilder.toString()

    val chordTokens = parseChordLine(chordLine)
    val strumEvents = mutableListOf<StrumEvent>()

    val eighthNoteBeats = 0.5f
    val msPerBeat = 60_000f / bpm

    for (i in 0 until chordLine.length) {
        val c = strumLine[i]
        val dir = when (c.uppercaseChar()) {
            'D' -> StrumDirection.DOWN
            'U' -> StrumDirection.UP
            else -> null
        }
        if (dir != null) {
            val beats = i * eighthNoteBeats
            val timeMs = (beats * msPerBeat).toLong()
            val chord = currentChordAtIndex(chordTokens, i)

            strumEvents += StrumEvent(
                chord = chord,
                direction = dir,
                timeMs = timeMs,
                index = i
            )
        }
    }

    // Chord events: when each chord first appears
    val chordEvents = chordTokens.map { token ->
        val beats = token.startIndex * eighthNoteBeats
        val timeMs = (beats * msPerBeat).toLong()
        ChordEvent(label = token.chord, timeMs = timeMs)
    }

    val durationMs = if (strumEvents.isNotEmpty()) {
        val lastIndex = strumEvents.maxOf { it.index }
        val lastBeats = (lastIndex + 1) * eighthNoteBeats
        (lastBeats * msPerBeat).toLong()
    } else {
        // fallback: last chord start + 4 beats
        val lastChordIndex = chordTokens.maxOfOrNull { it.startIndex } ?: 0
        val beats = lastChordIndex * eighthNoteBeats + 4f
        (beats * msPerBeat).toLong()
    }

    return PlaybackSong(
        title = title,
        bpm = bpm,
        durationMs = durationMs,
        chords = chordEvents,
        strums = strumEvents,
        rawChordLine = rawChordLine,
        rawStrumLine = rawStrumLine
    )
}

fun offsetStrums(strums: List<StrumEvent>, offsetMs: Long): List<StrumEvent> =
    strums.map { e ->
        e.copy(timeMs = e.timeMs + offsetMs)
    }

fun offsetChords(chords: List<ChordEvent>, offsetMs: Long): List<ChordEvent> =
    chords.map { c ->
        c.copy(timeMs = c.timeMs + offsetMs)
    }

fun offsetLyrics(lyrics: List<LyricLine>, offsetMs: Long): List<LyricLine> =
    lyrics.map { l ->
        l.copy(startMs = l.startMs + offsetMs, endMs = l.endMs + offsetMs)
    }

fun parseSectionBlockToPlayback(
    title: String,
    fullText: String,
    bpm: Int
): PlaybackSong {
    val lines = fullText.lines()
    var i = 0

    val allChords = mutableListOf<ChordEvent>()
    val allStrums = mutableListOf<StrumEvent>()
    val allLyrics = mutableListOf<LyricLine>()

    var offsetMs = 0L

    while (i < lines.size) {
        // 1) Find the next chord line in the remaining lines
        var chordLineIndex = -1
        var chordLine: String? = null

        while (i < lines.size) {
            val line = lines[i]
            // chord line: no tabs, looks like chords
            if (line.isNotBlank() &&
                !line.contains('|') &&
                !line.contains('-') &&
                looksLikeChordLine(line)
            ) {
                chordLineIndex = i
                chordLine = line
                break
            }
            i++
        }

        if (chordLine == null) {
            // No more chord blocks in this section
            break
        }

        // 2) Find the strum line: next non-blank line after chord line
        var strumLine: String? = null
        var j = chordLineIndex + 1
        while (j < lines.size) {
            val line = lines[j]
            if (line.isNotBlank()) {
                strumLine = line
                break
            }
            j++
        }
        if (strumLine == null) {
            // weird, bail out of this block and continue from j
            i = j
            continue
        }

        // 3) Lyrics for this block: starting after strum line,
        // skip blank lines then take contiguous non-blank lines
        var lyricStart = j + 1
        while (lyricStart < lines.size && lines[lyricStart].isBlank()) {
            lyricStart++
        }

        val lyricsForBlock = mutableListOf<String>()
        var k = lyricStart
        while (k < lines.size) {
            // This would mean the next chord
            if (lines[k].contains("|-")) {
                break
            }
            if (lines[k].isNotBlank()) {
                lyricsForBlock += lines[k].trim()
            }
            k++
        }

        // Now:
        // chordLine at `chordLineIndex`
        // strumLine at `j`
        // lyricsForBlock = lines[lyricStart until k]

        // 4) Parse ONE loop of this block's chord/strum pattern
        val base = parseAsciiPlaybackSong(
            title = "$title (block starting at line $chordLineIndex)",
            rawChordLine = chordLine!!,
            rawStrumLine = strumLine,
            bpm = bpm
        )

        val baseDuration = base.durationMs

        if (lyricsForBlock.isEmpty()) {
            lyricsForBlock.add("[$title]")
        }

        // 5) Repeat this block once per lyric line
        lyricsForBlock.forEachIndexed { idx, text ->
            val repOffset = offsetMs + baseDuration * idx

            allChords += offsetChords(base.chords, repOffset)
            allStrums += offsetStrums(base.strums, repOffset)
            allLyrics += LyricLine(
                text = text,
                startMs = repOffset,
                endMs = repOffset + baseDuration
            )
        }
        offsetMs += baseDuration * lyricsForBlock.size

        // Advance i to continue searching for the next block
        i = k
    }

    val durationMs = offsetMs.coerceAtLeast(0L)

    return PlaybackSong(
        title = title,
        bpm = bpm,
        durationMs = durationMs,
        chords = allChords,
        strums = allStrums,
        lyrics = allLyrics
    )
}


fun parseSongWithSections(
    title: String,
    fullText: String,
    bpmBySection: Map<String, Int>,
    defaultBpm: Int = 80
): PlaybackSong {
    val blocks = splitIntoSectionBlocks(fullText)

    val sectionSongs = blocks.map { block ->
        val bpm = bpmBySection[block.name] ?: defaultBpm
        parseSectionBlockToPlayback(
            title = block.name,
            fullText = block.body,
            bpm = bpm
        )
    }

    val allChords = mutableListOf<ChordEvent>()
    val allStrums = mutableListOf<StrumEvent>()
    val allLyrics = mutableListOf<LyricLine>()
    var offsetMs = 0L

    sectionSongs.forEach { section ->
        allChords += offsetChords(section.chords, offsetMs)
        allStrums += offsetStrums(section.strums, offsetMs)
        allLyrics += offsetLyrics(section.lyrics, offsetMs)
        offsetMs += section.durationMs
    }

    val bpmGlobal = sectionSongs.firstOrNull()?.bpm ?: defaultBpm

    return PlaybackSong(
        title = title,
        bpm = bpmGlobal,
        durationMs = offsetMs,
        chords = allChords,
        strums = allStrums,
        lyrics = allLyrics
    )
}


private fun looksLikeChordLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    // Must NOT contain tab / staff markers
    if (trimmed.contains('|') || trimmed.contains('-')) return false

    val tokens = trimmed.split(Regex("\\s+"))
    if (tokens.isEmpty()) return false

    // Basic chord token pattern: C, Am, G, Fmaj7, Dsus2, etc.
    val chordRegex = Regex("^[A-G][#b]?(m|maj7|sus2|sus4|add9|dim|aug)?\\d*$")

    // If at least half of the tokens look like chords, treat it as chord line
    val chordishCount = tokens.count { chordRegex.matches(it) }
    return chordishCount >= tokens.size / 2
}

