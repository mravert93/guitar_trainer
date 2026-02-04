package com.ravert.guitar_trainer.components

import kotlinx.browser.window

actual fun openLink(url: String) {
    window.location.href = url
}