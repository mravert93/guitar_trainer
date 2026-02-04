package com.ravert.guitar_trainer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ravert.guitar_trainer.guitartrainer.datamodels.Song
import com.ravert.guitar_trainer.pages.RemoteImageCircle

@Composable
fun VerticalImageBlock(
    name: String,
    imageUrl: String,
    songs: List<Song>,
    squareShape: Boolean,
    imageSize: Dp,
    textColor: Color,
    onSongClick: (String) -> Unit,
    onMoreClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(200.dp)
    ) {
        RemoteImageCircle(
            name = name,
            imageUrl = imageUrl,
            squareShape = squareShape,
            textColor = textColor,
            modifier = Modifier
                .size(imageSize)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = name,
            color = textColor,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            val top = songs.take(4)

            Spacer(Modifier.height(8.dp))

            top.forEach { song ->
                Text(
                    text = song.name,
                    textDecoration = TextDecoration.Underline,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .clickable {
                            onSongClick(song.uuid)
                        }
                )
            }

            if (songs.size > 4) {
                Text(
                    text = "${songs.size - 4} more songs",
                    textDecoration = TextDecoration.Underline,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable {
                        onMoreClick()
                    }
                )
            }
        }
    }
}