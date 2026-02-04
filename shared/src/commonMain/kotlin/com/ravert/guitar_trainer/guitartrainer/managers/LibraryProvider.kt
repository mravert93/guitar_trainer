@file:OptIn(ExperimentalUuidApi::class)

package com.ravert.guitar_trainer.guitartrainer.managers

import com.ravert.guitar_trainer.guitartrainer.datamodels.Album
import com.ravert.guitar_trainer.guitartrainer.datamodels.Artist
import com.ravert.guitar_trainer.guitartrainer.datamodels.Song
import com.ravert.guitar_trainer.guitartrainer.datamodels.api.CreateAlbumRequest
import com.ravert.guitar_trainer.guitartrainer.datamodels.api.CreateArtistRequest
import com.ravert.guitar_trainer.guitartrainer.datamodels.api.CreateSongRequest
import com.ravert.guitar_trainer.guitartrainer.datamodels.api.DeleteAlbumRequest
import com.ravert.guitar_trainer.guitartrainer.datamodels.api.DeleteArtistRequest
import com.ravert.guitar_trainer.guitartrainer.datamodels.api.DeleteSongRequest
import com.ravert.guitar_trainer.guitartrainer.http.httpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.utils.EmptyContent.contentType
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.parametersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class LibraryProvider(
    private val baseUrl: String = "https://guitar-trainer.onrender.com", // "http://0.0.0.0:8081" Ktor server
    private val client: HttpClient = httpClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    private val _albums  = MutableStateFlow<List<Album>>(emptyList())
    private val _songs   = MutableStateFlow<List<Song>>(emptyList())

    val artists: StateFlow<List<Artist>> =
        _artists.map { it.sortedBy { a -> a.name } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Album>> =
        _albums.map { it.sortedBy { a -> a.name } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val songs: StateFlow<List<Song>> =
        _songs.map { it.sortedBy { s -> s.name } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // Initial load
        scope.launch {
            refreshAll()
        }
    }

    suspend fun refreshAll() {
        refreshArtists()
        refreshAlbums()
        refreshSongs()
    }

    suspend fun refreshArtists() {
        val list: List<Artist> = client.get("$baseUrl/artists").body()
        _artists.value = list
    }

    suspend fun refreshAlbums() {
        val list: List<Album> = client.get("$baseUrl/albums").body()
        _albums.value = list
    }

    suspend fun refreshSongs() {
        val list: List<Song> = client.get("$baseUrl/songs").body()
        _songs.value = list
    }

    // ---- mutations ----

    fun addArtist(uuid: String? = null, name: String, imageUrl: String?) {
        scope.launch {
            client.post("$baseUrl/artists") {
                contentType(ContentType.Application.Json)
                setBody(CreateArtistRequest(uuid, name, imageUrl))
            }
            refreshArtists()
        }
    }

    fun deleteArtist(uuid: String) {
        scope.launch {
            // Optimistic UI update
            val before = artists.value
            _artists.value = before.filterNot { it.uuid == uuid }

            runCatching {
                client.delete("$baseUrl/artists") {
                    contentType(ContentType.Application.Json)
                    setBody(DeleteArtistRequest(uuid))
                }
            }.onFailure {
                // rollback
                _artists.value = before
            }
        }
    }

    fun addAlbum(uuid: String? = null, artistId: String, name: String, imageUrl: String?) {
        scope.launch {
            client.post("$baseUrl/albums") {
                contentType(ContentType.Application.Json)
                setBody(CreateAlbumRequest(uuid, artistId, name, imageUrl))
            }
            refreshAlbums()
        }
    }

    fun deleteAlbum(uuid: String) {
        scope.launch {
            // Optimistic UI update
            val before = _albums.value
            _albums.value = before.filterNot { it.uuid == uuid }

            runCatching {
                client.delete("$baseUrl/albums") {
                    contentType(ContentType.Application.Json)
                    setBody(DeleteAlbumRequest(uuid))
                }
            }.onFailure {
                // rollback
                _albums.value = before
            }
        }
    }

    fun addSong(
        uuid: String? = null,
        artistId: String,
        albumId: String,
        name: String,
        lengthSeconds: Int,
        bpm: Int,
        docUrl: String
    ) {
        scope.launch {
            client.post("$baseUrl/songs") {
                contentType(ContentType.Application.Json)
                setBody(CreateSongRequest(uuid, artistId, albumId, name, lengthSeconds, bpm, docUrl))
            }
            refreshSongs()
        }
    }

    fun deleteSong(uuid: String) {
        scope.launch {
            // Optimistic UI update
            val before = _songs.value
            _songs.value = before.filterNot { it.uuid == uuid }

            runCatching {
                client.delete("$baseUrl/songs") {
                    contentType(ContentType.Application.Json)
                    setBody(DeleteSongRequest(uuid))
                }
            }.onFailure {
                // rollback
                _songs.value = before
            }
        }
    }

    suspend fun syncSpreadsheet() {
        val spreadSheetUrl = "https://docs.google.com/spreadsheets/d/1ZtMQYruZ9rbX_eZRk9lhypCyd7aUYt0rwCQSjICY9ZQ/export?format=csv&gid=0"

        client.post("$baseUrl/admin/importSheet") {
            contentType(ContentType.Application.Json)
            parameter("csvUrl", spreadSheetUrl)
        }
    }

    // convenient lookup for SongScreen
    fun getSongById(id: String): Song? =
        songs.value.firstOrNull { it.uuid == id }
}