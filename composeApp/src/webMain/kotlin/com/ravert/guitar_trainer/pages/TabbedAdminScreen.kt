package com.ravert.guitar_trainer.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ravert.guitar_trainer.components.AlbumsAdminPanel
import com.ravert.guitar_trainer.components.ArtistsAdminPanel
import com.ravert.guitar_trainer.components.SongsAdminPanel
import com.ravert.guitar_trainer.guitartrainer.managers.LibraryProvider
import kotlinx.coroutines.launch

@Composable
fun TabbedAdminScreen(
    libraryProvider: LibraryProvider,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artists by libraryProvider.artists.collectAsState(initial = emptyList())
    val albums by libraryProvider.albums.collectAsState(initial = emptyList())
    val songs by libraryProvider.songs.collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) }

    // --- Sync UI state ---
    var syncing by rememberSaveable { mutableStateOf(false) }
    var syncMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var syncIsError by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Admin",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }

        Spacer(Modifier.height(12.dp))

        // --- Sync section (top) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515))
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Spreadsheet Sync",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    if (syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }

                    Button(
                        enabled = !syncing,
                        onClick = {
                            syncing = true
                            syncMessage = null
                            syncIsError = false

                            scope.launch {
                                runCatching {
                                    libraryProvider.syncSpreadsheet()
                                }.onSuccess {
                                    syncMessage = "Sync complete."
                                    syncIsError = false
                                    libraryProvider.refreshAll()
                                }.onFailure { t ->
                                    syncMessage = "Sync failed: ${t.message ?: "Unknown error"}"
                                    syncIsError = true
                                }
                                syncing = false
                            }
                        }
                    ) {
                        Text(if (syncing) "Syncing..." else "Sync now")
                    }
                }

                if (syncMessage != null) {
                    Text(
                        text = syncMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (syncIsError) Color(0xFFFF6B6B) else Color(0xFF8BE28B)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Artists") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Albums") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Songs") })
        }

        Spacer(Modifier.height(12.dp))

        when (selectedTab) {
            0 -> ArtistsAdminPanel(
                artists = artists.sortedBy { it.name.lowercase() },
                onCreate = { _, _ -> onAdd() },
                onSave = { uuid, name, image ->
                    libraryProvider.addArtist(uuid, name, image)
                },
                onDelete = { libraryProvider.deleteArtist(it.uuid) },
            )

            1 -> AlbumsAdminPanel(
                albums = albums.sortedBy { it.name.lowercase() },
                artists = artists,
                onCreate = { onAdd() },
                onSave = { uuid, artistId, name, image ->
                    libraryProvider.addAlbum(uuid, artistId, name, image)
                },
                onDelete = { libraryProvider.deleteAlbum(it.uuid) },
            )

            2 -> SongsAdminPanel(
                songs = songs.sortedBy { it.name.lowercase() },
                artists = artists,
                albums = albums,
                onCreate = { onAdd() },
                onSave = { uuid, artistUuid, albumUuid, name, songLength, bpm, docUrl ->
                    libraryProvider.addSong(uuid, artistUuid, albumUuid, name, songLength, bpm, docUrl)
                },
                onDelete = { libraryProvider.deleteSong(it.uuid) },
            )
        }
    }
}

