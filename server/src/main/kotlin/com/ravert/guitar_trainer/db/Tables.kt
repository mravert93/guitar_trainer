package com.ravert.guitar_trainer.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ReferenceOption

object ArtistsTable : Table("artists") {
    val id = uuid("id")
    val name = varchar("name", 255)
    val imageUrl = text("image_url").nullable()

    override val primaryKey = PrimaryKey(id)
}

object AlbumsTable : Table("albums") {
    val id = uuid("id")
    val artistId = reference("artist_id", ArtistsTable.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val imageUrl = text("image_url").nullable()

    override val primaryKey = PrimaryKey(id)
}

object SongsTable : Table("songs") {
    val id = uuid("id")
    val artistId = reference("artist_id", ArtistsTable.id, onDelete = ReferenceOption.CASCADE)
    val albumId = reference("album_id", AlbumsTable.id, onDelete = ReferenceOption.SET_NULL)
    val name = varchar("name", 255)
    val lengthSeconds = integer("length_seconds")
    val bpm = integer("bpm")
    val docUrl = text("doc_url")

    override val primaryKey = PrimaryKey(id)
}