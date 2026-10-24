package org.example.autoplay.net

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

/** A phone in the car's session, as every participant sees it. */
data class DeviceInfo(
    val id: String,
    val name: String,
    val online: Boolean,
    /** The phone granted audio capture, so it can play music in the car. */
    val sharing: Boolean,
    val bluetooth: Boolean = false,
    /** The driver silenced this phone: the car skips its music. */
    val muted: Boolean = false,
    /** The driver keeps the volume to themself; this phone can only ask for more. */
    val volumeLocked: Boolean = false,
)

/**
 * Messages exchanged between phone and car, over TCP (Wi-Fi) or RFCOMM (Bluetooth).
 * Wire format of each frame: [type: 1 byte][payload length: 4 bytes][payload].
 */
sealed interface Message {
    /**
     * Phone -> car: first contact. [code] is the 6-digit session code; a phone the car approved before
     * sends its [trust] token instead, and over Bluetooth the pairing itself replaces the code.
     */
    data class Hello(val version: Int, val code: String, val deviceName: String, val trust: String = "") : Message

    /** Phone -> car: reconnect after a dropped connection without asking for approval again. */
    data class Resume(val token: String) : Message

    /**
     * Car -> phone: the connection was approved; [token] allows resuming it later, [deviceId] names this phone,
     * and [trust] lets it join this car ([carId]) next time without a code.
     */
    data class Accepted(
        val token: String,
        val carName: String,
        val deviceId: String,
        val carId: String = "",
        val trust: String = "",
    ) : Message

    data class Rejected(val reason: String) : Message

    /** Car -> phones: connected devices in priority order; the first online one that shares audio plays music. */
    data class Roster(
        val devices: List<DeviceInfo>,
        val playingId: String?,
        val volume: Int = 100,
        val carName: String = "",
    ) : Message

    /** Phone playing music -> car: music volume in the car, 0..100. */
    data class Volume(val level: Int) : Message

    /** Phone with a locked volume -> car: "turn it up, please". */
    data object VolumeRequest : Message

    /** Car -> phone: a short line from the driver, e.g. the answer to a [VolumeRequest]. */
    data class Notice(val text: String) : Message

    /** Car -> phone playing music: a transport button was pressed on the screen or the steering wheel. */
    data class MediaCommand(val action: MediaAction) : Message

    /** Phone -> car: the song its music app is playing; [art] is a small JPEG in Base64, empty if none. */
    data class NowPlaying(val title: String, val artist: String, val playing: Boolean, val art: String = "") : Message

    /** Phone -> car: whether this phone is capturing its audio. */
    data class AudioState(val sharing: Boolean) : Message

    /** Phone -> car: raw PCM described by [AudioSpec]. */
    class Audio(val pcm: ByteArray) : Message {
        override fun equals(other: Any?) = other is Audio && pcm.contentEquals(other.pcm)
        override fun hashCode() = pcm.contentHashCode()
    }

    /** Phone -> car over Bluetooth, which is too slow for PCM: one raw AAC-LC access unit per message. */
    class AudioAac(val frame: ByteArray) : Message {
        override fun equals(other: Any?) = other is AudioAac && frame.contentEquals(other.frame)
        override fun hashCode() = frame.contentHashCode()
    }

    data object Ping : Message
    data object Pong : Message

    /** Either side: the user ended the session. */
    data object Bye : Message
}

enum class MediaAction { PlayPause, Next, Previous }

/** The single audio format used end to end, so no negotiation or resampling is needed. */
object AudioSpec {
    const val SAMPLE_RATE = 48_000
    const val CHANNELS = 2
    const val BYTES_PER_FRAME = CHANNELS * 2 // 16-bit PCM

    /** 20 ms per network frame keeps latency low without flooding the socket with tiny writes. */
    const val CHUNK_BYTES = SAMPLE_RATE / 50 * BYTES_PER_FRAME

    const val AAC_MIME = "audio/mp4a-latm"
    const val AAC_BIT_RATE = 128_000

    /** AudioSpecificConfig for AAC-LC, 48 kHz, stereo; fixed, so it never has to be sent. */
    val AAC_CONFIG = byteArrayOf(0x11, 0x90.toByte())
}

object Protocol {
    const val VERSION = 4
    const val SERVICE_TYPE = "_autoplay._tcp."
    const val DEFAULT_PORT = 47800

    /** RFCOMM service the car listens on when there is no Wi-Fi. */
    val BLUETOOTH_UUID: UUID = UUID.fromString("6f1d2a5e-3c8b-4e1a-9d7f-2b4c6a8e0f13")

    private const val MAX_PAYLOAD = 8 * 1024 * 1024

    private const val HELLO = 1
    private const val RESUME = 2
    private const val ACCEPTED = 3
    private const val REJECTED = 4
    private const val PING = 5
    private const val PONG = 6
    private const val BYE = 7
    private const val ROSTER = 8
    private const val AUDIO_STATE = 9
    private const val AUDIO = 10
    private const val AUDIO_AAC = 11
    private const val VOLUME = 12
    private const val VOLUME_REQUEST = 13
    private const val NOTICE = 14
    private const val MEDIA_COMMAND = 15
    private const val NOW_PLAYING = 16

    fun write(out: DataOutputStream, message: Message) {
        val (type, payload) = when (message) {
            is Message.Audio -> AUDIO to message.pcm
            is Message.AudioAac -> AUDIO_AAC to message.frame
            else -> {
                val (type, fields) = fieldsOf(message)
                type to fields.joinToString("\n") { it.replace('\n', ' ') }.toByteArray(Charsets.UTF_8)
            }
        }
        out.writeByte(type)
        out.writeInt(payload.size)
        out.write(payload)
        out.flush()
    }

    private fun fieldsOf(message: Message): Pair<Int, List<String>> = when (message) {
        is Message.Hello -> HELLO to listOf(message.version.toString(), message.code, message.deviceName, message.trust)
        is Message.Resume -> RESUME to listOf(message.token)
        is Message.Accepted -> ACCEPTED to listOf(message.token, message.carName, message.deviceId, message.carId, message.trust)
        is Message.Rejected -> REJECTED to listOf(message.reason)
        is Message.Roster -> ROSTER to listOf(message.playingId ?: "", message.volume.toString(), message.carName) + message.devices.flatMap {
            listOf(it.id, it.name, flag(it.online), flag(it.sharing), flag(it.bluetooth), flag(it.muted), flag(it.volumeLocked))
        }
        is Message.AudioState -> AUDIO_STATE to listOf(flag(message.sharing))
        is Message.Volume -> VOLUME to listOf(message.level.toString())
        Message.VolumeRequest -> VOLUME_REQUEST to emptyList()
        is Message.Notice -> NOTICE to listOf(message.text)
        is Message.MediaCommand -> MEDIA_COMMAND to listOf(message.action.name)
        is Message.NowPlaying -> NOW_PLAYING to listOf(message.title, message.artist, flag(message.playing), message.art)
        is Message.Audio, is Message.AudioAac -> error("Audio is binary")
        Message.Ping -> PING to emptyList()
        Message.Pong -> PONG to emptyList()
        Message.Bye -> BYE to emptyList()
    }

    fun read(input: DataInputStream): Message {
        val type = input.readUnsignedByte()
        val size = input.readInt()
        if (size !in 0..MAX_PAYLOAD) throw IOException("Invalid frame size $size")
        val payload = ByteArray(size).also(input::readFully)
        if (type == AUDIO) return Message.Audio(payload)
        if (type == AUDIO_AAC) return Message.AudioAac(payload)

        val fields = String(payload, Charsets.UTF_8).split("\n")
        fun field(i: Int) = fields.getOrNull(i) ?: throw IOException("Missing field $i in frame $type")

        return when (type) {
            HELLO -> Message.Hello(field(0).toIntOrNull() ?: 0, field(1), field(2), fields.getOrNull(3).orEmpty())
            RESUME -> Message.Resume(field(0))
            ACCEPTED -> Message.Accepted(field(0), field(1), field(2), fields.getOrNull(3).orEmpty(), fields.getOrNull(4).orEmpty())
            REJECTED -> Message.Rejected(field(0))
            ROSTER -> {
                val devices = fields.drop(3).chunked(7).filter { it.size == 7 }.map { f ->
                    DeviceInfo(f[0], f[1], f[2] == "1", f[3] == "1", f[4] == "1", muted = f[5] == "1", volumeLocked = f[6] == "1")
                }
                Message.Roster(devices, field(0).ifEmpty { null }, field(1).toIntOrNull() ?: 100, field(2))
            }
            AUDIO_STATE -> Message.AudioState(field(0) == "1")
            VOLUME -> Message.Volume(field(0).toIntOrNull()?.coerceIn(0, 100) ?: throw IOException("Bad volume"))
            VOLUME_REQUEST -> Message.VolumeRequest
            NOTICE -> Message.Notice(field(0))
            MEDIA_COMMAND -> Message.MediaCommand(
                MediaAction.entries.find { it.name == field(0) } ?: throw IOException("Unknown media action ${field(0)}"),
            )
            NOW_PLAYING -> Message.NowPlaying(field(0), field(1), field(2) == "1", fields.getOrNull(3).orEmpty())
            PING -> Message.Ping
            PONG -> Message.Pong
            BYE -> Message.Bye
            else -> throw IOException("Unknown frame type $type")
        }
    }

    private fun flag(value: Boolean) = if (value) "1" else "0"
}
