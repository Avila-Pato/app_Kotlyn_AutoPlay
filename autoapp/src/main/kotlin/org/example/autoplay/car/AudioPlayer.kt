package org.example.autoplay.car

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import org.example.autoplay.net.AudioSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Plays the PCM stream of the phone that currently has the music on the car's speakers. */
@Singleton
class AudioPlayer @Inject constructor(@ApplicationContext context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change -> onFocusChange?.invoke(change) }
        .build()

    /** Called when another app takes or gives back the audio, so the car can pause the phone's music. */
    @Volatile var onFocusChange: ((Int) -> Unit)? = null

    private var track: AudioTrack? = null
    private var sourceId: String? = null
    private var gain = 1f
    private var ducked = false

    /** Music volume chosen from the phone or the car screen, on top of the radio's own volume knob. */
    @Synchronized
    fun setGain(value: Float) {
        gain = value.coerceIn(0f, 1f)
        track?.setVolume(effectiveGain())
    }

    /** Lowers the music while an alert is spoken over it. */
    @Synchronized
    fun duck(on: Boolean) {
        ducked = on
        track?.setVolume(effectiveGain())
    }

    private fun effectiveGain() = if (ducked) gain * DUCKED_FACTOR else gain

    /** Blocks while the track buffer is full, which paces the sender through TCP back-pressure. */
    @Synchronized
    fun write(fromId: String, pcm: ByteArray) {
        if (fromId != sourceId) {
            // A different phone took over: drop what is left of the previous one
            track?.run { pause(); flush() }
            sourceId = fromId
        }
        val current = track ?: createTrack().also {
            it.setVolume(effectiveGain())
            track = it
            audioManager.requestAudioFocus(focusRequest)
        }
        if (current.playState != AudioTrack.PLAYSTATE_PLAYING) current.play()
        current.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
    }

    /** Holds the music without letting go of the audio, so it is heard again when the other app gives it back. */
    @Synchronized
    fun pause() {
        track?.run {
            pause()
            flush()
        }
    }

    /** Silences the car when no phone is playing anymore. */
    @Synchronized
    fun stop() {
        track?.run {
            pause()
            flush()
            release()
        }
        track = null
        sourceId = null
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun createTrack(): AudioTrack {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(AudioSpec.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(
            AudioSpec.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        // ~200 ms absorbs Wi-Fi jitter without a noticeable delay
        val buffer = maxOf(minBuffer, AudioSpec.SAMPLE_RATE / 5 * AudioSpec.BYTES_PER_FRAME)
        return AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(buffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private companion object {
        const val DUCKED_FACTOR = 0.3f
    }
}
