package com.ravert.guitar_trainer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.ravert.guitar_trainer.interop.mountYoutubeIntoSlot
import com.ravert.guitar_trainer.interop.unmountYoutubeFromSlot

@Composable
fun YoutubeEmbedSlot(videoId: String, modifier: Modifier = Modifier) {
    // Just a placeholder div for JS to mount into
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp) // pick what you want
            .background(Color.Black)
    ) {
        // give the Box a stable ID by rendering a small html container via JS
        // We'll attach the iframe to this element by ID.
        HtmlMount(id = "yt-slot-$videoId")
    }

    // mount the iframe whenever videoId changes
    LaunchedEffect(videoId) {
        mountYoutubeIntoSlot(slotId = "yt-slot-$videoId", videoId = videoId)
    }

    DisposableEffect(videoId) {
        onDispose { unmountYoutubeFromSlot("yt-slot-$videoId") }
    }
}

@Composable
expect fun HtmlMount(id: String)


