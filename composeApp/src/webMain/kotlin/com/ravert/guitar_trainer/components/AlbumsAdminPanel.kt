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
import com.ravert.guitar_trainer.guitartrainer.datamodels.Album
import com.ravert.guitar_trainer.guitartrainer.datamodels.Artist
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun AlbumsAdminPanel(
    albums: List<Album>,
    artists: List<Artist>,
    onCreate: (Album) -> Unit,
    onSave: (uuid: String?, artistId: String, name: String, imageUrl: String?) -> Unit,
    onDelete: (Album) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }

    val artistNameById = remember(artists) { artists.associate { it.uuid to it.name } }

    val filteredAlbums = remember(albums, query, artistNameById) {
        val q = query.trim()
        if (q.isBlank()) return@remember albums

        albums.filter { album ->
            val artistName = artistNameById[album.artistUuid].orEmpty()
            album.name.contains(q, ignoreCase = true) ||
                    album.uuid.contains(q, ignoreCase = true) ||
                    artistName.contains(q, ignoreCase = true) ||
                    (album.image ?: "").contains(q, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") {
            SectionHeader(
                title = "Albums",
                onAdd = {
                    val defaultArtist = artists.firstOrNull()
                    onCreate(
                        Album(
                            uuid = Uuid.random().toString(),
                            artistUuid = defaultArtist?.uuid ?: "",
                            name = "New Album",
                            image = ""
                        )
                    )
                }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = query,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search albums") },
                placeholder = { Text("Album name, artist, uuid, image URL…") },
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
            items = filteredAlbums,
            key = { it.uuid }
        ) { album ->
            AlbumEditRow(
                album = album,
                artists = artists,
                onSave = onSave,
                onDelete = { onDelete(album) }
            )
        }
    }
}

@Composable
private fun AlbumEditRow(
    album: Album,
    artists: List<Artist>,
    onSave: (uuid: String?, artistId: String, name: String, imageUrl: String?) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable(album.uuid) { mutableStateOf(false) }

    var name by remember(album.uuid) { mutableStateOf(album.name) }
    var image by remember(album.uuid) { mutableStateOf(album.image ?: "") }
    var artistUuid by remember(album.uuid) { mutableStateOf(album.artistUuid) }

    // Keep collapsed rows in sync if backend refreshes album values
    LaunchedEffect(album.uuid, album.name, album.image, album.artistUuid) {
        if (!expanded) {
            name = album.name
            image = album.image ?: ""
            artistUuid = album.artistUuid
        }
    }

    val selectedArtistName = remember(artistUuid, artists) {
        artists.firstOrNull { it.uuid == artistUuid }?.name ?: "Unknown artist"
    }

    val isDirty = remember(expanded, name, image, artistUuid, album) {
        if (!expanded) false else {
            name != album.name ||
                    (image != (album.image ?: "")) ||
                    artistUuid != album.artistUuid
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
                        text = album.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "$selectedArtistName • ${album.image?.takeIf { it.isNotBlank() } ?: "No image"}",
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

                    ArtistDropdown(
                        artists = artists,
                        selectedArtistUuid = artistUuid,
                        onSelected = { artistUuid = it }
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Album Name") },
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
                                onSave(album.uuid, artistUuid, name, image)
                                expanded = false // optional: auto-collapse after save
                            }
                        ) { Text("Save") }

                        OutlinedButton(onClick = {
                            // Reset edits to current album
                            name = album.name
                            image = album.image ?: ""
                            artistUuid = album.artistUuid
                        }) { Text("Reset") }

                        OutlinedButton(onClick = onDelete) { Text("Delete") }
                    }
                }
            }
        }
    }
}

