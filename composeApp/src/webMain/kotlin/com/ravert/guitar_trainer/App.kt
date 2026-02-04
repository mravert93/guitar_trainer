package com.ravert.guitar_trainer

import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ravert.guitar_trainer.components.ImageBlock
import com.ravert.guitar_trainer.components.PhoneSiteHeader
import com.ravert.guitar_trainer.components.SiteHeader
import com.ravert.guitar_trainer.guitartrainer.datamodels.Song
import com.ravert.guitar_trainer.guitartrainer.managers.LibraryProvider
import com.ravert.guitar_trainer.guitartrainer.parser.fetchDocHtml
import com.ravert.guitar_trainer.guitartrainer.parser.sanitizeForMonospace
import com.ravert.guitar_trainer.pages.AlbumDetailsScreen
import com.ravert.guitar_trainer.pages.ArtistDetailsDesktop
import com.ravert.guitar_trainer.pages.ArtistDetailsScreen
import com.ravert.guitar_trainer.pages.ArtistsScreen
import com.ravert.guitar_trainer.pages.LibraryAdminHost
import com.ravert.guitar_trainer.pages.TabbedAdminScreen
import com.ravert.guitar_trainer.router.Route

@Composable
fun App(
    currentRoute: Route,
    onNavigateToSong: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    onNavigateHome: () -> Unit,
    onArtists: () -> Unit,
    onAdminClick: () -> Unit,
    onAdminAdd: () -> Unit,
) {
    val libraryProvider = remember { LibraryProvider() }

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentRoute) {
                is Route.Home -> HomeScreen(
                    onSongClick = onNavigateToSong,
                    onArtistClick = onNavigateToArtist,
                    onArtistsClick = onArtists,
                    onTabsClick = onNavigateHome,
                    onAdminClick = onAdminClick,
                    libraryProvider = libraryProvider,
                    modifier = Modifier.fillMaxSize()
                )

                is Route.Song -> SongScreen(
                    songId = currentRoute.id,
                    onArtistsClick = onArtists,
                    onTabsClick = onNavigateHome,
                    onAdminClick = onAdminClick,
                    libraryProvider = libraryProvider,
                    modifier = Modifier.fillMaxSize()
                )

                Route.AdminAdd -> LibraryAdminHost(
                    provider = libraryProvider,
                    modifier = Modifier.fillMaxSize(),
                )

                Route.Admin -> TabbedAdminScreen(
                    libraryProvider = libraryProvider,
                    onAdd = onAdminAdd,
                    modifier = Modifier.fillMaxSize(),
                )

                Route.Artists -> ArtistsScreen(
                    libraryProvider = libraryProvider,
                    onArtistClick = onNavigateToArtist,
                    onTabsClick = onNavigateHome,
                    onArtistsClick = onArtists,
                    onAdminClick = onAdminClick,
                    modifier = Modifier.fillMaxSize()
                )

                is Route.Album -> AlbumDetailsScreen(
                    artistId = currentRoute.artistId,
                    albumId = currentRoute.albumId,
                    libraryProvider = libraryProvider,
                    onSongClick = onNavigateToSong,
                    onArtistsClick = onArtists,
                    onAdminClick = onAdminClick,
                    onTabsClick = onNavigateHome,
                    modifier = Modifier.fillMaxSize()
                )

                is Route.Artist -> ArtistDetailsScreen(
                    artistId = currentRoute.id,
                    libraryProvider = libraryProvider,
                    onSongClick = onNavigateToSong,
                    onAlbumClick = onNavigateToAlbum,
                    onArtistsClick = onArtists,
                    onAdminClick = onAdminClick,
                    onTabsClick = onNavigateHome,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    onSongClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onArtistsClick: () -> Unit,
    onTabsClick: () -> Unit,
    onAdminClick: () -> Unit,
    libraryProvider: LibraryProvider,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    val songs by libraryProvider.songs.collectAsState(initial = emptyList())
    val artists by libraryProvider.artists.collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()

    // Build a quick lookup for artists by id
    val artistById = remember(artists) {
        artists.associateBy { it.uuid }   // ⬅️ use `it.uuid` if that's your field
    }

    // Group songs by artistId
    val songsByArtistId = remember(songs) {
        songs.groupBy { it.artistUuid }
    }

    // Filtered artists:
    // Keep artist if:
    //  - artist name matches query OR
    //  - any of their songs match query
    val filteredArtists = remember(artists, songsByArtistId, q) {
        artists
            .sortedBy { it.name }
            .filter { artist ->
                if (q.isBlank()) return@filter true
                val artistMatches = artist.name.lowercase().contains(q)
                val songsMatch = songsByArtistId[artist.uuid]
                    .orEmpty()
                    .any { it.name.lowercase().contains(q) }
                artistMatches || songsMatch
            }
    }

    // Unknown-artist songs filtered by query
    val unknownArtistSongs = remember(songs, artistById, q) {
        songs
            .filter { artistById[it.artistUuid] == null }
            .filter { s -> q.isBlank() || s.name.lowercase().contains(q) }
            .sortedBy { it.name }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val isPhone = maxWidth < 900.dp

        Column {
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
                modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "All Songs",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(24.dp)
                )

                // 🔎 Search bar
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search artist or song…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
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

                // 1) Artists with songs, sorted by artist name
                filteredArtists
                    .sortedBy { it.name }
                    .forEach { artist ->
                        val artistSongs = songsByArtistId[artist.uuid].orEmpty()
                            .sortedBy { it.name }
                            .filter { song ->
                                if (q.isBlank()) true
                                else artist.name.lowercase().contains(q) || song.name.lowercase().contains(q)
                            }

                        if (artistSongs.isNotEmpty()) {
                            ImageBlock(
                                name = artist.name,
                                imageUrl = artist.image,
                                songs = artistSongs,
                                squareShape = false,
                                imageSize = 140.dp,
                                textColor = Color.White,
                                onSongClick = onSongClick,
                                onMoreClick = {
                                    onArtistClick(artist.uuid)
                                }
                            )

                            Spacer(Modifier.height(12.dp))
                        }
                    }

                // Unknown artist bucket (only shows if matches)
                if (unknownArtistSongs.isNotEmpty()) {
                    Divider()
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Unknown Artist",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                    ) {
                        unknownArtistSongs.forEach { song ->
                            TextButton(
                                onClick = { onSongClick(song.uuid) },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    song.name,
                                    textDecoration = TextDecoration.Underline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun SongScreen(
    songId: String,
    onArtistsClick: () -> Unit,
    onTabsClick: () -> Unit,
    onAdminClick: () -> Unit,
    libraryProvider: LibraryProvider,
    modifier: Modifier,
) {
    var song by remember { mutableStateOf<Song?>(null) }
    var docBody by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            isLoading = true
            error = null

            println("Getting song: ${songId}")
            val s = libraryProvider.getSongById(songId)
            song = s

            if (s != null) {
                println("Song retrieved: ${s.docUrl}")
                val txt = fetchDocHtml(s.docUrl + "/export?format=txt")
                docBody = sanitizeForMonospace(txt)
            }
        } catch (t: Throwable) {
            println("Something failed ${t.message}")
            error = t.message ?: t.toString()
        } finally {
            isLoading = false
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val isPhone = maxWidth < 900.dp

        Column {
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

            when {
                error != null -> Text("Error: $error")
                isLoading -> Text("Loading...")
                else -> AutoScrollDocScreen(
                    docText = docBody!!,
                    songDurationMs = song!!.songLength * 400L,
                    initialBpm = song!!.bpm,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

