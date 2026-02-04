package com.ravert.guitar_trainer.pages

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ravert.guitar_trainer.guitartrainer.datamodels.Album
import com.ravert.guitar_trainer.guitartrainer.datamodels.Artist

@Composable
fun LibraryAdminScreen(
    artists: List<Artist>,
    albums: List<Album>,
    onAddArtist: (name: String, imageUrl: String) -> Unit,
    onAddAlbum: (artistId: String, name: String, imageUrl: String) -> Unit,
    onAddSong: (
        artistId: String,
        albumId: String,
        name: String,
        lengthSeconds: Int,
        bpm: Int,
        docUrl: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Library Admin", style = MaterialTheme.typography.headlineSmall)

        Text("Artists: ${artists.size}, Albums: ${albums.size}")

        AddArtistSection(onAddArtist)

        AddAlbumSection(
            artists = artists,
            onAddAlbum = onAddAlbum
        )

        AddSongSection(
            artists = artists,
            albums = albums,
            onAddSong = onAddSong
        )
    }
}
