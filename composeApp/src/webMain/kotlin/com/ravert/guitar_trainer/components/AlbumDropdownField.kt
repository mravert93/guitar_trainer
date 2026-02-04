package com.ravert.guitar_trainer.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ravert.guitar_trainer.guitartrainer.datamodels.Album

@Composable
fun AlbumDropdownField(
    albums: List<Album>,
    selectedAlbumId: String?,
    onAlbumSelected: (String?) -> Unit, // allow null = "no album"
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedName = when {
        albums.isEmpty() && selectedAlbumId == null -> "No album (single)"
        selectedAlbumId == null -> "No album (single)"
        else -> albums.firstOrNull { it.uuid == selectedAlbumId }?.name ?: "Select album"
    }

    Column(modifier = modifier) {
        // This row is your "field"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color.LightGray,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(vertical = 4.dp)
                .clickable(enabled = albums.isNotEmpty()) {
                    expanded = !expanded
                    println("AlbumsSelector clicked, expanded=$expanded")
                }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(selectedName, modifier = Modifier.weight(1f))
            Text("v")
        }

        if (expanded && albums.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column {
                    albums.forEach { album ->
                        Text(
                            text = album.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAlbumSelected(album.uuid)
                                    expanded = false
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
