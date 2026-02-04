package com.ravert.guitar_trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ravert.guitar_trainer.guitartrainer.datamodels.LyricLine

@Composable
fun LyricsPanel(
    lyrics: List<LyricLine>,
    progressMs: Long,
    modifier: Modifier = Modifier,
    centerCurrentLine: Boolean = true
) {
    if (lyrics.isEmpty()) return

    // Figure out which line is "active" right now
    val currentIndex = remember(lyrics, progressMs) {
        lyrics.indexOfLast { progressMs in it.startMs..it.endMs }
            .coerceAtLeast(0)
    }

    val listState = rememberLazyListState()

    // Auto-scroll when currentIndex changes
    LaunchedEffect(currentIndex) {
        if (currentIndex in lyrics.indices) {
            val targetIndex = if (centerCurrentLine) {
                (currentIndex - 2).coerceAtLeast(0) // try to keep it above center
            } else {
                currentIndex
            }
            listState.animateScrollToItem(targetIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)            // fixed height; tweak to taste
            .background(Color(0xFF111111))
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = "Lyrics",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .padding(bottom = 4.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isCurrent = index == currentIndex

                Text(
                    text = line.text,
                    color = if (isCurrent) Color.White else Color.LightGray.copy(alpha = 0.7f),
                    style = if (isCurrent)
                        MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    else
                        MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}

