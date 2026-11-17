package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class ChessSoundEffect {
    MOVE,
    CAPTURE,
    CASTLE,
    CHECK,
    GAME_OVER
}

/**
 * Offline synthesized wooden chess sound engine using AudioTrack.
 * Fails gracefully in headless/Robolectric test environments.
 */
class ChessSoundManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var isSoundEnabled: Boolean = true

    fun play(effect: ChessSoundEffect) {
        if (!isSoundEnabled) return
        scope.launch {
            runCatching {
                when (effect) {
                    ChessSoundEffect.MOVE -> playTone(freqHz = 280.0, durationMs = 55, decay = 42.0, volume = 0.45f)
                    ChessSoundEffect.CAPTURE -> {
                        playTone(freqHz = 360.0, durationMs = 45, decay = 38.0, volume = 0.55f)
                        playTone(freqHz = 190.0, durationMs = 60, decay = 35.0, volume = 0.5f)
                    }
                    ChessSoundEffect.CASTLE -> {
                        playTone(freqHz = 260.0, durationMs = 45, decay = 40.0, volume = 0.45f)
                        Thread.sleep(65)
                        playTone(freqHz = 320.0, durationMs = 50, decay = 40.0, volume = 0.45f)
                    }
                    ChessSoundEffect.CHECK -> playTone(freqHz = 660.0, durationMs = 130, decay = 18.0, volume = 0.48f)
                    ChessSoundEffect.GAME_OVER -> {
                        playTone(freqHz = 523.25, durationMs = 110, decay = 14.0, volume = 0.45f)
                        Thread.sleep(90)
                        playTone(freqHz = 659.25, durationMs = 140, decay = 12.0, volume = 0.45f)
                        Thread.sleep(100)
                        playTone(freqHz = 783.99, durationMs = 220, decay = 8.0, volume = 0.48f)
                    }
                }
            }
        }
    }

    private fun playTone(freqHz: Double, durationMs: Int, decay: Double, volume: Float) {
        val sampleRate = 22050
        val sampleCount = (sampleRate * durationMs) / 1000
        if (sampleCount <= 0) return

        val samples = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            val t = i.toDouble() / sampleRate
            val env = exp(-decay * t)
            val wave = sin(2.0 * PI * freqHz * t) + 0.35 * sin(2.0 * PI * (freqHz * 2.1) * t)
            samples[i] = (wave * env * volume * Short.MAX_VALUE * 0.65).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(maxOf(minBuf, samples.size * 2))
            .build()

        try {
            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep(durationMs.toLong() + 20L)
        } finally {
            runCatching {
                track.stop()
                track.release()
            }
        }
    }
}
