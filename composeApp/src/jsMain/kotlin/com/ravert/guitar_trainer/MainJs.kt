package com.ravert.guitar_trainer

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.ravert.guitar_trainer.router.parsePathToRoute
import com.ravert.guitar_trainer.router.Route
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val root = document.getElementById("root") ?: error("Missing #root")

    ComposeViewport(root) {
        var route by remember {
            mutableStateOf(parsePathToRoute(window.location.pathname))
        }

        // Listen to browser back/forward
        DisposableEffect(Unit) {
            val listener: (Event) -> Unit = {
                route = parsePathToRoute(window.location.pathname)
            }
            window.addEventListener("popstate", listener)
            onDispose {
                window.removeEventListener("popstate", listener)
            }
        }

        App(
            currentRoute = route,
            onNavigateToSong = { id ->
                val path = "/songs/$id"
                window.history.pushState(null, "", path)
                route = Route.Song(id)
            },
            onNavigateHome = {
                val path = "/"
                window.history.pushState(null, "", path)
                route = Route.Home
            },
            onNavigateToTabs = {
                val path = "/tabs"
                window.history.pushState(null, "", path)
                route = Route.Tabs
            },
            onArtists = {
                val path = "/artists"
                window.history.pushState(null, "", path)
                route = Route.Artists
            },
            onAdminClick = {
                val path = "/admin"
                window.history.pushState(null, "", path)
                route = Route.Admin
            },
            onNavigateToArtist = { id ->
                val path = "/artists/$id"
                window.history.pushState(null, "", path)
                route = Route.Artist(id)
            },
            onNavigateToAlbum = { artistId, albumId ->
                val path = "/albums/$artistId/$albumId"
                window.history.pushState(null, "", path)
                route = Route.Album(artistId, albumId)
            },
            onAdminAdd = {
                val path = "/admin/add"
                window.history.pushState(null, "", path)
                route = Route.AdminAdd
            },
            onNavigateToGear = {
                val path = "/gear"
                window.history.pushState(null, "", path)
                route = Route.Gear
            },
            onNavigateToAbout = {
                val path = "/about"
                window.history.pushState(null, "", path)
                route = Route.About
            },
        )
    }
}
