package com.ravert.guitar_trainer.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.ravert.guitar_trainer.components.SelectedTab
import com.ravert.guitar_trainer.components.SiteHeader
import com.ravert.guitar_trainer.guitartrainer.datamodels.Album
import com.ravert.guitar_trainer.guitartrainer.datamodels.Artist
import com.ravert.guitar_trainer.guitartrainer.datamodels.Song
import com.ravert.guitar_trainer.guitartrainer.managers.LibraryProvider

@Composable
fun ArtistDetailsScreen(
    artistId: String,
    libraryProvider: LibraryProvider,
    onHomeClick: () -> Unit,
    onSongClick: (String) -> Unit,
    onAlbumClick: (String, String) -> Unit,
    onArtistsClick: () -> Unit,
    onTabsClick: () -> Unit,
    onAdminClick: () -> Unit,
    onGearClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
    ) {
        val isPhone = maxWidth < 900.dp

        if (isPhone) {
            ArtistDetailsPhone(
                artistId = artistId,
                libraryProvider = libraryProvider,
                onHomeClick = onHomeClick,
                onSongClick = onSongClick,
                onAlbumClick = onAlbumClick,
                onArtistsClick = onArtistsClick,
                onTabsClick = onTabsClick,
                onAdminClick = onAdminClick,
                onGearClick = onGearClick,
                onAboutClick = onAboutClick,
                modifier = modifier
            )
        } else {
            ArtistDetailsDesktop(
                artistId = artistId,
                libraryProvider = libraryProvider,
                onHomeClick = onHomeClick,
                onSongClick = onSongClick,
                onAlbumClick = onAlbumClick,
                onArtistsClick = onArtistsClick,
                onTabsClick = onTabsClick,
                onAdminClick = onAdminClick,
                onGearClick = onGearClick,
                onAboutClick = onAboutClick,
                modifier = modifier
            )
        }
    }
}


@Composable
fun ArtistDetailsDesktop(
    artistId: String,
    libraryProvider: LibraryProvider,
    onHomeClick: () -> Unit,
    onSongClick: (String) -> Unit,
    onAlbumClick: (String, String) -> Unit,
    onArtistsClick: () -> Unit,
    onTabsClick: () -> Unit,
    onAdminClick: () -> Unit,
    onGearClick: () -> Unit,
    onAboutClick: () -> Unit,
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

    val rows = remember(albums) {
        albums.chunked(3)
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        SiteHeader(
            selectedTab = SelectedTab.ARTISTS,
            onHomeClick = onHomeClick,
            onTabsClick = onTabsClick,
            onArtistsClick = onArtistsClick,
            onAdminClick = onAdminClick,
            onGearClick = onGearClick,
            onAboutClick = onAboutClick,
        )

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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

            rows.forEachIndexed { _, row ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.Center
                ) {
                    row.forEach { album ->
                        val albumSongs = songs[album.uuid] ?: emptyList()
                        key(album.uuid) {
                            Box(
                                modifier = Modifier.weight(1f)
                                    .padding(horizontal = 8.dp),
                            ) {
                                ImageBlock(
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
                            }
                        }
                    }

                    // Fill empty slots in the last row
                    if (row.size < 3) {
                        repeat(3 - row.size) {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}