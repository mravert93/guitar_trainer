package com.ravert.guitar_trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravert.guitar_trainer.metronome.playMetronomeClick
import guitar_trainer.composeapp.generated.resources.JetBrainsMono_Regular
import guitar_trainer.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.Font as ResFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AutoScrollDocScreen(
    docText: String,
    songDurationMs: Long,
    initialBpm: Int,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var isPlaying by remember { mutableStateOf(false) }
    var speedMultiplier by remember { mutableStateOf(1f) } // 0.5x–2x
    var isMetronomeOn by remember { mutableStateOf(false) }
    var isTickOn by remember { mutableStateOf(false) }      // visual tick
    val scope = rememberCoroutineScope()

    // -------- Auto-scroll loop --------
    LaunchedEffect(isPlaying, docText, songDurationMs, speedMultiplier) {
        if (!isPlaying) return@LaunchedEffect

        // Let layout happen so maxValue is correct
        delay(50)

        val totalScroll = scrollState.maxValue
        if (totalScroll <= 0 || songDurationMs <= 0) return@LaunchedEffect

        val frameMs = 16L
        var elapsed = 0L

        // Effective duration considering speed multiplier
        val effectiveDurationMs = (songDurationMs / speedMultiplier).toLong()

        while (isPlaying && elapsed < effectiveDurationMs) {
            delay(frameMs)
            elapsed += frameMs

            val fraction = (elapsed.toFloat() / effectiveDurationMs.toFloat())
                .coerceIn(0f, 1f)

            val targetScroll = (totalScroll * fraction).roundToInt()
            scrollState.scrollTo(targetScroll)
        }

        // Ensure we land exactly at the bottom
        scrollState.scrollTo(totalScroll)
        isPlaying = false
    }

    // -------- Metronome loop --------
    LaunchedEffect(isMetronomeOn, isPlaying, initialBpm) {
        if (!isMetronomeOn || !isPlaying || initialBpm <= 0) return@LaunchedEffect

        val bpm = (initialBpm * speedMultiplier).toInt()
        val intervalMs = (60_000f / bpm.toFloat()).toLong()

        while (isMetronomeOn && isPlaying) {
            playMetronomeClick()

            isTickOn = true
            delay(80)          // flash duration
            isTickOn = false

            val remaining = intervalMs - 80
            if (remaining > 0) {
                delay(remaining)
            }
        }
        isTickOn = false
    }

    val mono = FontFamily(
        ResFont(Res.font.JetBrainsMono_Regular)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (songDurationMs > 0L) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
                    .padding(16.dp)
            ) {
                // --- Top controls row ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = {
                        if (isPlaying) {
                            isPlaying = false
                        } else {
                            // restart from top if we already finished
                            if (scrollState.value >= scrollState.maxValue) {
                                scope.launch {
                                    scrollState.scrollTo(0)
                                }
                            }
                            isPlaying = true
                        }
                    }) {
                        Text(
                            if (isPlaying) "Pause" else "Play",
                            color = Color.White,
                        )
                    }

                    OutlinedButton(onClick = {
                        isPlaying = false
                        scope.launch {
                            scrollState.scrollTo(0)
                        }
                    }) {
                        Text(
                            "Reset",
                            color = Color.White,
                        )
                    }

                    // Metronome toggle (only if bpm provided)
                    val bpm = (initialBpm * speedMultiplier).toInt()
                    if (bpm > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Metronome ($bpm BPM)",
                                color = Color.White,
                            )

                            Switch(
                                checked = isMetronomeOn,
                                onCheckedChange = { isMetronomeOn = it && bpm > 0 }
                            )
                        }
                    }
                }

                // --- Speed slider row ---
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Scroll speed: ${(speedMultiplier * 10) / 10}x",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    Slider(
                        value = speedMultiplier,
                        onValueChange = { speedMultiplier = it },
                        valueRange = 0.5f..2f,
                        steps = 5   // 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0
                    )
                }
            }
        }

        // --- Scroll area ---
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(Color.White)
                .verticalScroll(scrollState)
                .padding(12.dp)
        ) {
            var fontSizeSp = 12f
            val density = LocalDensity.current
            val maxWidthPx = with(density) { maxWidth.toPx() }

            val longestLineChars = remember(docText) {
                docText.lineSequence().maxOfOrNull { it.length } ?: 0
            }

            // Estimate font size so longest line fits
            fun computeFitFontSizeSp(): Float {
                if (longestLineChars == 0) return 14f

                // Monospace ≈ 0.6em per character (empirical)
                val estimatedCharWidthPx = 0.6f
                val paddingPx = with(density) { 24.dp.toPx() }

                val usableWidthPx = (maxWidthPx - paddingPx).coerceAtLeast(100f)
                val sp = usableWidthPx / (longestLineChars * estimatedCharWidthPx)

                return sp.coerceIn(8f, 20f)
            }

            // Optional: auto-fit on first load
            LaunchedEffect(docText) {
                fontSizeSp = computeFitFontSizeSp()
            }

            Text(
                text = docText,
                fontFamily = mono,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = fontSizeSp.sp,
                    fontFamily = mono,
                    letterSpacing = 0.sp,
                    color = Color.Black
                ),
                softWrap = false,
                textAlign = TextAlign.Start,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
