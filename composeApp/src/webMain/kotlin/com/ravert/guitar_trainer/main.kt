package com.ravert.guitar_trainer

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.ravert.guitar_trainer.guitartrainer.datamodels.Song
import com.ravert.guitar_trainer.guitartrainer.managers.LibraryProvider
import com.ravert.guitar_trainer.guitartrainer.parser.fetchDocHtml
import com.ravert.guitar_trainer.guitartrainer.parser.sanitizeForMonospace

@OptIn(ExperimentalComposeUiApi::class)
fun legacyMain() {
    ComposeViewport {
        var docBody by remember { mutableStateOf<String?>(null) }
        var song by remember { mutableStateOf<Song?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            try {
                isLoading = true
                error = null

                val s = LibraryProvider().getSongById("")
                song = s

                if (s != null) {
                    val txt = fetchDocHtml(s.docUrl + "/export?format=txt")
                    docBody = sanitizeForMonospace(txt)

                    docBody!!.lines().take(30).forEachIndexed { i, line ->
                        println("line $i length=${line.length} : '$line'")
                    }
                }
            } catch (t: Throwable) {
                error = t.message ?: t.toString()
            } finally {
                isLoading = false
            }
        }

        when {
            error != null -> {
                Text("Error: $error")
            }

            isLoading || docBody == null || song == null -> {
                Text("Loading...")
            }

            else -> {
                AutoScrollDocScreen(
                    docText = docBody!!,
                    songDurationMs = song!!.songLength * 750L,
                    initialBpm = song!!.bpm,
                )
            }
        }
    }
}
