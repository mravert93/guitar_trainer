package com.ravert.guitar_trainer.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ravert.guitar_trainer.guitartrainer.managers.LibraryProvider

@Composable
fun LibraryAdminHost(
    provider: LibraryProvider,
    modifier: Modifier = Modifier) {
    val artists by provider.artists.collectAsState()
    val albums  by provider.albums.collectAsState()

    LibraryAdminScreen(
        artists = artists,
        albums = albums,
        onAddArtist = { name, imageUrl ->
            provider.addArtist(null, name, imageUrl)
        },
        onAddAlbum = { artistId, name, imageUrl ->
            provider.addAlbum(null, artistId, name, imageUrl)
        },
        onAddSong = { artistId, albumId, name, lengthSeconds, bpm, docUrl ->
            provider.addSong(null, artistId, albumId, name, lengthSeconds, bpm, docUrl)
        },
        modifier = modifier,
    )
}
