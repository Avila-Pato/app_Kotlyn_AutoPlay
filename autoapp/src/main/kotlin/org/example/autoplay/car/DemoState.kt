package org.example.autoplay.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import org.example.autoplay.net.DeviceInfo

/**
 * The car screen filled with made-up phones and a song, to see the dashboard without connecting
 * anything. Nothing here touches the real session; buttons on these phones find nobody and do nothing.
 */
fun demoState(live: CarSessionState): CarSessionState = live.copy(
    devices = listOf(
        DeviceInfo("demo-1", "Galaxy de Ana", online = true, sharing = true),
        DeviceInfo("demo-2", "iPhone de Tomás", online = true, sharing = true, volumeLocked = true),
        DeviceInfo("demo-3", "Pixel de Sofi", online = true, sharing = true, muted = true, bluetooth = true),
        DeviceInfo("demo-4", "Moto G", online = false, sharing = false),
    ),
    playingId = "demo-1",
    track = Track("Mood (Remix)", "24kGoldn, Justin Bieber, J Balvin", playing = true, art = demoCover),
    volumeRequest = VolumeRequest("demo-2", "iPhone de Tomás"),
)

/** A plain gradient standing in for an album cover. */
private val demoCover: Bitmap by lazy {
    Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).also { bitmap ->
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 256f, 256f, 0xFFFF7A45.toInt(), 0xFF7B2FF7.toInt(), Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawRect(0f, 0f, 256f, 256f, paint)
    }
}
