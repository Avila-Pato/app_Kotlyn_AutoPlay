package org.example.autoplay.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import org.example.autoplay.net.DeviceInfo
import org.example.autoplay.net.cleanDeviceName

/** The made-up phones of the demo, in priority order. */
fun demoDevices(): List<DeviceInfo> = listOf(
    DeviceInfo("demo-1", "Galaxy de Ana", online = true, sharing = true),
    DeviceInfo("demo-2", "iPhone de Tomás", online = true, sharing = true, volumeLocked = true),
    DeviceInfo("demo-3", "Pixel de Sofi", online = true, sharing = true, muted = true, bluetooth = true),
    DeviceInfo("demo-4", "Moto G", online = false, sharing = false),
)

/** Same rule as the real car: the first phone that is online, shares audio and is not muted plays. */
fun demoPlayingId(devices: List<DeviceInfo>): String? = devices.firstOrNull { it.online && it.sharing && !it.muted }?.id

fun demoMute(devices: List<DeviceInfo>, id: String, muted: Boolean): List<DeviceInfo> =
    devices.map { if (it.id == id) it.copy(muted = muted) else it }

fun demoLock(devices: List<DeviceInfo>, id: String, locked: Boolean): List<DeviceInfo> =
    devices.map { if (it.id == id) it.copy(volumeLocked = locked) else it }

/** Moves a phone to [position] in the priority list, like the real car does. */
fun demoMove(devices: List<DeviceInfo>, id: String, position: Int): List<DeviceInfo> {
    val device = devices.find { it.id == id } ?: return devices
    val rest = devices - device
    return rest.toMutableList().apply { add(position.coerceIn(0, rest.size), device) }
}

fun demoRename(devices: List<DeviceInfo>, id: String, name: String): List<DeviceInfo> {
    val clean = cleanDeviceName(name) ?: return devices
    return devices.map { if (it.id == id) it.copy(name = clean) else it }
}

fun demoRemove(devices: List<DeviceInfo>, id: String): List<DeviceInfo> = devices.filter { it.id != id }

/**
 * The car screen filled with made-up phones and a song, to see the dashboard without connecting
 * anything. Nothing here touches the real session; the screen applies the buttons to [devices] itself.
 */
fun demoState(live: CarSessionState, devices: List<DeviceInfo> = demoDevices()): CarSessionState {
    val playingId = demoPlayingId(devices)
    return live.copy(
        devices = devices,
        playingId = playingId,
        track = if (playingId != null) Track("Mood (Remix)", "24kGoldn, Justin Bieber, J Balvin", playing = true, art = demoCover) else null,
        volumeRequest = demoVolumeRequest(devices),
    )
}

/** The phone with the locked volume keeps asking until the driver answers, unlocks it or disconnects it. */
fun demoVolumeRequest(devices: List<DeviceInfo>): VolumeRequest? =
    VolumeRequest("demo-2", "iPhone de Tomás").takeIf { devices.any { it.id == "demo-2" && it.online && it.volumeLocked } }

/** A plain gradient standing in for an album cover. */
private val demoCover: Bitmap by lazy {
    Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).also { bitmap ->
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 256f, 256f, 0xFFFF7A45.toInt(), 0xFF7B2FF7.toInt(), Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawRect(0f, 0f, 256f, 256f, paint)
    }
}
