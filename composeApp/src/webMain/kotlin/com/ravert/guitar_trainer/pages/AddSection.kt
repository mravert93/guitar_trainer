package com.ravert.guitar_trainer.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ravert.guitar_trainer.components.AlbumDropdownField
import com.ravert.guitar_trainer.components.ArtistDropdownField
import com.ravert.guitar_trainer.guitartrainer.datamodels.Album
import com.ravert.guitar_trainer.guitartrainer.datamodels.Artist

@Composable
fun AddArtistSection(
    onAddArtist: (name: String, imageUrl: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Add Artist",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    saved = false
                },
                label = { Text("Artist Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = imageUrl,
                onValueChange = {
                    imageUrl = it
                    saved = false
                },
                label = { Text("Image URL (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            if (saved) {
                Text("Artist saved ✅", color = MaterialTheme.colorScheme.primary)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = {
                    if (name.isBlank()) {
                        error = "Artist name is required"
                        saved = false
                    } else {
                        error = null
                        onAddArtist(name, imageUrl)
                        name = ""
                        imageUrl = ""
                        saved = true
                    }
                }) {
                    Text("Save Artist")
                }
            }
        }
    }
}

@Composable
fun AddAlbumSection(
    artists: List<Artist>,
    onAddAlbum: (artistId: String, name: String, imageUrl: String) -> Unit
) {
    var selectedArtistId by remember { mutableStateOf<String?>(null) }

    var albumName by remember { mutableStateOf("") }
    var albumImageUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    // Keep selected artist in sync if list changes (e.g. you add the first artist)
    LaunchedEffect(artists) {
        if (artists.isNotEmpty() && selectedArtistId == null) {
            selectedArtistId = artists.first().uuid
        } else if (artists.none { it.uuid == selectedArtistId }) {
            selectedArtistId = artists.firstOrNull()?.uuid
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Add Album", style = MaterialTheme.typography.titleMedium)

            ArtistDropdownField(
                artists = artists,
                selectedArtistId = selectedArtistId,
                onArtistSelected = {
                    selectedArtistId = it
                    saved = false
                }
            )

            OutlinedTextField(
                value = albumName,
                onValueChange = {
                    albumName = it
                    saved = false
                },
                label = { Text("Album Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = albumImageUrl,
                onValueChange = {
                    albumImageUrl = it
                    saved = false
                },
                label = { Text("Album Image URL (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            if (saved) {
                Text("Album saved ✅", color = MaterialTheme.colorScheme.primary)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = {
                    if (selectedArtistId == null) {
                        error = "Please select an artist"
                        saved = false
                    } else if (albumName.isBlank()) {
                        error = "Album name is required"
                        saved = false
                    } else {
                        error = null
                        onAddAlbum(
                            selectedArtistId!!,
                            albumName,
                            albumImageUrl
                        )
                        albumName = ""
                        albumImageUrl = ""
                        saved = true
                    }
                }) {
                    Text("Save Album")
                }
            }
        }
    }
}

@Composable
fun AddSongSection(
    artists: List<Artist>,
    albums: List<Album>,
    onAddSong: (
        artistId: String,
        albumId: String,
        name: String,
        lengthSeconds: Int,
        bpm: Int,
        docUrl: String
    ) -> Unit
) {
    var selectedArtistId by remember { mutableStateOf<String?>(null) }

    val albumsForArtist = remember(artists, albums, selectedArtistId) {
        albums.filter { it.artistUuid == selectedArtistId }
    }

    var selectedAlbumId by remember { mutableStateOf<String?>(null) }

    var songName by remember { mutableStateOf("") }
    var songLengthText by remember { mutableStateOf("") } // in seconds or mm:ss later
    var bpmText by remember { mutableStateOf("") }
    var docUrl by remember { mutableStateOf("") }

    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    // Keep selected artist in sync if list changes (e.g. you add the first artist)
    LaunchedEffect(artists) {
        if (artists.isNotEmpty() && selectedArtistId == null) {
            selectedArtistId = artists.first().uuid
            val filtered = albums.filter { it.artistUuid == selectedArtistId }
            selectedAlbumId = filtered.firstOrNull()?.uuid
        } else if (artists.none { it.uuid == selectedArtistId }) {
            selectedArtistId = artists.firstOrNull()?.uuid
            val filtered = albums.filter { it.artistUuid == selectedArtistId }
            selectedAlbumId = filtered.firstOrNull()?.uuid
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Add Song", style = MaterialTheme.typography.titleMedium)

            // Artist selector
            ArtistDropdownField(
                artists = artists,
                selectedArtistId = selectedArtistId,
                onArtistSelected = {
                    selectedArtistId = it
                    saved = false
                }
            )

            // Album selector (optional)
            AlbumDropdownField(
                albums = albumsForArtist,
                selectedAlbumId = selectedAlbumId,
                onAlbumSelected = {
                    selectedAlbumId = it
                    saved = false
                }
            )

            OutlinedTextField(
                value = songName,
                onValueChange = {
                    songName = it
                    saved = false
                },
                label = { Text("Song Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = songLengthText,
                onValueChange = {
                    songLengthText = it
                    saved = false
                },
                label = { Text("Song Length (seconds)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = bpmText,
                onValueChange = {
                    bpmText = it
                    saved = false
                },
                label = { Text("BPM") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = docUrl,
                onValueChange = {
                    docUrl = it
                    saved = false
                },
                label = { Text("Doc URL (Google Doc / Tab URL)") },
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            if (saved) {
                Text("Song saved ✅", color = MaterialTheme.colorScheme.primary)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = {
                    val artist = selectedArtistId
                    val album = selectedAlbumId
                    if (artist == null) {
                        error = "Please select an artist"
                        saved = false
                        return@Button
                    }
                    if (album == null) {
                        error = "Please select an album"
                        saved = false
                        return@Button
                    }
                    if (songName.isBlank()) {
                        error = "Song name is required"
                        saved = false
                        return@Button
                    }
                    val length = songLengthText.toIntOrNull()
                    if (length == null || length <= 0) {
                        error = "Song length must be a positive number (seconds)"
                        saved = false
                        return@Button
                    }
                    val bpm = bpmText.toIntOrNull()
                    if (bpm == null || bpm <= 0) {
                        error = "BPM must be a positive number"
                        saved = false
                        return@Button
                    }
                    if (docUrl.isBlank()) {
                        error = "Doc URL is required"
                        saved = false
                        return@Button
                    }

                    error = null
                    onAddSong(
                        artist,
                        album,
                        songName,
                        length,
                        bpm,
                        docUrl
                    )

                    songName = ""
                    songLengthText = ""
                    docUrl = ""
                    saved = true
                }) {
                    Text("Save Song")
                }
            }
        }
    }
}

