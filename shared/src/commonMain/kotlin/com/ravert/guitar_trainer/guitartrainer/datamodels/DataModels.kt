package com.ravert.guitar_trainer.guitartrainer.datamodels

import kotlinx.serialization.Serializable

@Serializable
data class Artist(
    val uuid: String,
    val name: String,
    val image: String,
)

@Serializable
data class Album(
    val uuid: String,
    val artistUuid: String,
    val name: String,
    val image: String,
)

@Serializable
data class Song(
    val uuid: String,
    val artistUuid: String,
    val albumUuid: String,
    val name: String,
    val songLength: Int, // Seconds
    val bpm: Int,
    val docUrl: String,
)

@Serializable
data class GearItem(
    val uuid: String,
    val name: String,
    val brand: String,
    val imageUrl: String,
    val buyLink: String,
)