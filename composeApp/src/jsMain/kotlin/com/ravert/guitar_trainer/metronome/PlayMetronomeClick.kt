package com.ravert.guitar_trainer.metronome

private val audioCtx by lazy {
    js("new (window.AudioContext || window.webkitAudioContext)()")
}

actual fun playMetronomeClick() {
    val osc = audioCtx.createOscillator()
    val gain = audioCtx.createGain()

    // Higher pitch for a metronome tick
    osc.frequency.value = 1500.0
    gain.gain.value = 0.2  // adjust volume

    // Connect oscillator → gain → destination
    osc.connect(gain)
    gain.connect(audioCtx.destination)

    // Start and stop quickly (short click)
    osc.start()
    osc.stop(audioCtx.currentTime + 0.05) // 50ms click
}
