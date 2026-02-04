package com.ravert.guitar_trainer.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ravert.guitar_trainer.components.ImageBlock
import com.ravert.guitar_trainer.components.PhoneSiteHeader
import com.ravert.guitar_trainer.components.SiteHeader
import com.ravert.guitar_trainer.components.VerticalImageBlock
import com.ravert.guitar_trainer.guitartrainer.datamodels.Album
import com.ravert.guitar_trainer.guitartrainer.datamodels.Artist
import com.ravert.guitar_trainer.guitartrainer.datamodels.Song
import com.ravert.guitar_trainer.guitartrainer.managers.LibraryProvider
import kotlin.collections.chunked

@Composable
fun ArtistDetailsPhone(
    artistId: String,
    libraryProvider: LibraryProvider,
    onSongClick: (String) -> Unit,
    onAlbumClick: (String, String) -> Unit,
    onArtistsClick: () -> Unit,
    onTabsClick: () -> Unit,
    onAdminClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    var artist by remember(artistId) { mutableStateOf<Artist?>(null) }
    var songs by remember { mutableStateOf<Map<String, List<Song>>>(mapOf()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }

    LaunchedEffect(Unit) {
        artist = libraryProvider.artists.value.firstOrNull { it.uuid == artistId }
        songs = libraryProvider.songs.value.filter { it.artistUuid == artistId }.groupBy { it.albumUuid }
        albums = libraryProvider.albums.value.filter { it.artistUuid == artistId }
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        PhoneSiteHeader(
            onTabsClick = onTabsClick,
            onArtistsClick = onArtistsClick,
            onAdminClick = onAdminClick,
        )

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
                    .background(Color.Black),
            ) {
                Text(
                    text = (artist?.name ?: "") + " Tabs",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 40.dp)
                )
            }

            Spacer(Modifier.height(40.dp))

            albums.forEachIndexed { _, album ->
                val albumSongs = songs[album.uuid] ?: emptyList()

                VerticalImageBlock(
                    name = album.name,
                    imageUrl = album.image,
                    songs = albumSongs,
                    imageSize = 140.dp,
                    squareShape = true,
                    textColor = Color.Black,
                    onSongClick = onSongClick,
                    onMoreClick = {
                        onAlbumClick(artistId, album.uuid)
                    }
                )

                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}