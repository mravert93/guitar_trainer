package com.ravert.guitar_trainer.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ravert.guitar_trainer.components.PhoneSiteHeader
import com.ravert.guitar_trainer.components.SiteHeader
import com.ravert.guitar_trainer.guitartrainer.datamodels.Album
import com.ravert.guitar_trainer.guitartrainer.datamodels.Artist
import com.ravert.guitar_trainer.guitartrainer.datamodels.Song
import com.ravert.guitar_trainer.guitartrainer.managers.LibraryProvider

@Composable
fun AlbumDetailsScreen(
    artistId: String,
    albumId: String,
    libraryProvider: LibraryProvider,
    onSongClick: (String) -> Unit,
    onArtistsClick: () -> Unit,
    onTabsClick: () -> Unit,
    onAdminClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    var songs by remember { mutableStateOf<List<Song>>(listOf()) }
    var album by remember { mutableStateOf<Album?>(null) }

    LaunchedEffect(Unit) {
        album = libraryProvider.albums.value.firstOrNull { it.uuid == albumId }
        songs = libraryProvider.songs.value.filter {
            it.artistUuid == artistId &&
            it.albumUuid == albumId
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val isPhone = maxWidth < 900.dp

        Column(
            modifier = modifier.fillMaxSize(),
        ) {
            if (isPhone) {
                PhoneSiteHeader(
                    onTabsClick = onTabsClick,
                    onArtistsClick = onArtistsClick,
                    onAdminClick = onAdminClick
                )
            } else {
                SiteHeader(
                    onTabsClick = onTabsClick,
                    onArtistsClick = onArtistsClick,
                    onAdminClick = onAdminClick
                )
            }

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                        .background(Color.Black),
                ) {
                    Text(
                        text = (album?.name ?: "") + " Tabs",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 40.dp)
                    )
                }

                Spacer(Modifier.height(40.dp))

                songs.forEach { song ->
                    Text(
                        text = song.name,
                        textDecoration = TextDecoration.Underline,
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .clickable {
                                onSongClick(song.uuid)
                            }
                    )
                }
            }

            Spacer(modifier = modifier.height(40.dp))
        }
    }
}