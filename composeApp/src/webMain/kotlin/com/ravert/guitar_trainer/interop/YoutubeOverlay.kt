package com.ravert.guitar_trainer.interop

import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLIFrameElement

private fun overlayDiv(): HTMLDivElement? =
    document.getElementById("yt-overlay") as? HTMLDivElement

fun ytOverlayHide() {
    val el = overlayDiv() ?: return
    el.style.left = "-99999px"
    el.style.top = "-99999px"
    el.style.width = "0"
    el.style.height = "0"
    el.innerHTML = "" // optional: remove iframe when hidden
}

fun ytOverlayShowAndSetVideo(videoId: String) {
    val el = overlayDiv() ?: return

    // Only create iframe once
    if (el.querySelector("iframe") == null) {
        val iframe = document.createElement("iframe") as HTMLIFrameElement
        iframe.style.width = "100%"
        iframe.style.height = "100%"
        iframe.style.border = "0"
        iframe.allowFullscreen = true
        iframe.setAttribute(
            "allow",
            "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
        )
        el.appendChild(iframe)
    }

    val iframe = el.querySelector("iframe") as HTMLIFrameElement
    iframe.src = "https://www.youtube.com/embed/$videoId"
}

/**
 * Positions the overlay over a Compose placeholder box.
 * x/y/w/h are in *CSS pixels* relative to the viewport.
 */
fun ytOverlaySetRect(x: Double, y: Double, w: Double, h: Double) {
    val el = overlayDiv() ?: return
    el.style.left = "${x}px"
    el.style.top = "${y}px"
    el.style.width = "${w}px"
    el.style.height = "${h}px"
}
