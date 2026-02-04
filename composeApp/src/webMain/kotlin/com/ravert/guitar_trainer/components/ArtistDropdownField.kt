package com.ravert.guitar_trainer.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.ravert.guitar_trainer.guitartrainer.datamodels.Artist

@Composable
fun ArtistDropdownField(
    artists: List<Artist>,
    selectedArtistId: String?,
    onArtistSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedName = when {
        artists.isEmpty() -> "No artists yet"
        selectedArtistId == null -> "Select artist"
        else -> artists.firstOrNull { it.uuid == selectedArtistId }?.name ?: "Select artist"
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
                .clickable(enabled = artists.isNotEmpty()) {
                    expanded = !expanded
                    println("ArtistSelector clicked, expanded=$expanded")
                }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(selectedName, modifier = Modifier.weight(1f))
            Text("v")
        }

        if (expanded && artists.isNotEmpty()) {
            // THIS MUST RENDER if expanded == true
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column {
                    artists.forEach { artist ->
                        Text(
                            text = artist.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onArtistSelected(artist.uuid)
                                    expanded = false
                                    println("Selected artist: ${artist.name}")
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
