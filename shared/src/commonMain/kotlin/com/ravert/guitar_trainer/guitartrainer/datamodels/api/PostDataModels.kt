package com.ravert.guitar_trainer.guitartrainer.datamodels.api

import kotlinx.serialization.Serializable

@Serializable
data class CreateArtistRequest(
    val uuid: String? = null,
    val name: String,
    val imageUrl: String? = null
)

@Serializable
data class DeleteArtistRequest(
    val uuid: String
)

@Serializable
data class CreateAlbumRequest(
    val uuid: String? = null,
    val artistId: String,
    val name: String,
    val imageUrl: String? = null
)

@Serializable
data class DeleteAlbumRequest(
    val uuid: String
)

@Serializable
data class CreateSongRequest(
    val uuid: String? = null,
    val artistId: String,
    val albumId: String,
    val name: String,
    val lengthSeconds: Int,
    val bpm: Int,
    val docUrl: String
)

@Serializable
data class DeleteSongRequest(
    val uuid: String
)
