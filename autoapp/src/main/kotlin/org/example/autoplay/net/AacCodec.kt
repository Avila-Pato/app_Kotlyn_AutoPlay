package org.example.autoplay.net

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Compresses the phone's PCM to AAC for Bluetooth, which cannot carry ~1.5 Mbit/s of raw audio.
 * Uses the platform codec in synchronous mode; not thread-safe, callers serialize access.
 */
class AacEncoder {
    private val codec = MediaCodec.createEncoderByType(AudioSpec.AAC_MIME).apply {
        val format = MediaFormat.createAudioFormat(AudioSpec.AAC_MIME, AudioSpec.SAMPLE_RATE, AudioSpec.CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AudioSpec.AAC_BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AudioSpec.CHUNK_BYTES * 2)
        }
        configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        start()
    }
    private val info = MediaCodec.BufferInfo()
    private var framesQueued = 0L

    /** Feeds PCM and hands every finished AAC access unit to [onFrame]. */
    fun encode(pcm: ByteArray, onFrame: (ByteArray) -> Unit) {
        val index = codec.dequeueInputBuffer(TIMEOUT_US)
        if (index >= 0) {
            val buffer = codec.getInputBuffer(index)!!
            buffer.clear()
            val size = minOf(pcm.size, buffer.remaining())
            buffer.put(pcm, 0, size)
            val timeUs = framesQueued * 1_000_000 / AudioSpec.SAMPLE_RATE
            framesQueued += size / AudioSpec.BYTES_PER_FRAME
            codec.queueInputBuffer(index, 0, size, timeUs, 0)
        }
        drain(codec, info, onFrame)
    }

    fun release() {
        runCatching { codec.stop() }
        codec.release()
    }
}

/** Turns the AAC frames from a Bluetooth phone back into PCM for the car's speakers. */
class AacDecoder {
    private val codec = MediaCodec.createDecoderByType(AudioSpec.AAC_MIME).apply {
        val format = MediaFormat.createAudioFormat(AudioSpec.AAC_MIME, AudioSpec.SAMPLE_RATE, AudioSpec.CHANNELS).apply {
            setInteger(MediaFormat.KEY_IS_ADTS, 0)
            setByteBuffer("csd-0", ByteBuffer.wrap(AudioSpec.AAC_CONFIG))
        }
        configure(format, null, null, 0)
        start()
    }
    private val info = MediaCodec.BufferInfo()

    fun decode(frame: ByteArray, onPcm: (ByteArray) -> Unit) {
        val index = codec.dequeueInputBuffer(TIMEOUT_US)
        if (index >= 0) {
            codec.getInputBuffer(index)!!.apply {
                clear()
                put(frame, 0, minOf(frame.size, remaining()))
            }
            codec.queueInputBuffer(index, 0, frame.size, 0, 0)
        }
        drain(codec, info, onPcm)
    }

    fun release() {
        runCatching { codec.stop() }
        codec.release()
    }
}

private const val TIMEOUT_US = 10_000L

/** Copies out every buffer the codec has ready; config buffers are skipped because both ends know the format. */
private fun drain(codec: MediaCodec, info: MediaCodec.BufferInfo, onOutput: (ByteArray) -> Unit) {
    while (true) {
        val index = codec.dequeueOutputBuffer(info, 0)
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
        if (index < 0) return
        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
            val out = ByteArray(info.size)
            codec.getOutputBuffer(index)!!.apply {
                position(info.offset)
                get(out)
            }
            onOutput(out)
        }
        codec.releaseOutputBuffer(index, false)
    }
}
