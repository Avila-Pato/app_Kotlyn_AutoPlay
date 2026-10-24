package org.example.autoplay.phone

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import androidx.annotation.RequiresApi
import org.example.autoplay.net.AudioSpec

/**
 * Captures what other apps (YouTube, Spotify…) are playing on this phone.
 * Apps that opt out of playback capture are silent here; that is an Android rule, not a bug.
 */
@RequiresApi(29)
class AudioCapture(projection: MediaProjection, private val onPcm: (ByteArray) -> Unit) {
    private val record: AudioRecord
    @Volatile private var running = true
    private val thread = Thread(::loop, "audio-capture")

    init {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(AudioSpec.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        record = buildRecord(config, format)
    }

    @SuppressLint("MissingPermission") // PhoneService checks RECORD_AUDIO before creating this
    private fun buildRecord(config: AudioPlaybackCaptureConfiguration, format: AudioFormat): AudioRecord =
        AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(AudioSpec.CHUNK_BYTES * 8)
            .setAudioPlaybackCaptureConfig(config)
            .build()

    fun start() {
        record.startRecording()
        thread.start()
    }

    fun stop() {
        running = false
        thread.join(500)
        runCatching { record.stop() }
        record.release()
    }

    private fun loop() {
        val chunk = ByteArray(AudioSpec.CHUNK_BYTES)
        while (running) {
            val read = record.read(chunk, 0, chunk.size)
            if (read > 0) onPcm(chunk.copyOf(read))
            if (read < 0) break
        }
    }
}
