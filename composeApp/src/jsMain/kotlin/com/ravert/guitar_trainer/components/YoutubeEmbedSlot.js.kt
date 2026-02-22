package com.ravert.guitar_trainer.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.document

@Composable
actual fun HtmlMount(id: String) {
    DisposableEffect(id) {
        val existing = document.getElementById(id)
        if (existing == null) {
            val el = document.createElement("div")
            el.id = id
            // IMPORTANT: append into root. If you have a better container, use that.
            // If this ends up in the wrong place, tell me your root id and I'll adjust.
            document.getElementById("root")?.appendChild(el)
        }
        onDispose {
            document.getElementById(id)?.remove()
        }
    }
}