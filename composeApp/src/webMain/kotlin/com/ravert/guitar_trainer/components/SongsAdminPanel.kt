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
import androidx.compose.material3.TextFieldDefaults
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
import com.ravert.guitar_trainer.guitartrainer.datamodels.Song
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


@OptIn(ExperimentalUuidApi::class)
@Composable
fun SongsAdminPanel(
    songs: List<Song>,
    artists: List<Artist>,
    albums: List<Album>,
    onCreate: (Song) -> Unit,
    onSave: (uuid: String?, artistId: String, albumId: String, name: String, lengthSeconds: Int, bpm: Int, docUrl: String) -> Unit,
    onDelete: (Song) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }

    // Optional: include artist/album names in search too (cheap + better UX)
    val artistNameById = remember(artists) { artists.associate { it.uuid to it.name } }
    val albumNameById = remember(albums) { albums.associate { it.uuid to it.name } }

    val filteredSongs = remember(songs, query, artistNameById, albumNameById) {
        val q = query.trim().lowercase()
        if (q.isBlank()) return@remember songs

        songs.filter { song ->
            val artistName = artistNameById[song.artistUuid].orEmpty()
            val albumName = albumNameById[song.albumUuid ?: ""].orEmpty()

            song.name.contains(q, ignoreCase = true) ||
                    song.uuid.contains(q, ignoreCase = true) ||
                    artistName.contains(q, ignoreCase = true) ||
                    albumName.contains(q, ignoreCase = true) ||
                    song.docUrl.contains(q, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") {
            SectionHeader(
                title = "Songs",
                onAdd = {
                    val defaultArtist = artists.firstOrNull()
                    onCreate(
                        Song(
                            uuid = Uuid.random().toString(),
                            artistUuid = defaultArtist?.uuid ?: "",
                            albumUuid = "null",
                            name = "New Song",
                            songLength = 180,
                            bpm = 100,
                            docUrl = ""
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
                label = { Text("Search songs") },
                placeholder = { Text("Name, artist, album, uuid, doc URL…") },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                }
            )

            Spacer(Modifier.height(8.dp))
        }

        items(
            items = filteredSongs,
            key = { it.uuid }
        ) { song ->
            SongEditRow(
                song = song,
                artists = artists,
                albums = albums,
                onSave = onSave,
                onDelete = { onDelete(song) }
            )
        }
    }
}


@Composable
private fun SongEditRow(
    song: Song,
    artists: List<Artist>,
    albums: List<Album>,
    onSave: (uuid: String?,
             artistId: String,
             albumId: String,
             name: String,
             lengthSeconds: Int,
             bpm: Int,
             docUrl: String) -> Unit,
    onDelete: () -> Unit
) {
    // Expand/collapse state (per song row)
    var expanded by rememberSaveable(song.uuid) { mutableStateOf(false) }

    // Editable fields (only really used in expanded mode)
    var name by remember(song.uuid) { mutableStateOf(song.name) }
    var docUrl by remember(song.uuid) { mutableStateOf(song.docUrl) }
    var songLength by remember(song.uuid) { mutableStateOf(song.songLength.toString()) }
    var bpmText by remember(song.uuid) { mutableStateOf(song.bpm.toString()) }

    var artistUuid by remember(song.uuid) { mutableStateOf(song.artistUuid) }
    var albumUuid by remember(song.uuid) { mutableStateOf(song.albumUuid ?: "") }

    // If the backing song changes (e.g. refreshed from backend after save), keep UI in sync
    LaunchedEffect(song.uuid, song.name, song.docUrl, song.songLength, song.bpm, song.artistUuid, song.albumUuid) {
        if (!expanded) {
            name = song.name
            docUrl = song.docUrl
            songLength = song.songLength.toString()
            bpmText = song.bpm.toString()
            artistUuid = song.artistUuid
            albumUuid = song.albumUuid ?: ""
        }
    }

    val albumsByArtist = remember(albums) {
        albums
            .groupBy { it.artistUuid }
            .mapValues { (_, list) -> list.sortedBy { it.name.lowercase() } }
    }
    val albumsForArtist = albumsByArtist[artistUuid].orEmpty()

    val selectedArtistName = remember(artistUuid, artists) {
        artists.firstOrNull { it.uuid == artistUuid }?.name ?: "Unknown artist"
    }
    val selectedAlbumName = remember(albumUuid, albumsForArtist, albums) {
        // albumUuid might not be in albumsForArtist if artist changes; try full list fallback
        (albumsForArtist.firstOrNull { it.uuid == albumUuid }
            ?: albums.firstOrNull { it.uuid == albumUuid })?.name ?: "No album"
    }

    val isDirty = remember(expanded, name, docUrl, songLength, bpmText, artistUuid, albumUuid, song) {
        if (!expanded) false else {
            name != song.name ||
                    docUrl != song.docUrl ||
                    (songLength.toIntOrNull() ?: song.songLength) != song.songLength ||
                    (bpmText.toIntOrNull() ?: song.bpm) != song.bpm ||
                    artistUuid != song.artistUuid ||
                    albumUuid != (song.albumUuid ?: "")
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // --- Collapsed header row (always shown) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "$selectedArtistName • $selectedAlbumName • ${song.songLength}s • ${song.bpm} bpm",
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

            // --- Expanded edit form ---
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    Divider()

                    ArtistDropdown(
                        artists = artists,
                        selectedArtistUuid = artistUuid,
                        onSelected = {
                            artistUuid = it
                            // reset album if it doesn't match artist anymore
                            if (albumsByArtist[it].orEmpty().none { a -> a.uuid == albumUuid }) {
                                albumUuid = ""
                            }
                        }
                    )

                    AlbumDropdown(
                        albums = albumsForArtist,
                        selectedAlbumUuid = albumUuid,
                        onSelected = { albumUuid = it }
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Song Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = songLength,
                        onValueChange = { songLength = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Song Length (seconds)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = bpmText,
                        onValueChange = { bpmText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("BPM") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = docUrl,
                        onValueChange = { docUrl = it },
                        label = { Text("Doc URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = isDirty,
                            onClick = {
                                val len = songLength.toIntOrNull() ?: song.songLength
                                val bpm = bpmText.toIntOrNull() ?: song.bpm

                                onSave(
                                    song.uuid,
                                    artistUuid,
                                    albumUuid,
                                    name,
                                    len,
                                    bpm,
                                    docUrl
                                )

                                // Optional: auto-collapse after save
                                expanded = false
                            }
                        ) { Text("Save") }

                        OutlinedButton(onClick = {
                            // reset edits to current song values
                            name = song.name
                            docUrl = song.docUrl
                            songLength = song.songLength.toString()
                            bpmText = song.bpm.toString()
                            artistUuid = song.artistUuid
                            albumUuid = song.albumUuid ?: ""
                        }) { Text("Reset") }

                        OutlinedButton(onClick = onDelete) { Text("Delete") }
                    }
                }
            }
        }
    }
}

