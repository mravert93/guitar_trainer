package com.ravert.guitar_trainer.interop

import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLIFrameElement

actual fun mountYoutubeIntoSlot(slotId: String, videoId: String) {
    val slot = document.getElementById(slotId) as? HTMLElement ?: return

    // Clear anything currently inside
    while (slot.firstChild != null) slot.removeChild(slot.firstChild!!)

    // Create a responsive wrapper (16:9)
    val wrapper = document.createElement("div") as HTMLElement
    wrapper.style.position = "relative"
    wrapper.style.width = "100%"
    wrapper.style.paddingTop = "56.25%" // 16:9
    wrapper.style.borderRadius = "16px"
    wrapper.style.setProperty("overflow", "hidden") // use setProperty (typed prop may be missing)

    val iframe = document.createElement("iframe") as HTMLIFrameElement
    iframe.src = "https://www.youtube-nocookie.com/embed/$videoId"
    iframe.style.position = "absolute"
    iframe.style.left = "0"
    iframe.style.top = "0"
    iframe.style.width = "100%"
    iframe.style.height = "100%"
    iframe.style.border = "0"
    iframe.allowFullscreen = true

    // let the user scroll *around* it; iframe still captures scroll while hovering (that’s expected)
    // but it won't break page scrolling overall anymore.
    iframe.setAttribute(
        "allow",
        "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
    )

    wrapper.appendChild(iframe)
    slot.appendChild(wrapper)
}

actual fun unmountYoutubeFromSlot(slotId: String) {
    val slot = document.getElementById(slotId) as? HTMLElement ?: return
    while (slot.firstChild != null) slot.removeChild(slot.firstChild!!)
}