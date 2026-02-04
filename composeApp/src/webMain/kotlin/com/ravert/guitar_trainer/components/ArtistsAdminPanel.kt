package com.ravert.guitar_trainer.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ravert.guitar_trainer.guitartrainer.datamodels.Artist
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Composable
fun ArtistsAdminPanel(
    artists: List<Artist>,
    onCreate: (name: String, imageUrl: String?) -> Unit,
    onSave: (uuid: String, name: String, imageUrl: String?) -> Unit,
    onDelete: (Artist) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }

    val filteredArtists = remember(artists, query) {
        val q = query.trim()
        if (q.isBlank()) return@remember artists

        artists.filter { artist ->
            artist.name.contains(q, ignoreCase = true) ||
                    artist.uuid.contains(q, ignoreCase = true) ||
                    (artist.image ?: "").contains(q, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") {
            SectionHeader(
                title = "Artists",
                onAdd = { onCreate("", "") }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = query,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search artists") },
                placeholder = { Text("Name, uuid, image URL…") },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                }
            )

            Spacer(Modifier.height(8.dp))
        }

        items(
            items = filteredArtists,
            key = { it.uuid }
        ) { artist ->
            ArtistEditRow(
                artist = artist,
                onSave = onSave,
                onDelete = { onDelete(artist) }
            )
        }
    }
}

@Composable
private fun ArtistEditRow(
    artist: Artist,
    onSave: (uuid: String, name: String, imageUrl: String?) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable(artist.uuid) { mutableStateOf(false) }

    var name by remember(artist.uuid) { mutableStateOf(artist.name) }
    var image by remember(artist.uuid) { mutableStateOf(artist.image ?: "") }

    // Keep collapsed rows in sync if backend refreshes artist values
    LaunchedEffect(artist.uuid, artist.name, artist.image) {
        if (!expanded) {
            name = artist.name
            image = artist.image ?: ""
        }
    }

    val isDirty = remember(expanded, name, image, artist) {
        if (!expanded) false else {
            name != artist.name ||
                    image != (artist.image ?: "")
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // --- Collapsed header (always visible) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = artist.name.ifBlank { "(Unnamed artist)" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = artist.image?.takeIf { it.isNotBlank() } ?: "No image",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isDirty) {
                    Text(
                        text = "Unsaved",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Collapse" else "Edit")
                }
            }

            // --- Expanded editor ---
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Divider()

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = image,
                        onValueChange = { image = it },
                        label = { Text("Image URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = isDirty,
                            onClick = {
                                onSave(artist.uuid, name, image)
                                expanded = false // optional: auto-collapse after save
                            }
                        ) { Text("Save") }

                        OutlinedButton(onClick = {
                            // Reset edits to current artist
                            name = artist.name
                            image = artist.image ?: ""
                        }) { Text("Reset") }

                        OutlinedButton(onClick = onDelete) { Text("Delete") }
                    }
                }
            }
        }
    }
}
