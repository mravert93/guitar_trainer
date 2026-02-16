package com.ravert.guitar_trainer.pages

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import com.ravert.guitar_trainer.components.PhoneSiteHeader
import com.ravert.guitar_trainer.components.SelectedTab
import com.ravert.guitar_trainer.components.SiteHeader
import com.ravert.guitar_trainer.guitartrainer.datamodels.Artist
import com.ravert.guitar_trainer.guitartrainer.managers.LibraryProvider


@Composable
fun ArtistsScreen(
    libraryProvider: LibraryProvider,
    onArtistClick: (String) -> Unit,
    onHomeClick: () -> Unit,
    onTabsClick: () -> Unit,
    onAdminClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onGearClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val artists by libraryProvider.artists.collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()

    // Filter + sort artists
    val filteredArtists = remember(artists, q) {
        artists
            .filter { artist ->
                q.isBlank() || artist.name.lowercase().contains(q)
            }
            .sortedBy { it.name.lowercase() }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val isPhone = maxWidth < 900.dp

        val numRows = if (isPhone) 2 else 4
        val rows = filteredArtists.chunked(numRows)

        Column {
            if (isPhone) {
                PhoneSiteHeader(
                    selectedTab = SelectedTab.ARTISTS,
                    onHomeClick = onHomeClick,
                    onTabsClick = onTabsClick,
                    onArtistsClick = onArtistsClick,
                    onAdminClick = onAdminClick,
                    onGearClick = onGearClick,
                    onAboutClick = onAboutClick,
                )
            } else {
                SiteHeader(
                    selectedTab = SelectedTab.ARTISTS,
                    onHomeClick = onHomeClick,
                    onTabsClick = onTabsClick,
                    onArtistsClick = onArtistsClick,
                    onAdminClick = onAdminClick,
                    onGearClick = onGearClick,
                    onAboutClick = onAboutClick,
                )
            }

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(vertical = 16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Artists",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp, start = 16.dp)
                )

                // 🔎 Search bar
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search artists…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedPlaceholderColor = Color.Gray,
                        unfocusedPlaceholderColor = Color.Gray,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Spacer(Modifier.height(12.dp))

                if (rows.isEmpty()) {
                    // Empty state
                    Text(
                        text = "No artists found",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    rows.forEachIndexed { rowIndex, rowArtists ->
                        val isDarkRow = rowIndex % 2 == 1
                        val rowBackground = if (isDarkRow) Color.Black else Color.White
                        val fallbackTextColor = if (isDarkRow) Color.White else Color.Black

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBackground)
                                .padding(vertical = 32.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowArtists.forEach { artist ->
                                key(artist.uuid) {
                                    ArtistItem(
                                        artist = artist,
                                        onClick = { onArtistClick(artist.uuid) },
                                        textColor = fallbackTextColor,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                    )
                                }
                            }

                            // Fill empty slots in the last row
                            if (rowArtists.size < numRows) {
                                repeat(numRows - rowArtists.size) {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistItem(
    artist: Artist,
    onClick: () -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RemoteImageCircle(
            name = artist.name,
            imageUrl = artist.image,
            squareShape = false,
            textColor = textColor,
            modifier = Modifier
                .size(140.dp)
        )

        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFeb9d02),
            textAlign = TextAlign.Center,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// commonMain
@Composable
expect fun RemoteImageCircle(
    name: String,
    imageUrl: String,
    squareShape: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier
)

