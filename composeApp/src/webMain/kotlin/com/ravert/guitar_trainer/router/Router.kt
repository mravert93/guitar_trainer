package com.ravert.guitar_trainer.router

sealed class Route {
    object Home : Route()
    object Tabs : Route()
    object Admin : Route()
    object AdminAdd : Route()
    object Artists : Route()
    data class Song(val id: String) : Route()
    data class Artist(val id: String) : Route()
    data class Album(val artistId: String, val albumId: String) : Route()
    data object Gear : Route()
    data object About : Route()
}

fun parsePathToRoute(path: String): Route {
    // Normalize: remove query, trailing slashes
    val clean = path.substringBefore("?").trimEnd('/')

    if (clean.isEmpty() || clean == "/") return Route.Home

    val segments = clean.split("/").filter { it.isNotBlank() }
    return when {
        segments.size == 2 && segments[0] == "songs" -> Route.Song(id = segments[1])
        segments.size == 2 && segments[0] == "admin" && segments[1] == "add" -> Route.AdminAdd
        segments.size == 2 && segments[0] == "artists" -> Route.Artist(id = segments[1])
        segments.size == 3 && segments[0] == "albums" -> Route.Album(artistId = segments[1], albumId = segments[2])
        segments[0] == "admin" -> Route.Admin
        segments[0] == "artists" -> Route.Artists
        segments[0] == "tabs" -> Route.Tabs
        segments[0] == "gear" -> Route.Gear
        segments[0] == "about" -> Route.About
        else -> Route.Home // fallback
    }
}
