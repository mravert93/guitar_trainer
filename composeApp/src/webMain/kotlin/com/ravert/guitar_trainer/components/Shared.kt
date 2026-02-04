package com.ravert.guitar_trainer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.ravert.guitar_trainer.guitartrainer.datamodels.Album
import com.ravert.guitar_trainer.guitartrainer.datamodels.Artist

@Composable
fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Button(onClick = onAdd) { Text("Add") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDropdown(
    artists: List<Artist>,
    selectedArtistUuid: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedName =
        artists.firstOrNull { it.uuid == selectedArtistUuid }?.name ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Artist") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            artists
                .sortedBy { it.name.lowercase() }
                .forEach { artist ->
                    DropdownMenuItem(
                        text = { Text(artist.name) },
                        onClick = {
                            onSelected(artist.uuid)
                            expanded = false
                        }
                    )
                }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDropdown(
    albums: List<Album>,
    selectedAlbumUuid: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedName =
        albums.firstOrNull { it.uuid == selectedAlbumUuid }?.name ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Album (optional)") },
            placeholder = { Text("No album") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("No album") },
                onClick = {
                    onSelected("")
                    expanded = false
                }
            )

            albums.forEach { album ->
                DropdownMenuItem(
                    text = { Text(album.name) },
                    onClick = {
                        onSelected(album.uuid)
                        expanded = false
                    }
                )
            }
        }
    }
}
