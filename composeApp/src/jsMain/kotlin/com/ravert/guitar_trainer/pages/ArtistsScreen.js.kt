package com.ravert.guitar_trainer.pages

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.jetbrains.skia.Image as SkiaImage
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

@OptIn(ExperimentalWasmJsInterop::class)
@Composable
actual fun RemoteImageCircle(
    name: String,
    imageUrl: String,
    squareShape: Boolean,
    textColor: Color,
    modifier: Modifier
) {
    var bitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    var loadFailed by remember(imageUrl) { mutableStateOf(false) }

    LaunchedEffect(imageUrl) {
        bitmap = null
        loadFailed = false

        if (imageUrl.isNullOrBlank()) return@LaunchedEffect

        try {
            // Fetch the image as binary
            val proxied = "http://0.0.0.0:8081/imageProxy?url=${encodeUrlComponent(imageUrl)}"
            val response = window.fetch(proxied).await()
            if (!response.ok) {
                console.warn("Image fetch failed: ${response.status} ${response.statusText}")
                loadFailed = true
                return@LaunchedEffect
            }

            val buffer = response.arrayBuffer().await()
            val uint8 = Uint8Array(buffer)

            val bytes = ByteArray(uint8.length) { i ->
                (uint8.get(i).toInt()).toByte()
            }

            // Decode with Skia
            val skiaImage = SkiaImage.makeFromEncoded(bytes)
            bitmap = skiaImage.toComposeImageBitmap()

        } catch (e: Throwable) {
            console.error("Failed to load image: ", e)
            loadFailed = true
        }
    }

    val shape = if (squareShape) RoundedCornerShape(2.dp) else CircleShape
    Box(
        modifier = modifier
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            loadFailed || imageUrl.isNullOrBlank() -> {
                LetterAvatarCircle(
                    text = name,
                    shape = shape,
                    textColor = textColor,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                // Simple "loading" state, you can make this prettier
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LetterAvatarCircle(
    text: String,
    shape: RoundedCornerShape,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

fun encodeUrlComponent(value: String): String =
    js("encodeURIComponent")(value) as String