package org.example.autoplay.car

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CarViewModel @Inject constructor(
    private val server: CarServer,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val state: StateFlow<CarSessionState> = server.state
    private val prefs = context.getSharedPreferences("car", Context.MODE_PRIVATE)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            server.start(name = prefs.getString(KEY_NAME, null) ?: defaultName())
            // The service outlives this screen, so music keeps playing when the driver opens another app
            CarService.start(context)
        }
    }

    fun allow() = server.respond(allow = true)
    fun deny() = server.respond(allow = false)
    fun newCode() = server.newCode()
    fun startBluetooth() = server.startBluetooth()
    fun setVolume(level: Int) = server.setVolume(level)

    fun rename(name: String) {
        val clean = name.trim().take(MAX_NAME_LENGTH)
        if (clean.isEmpty()) return
        prefs.edit().putString(KEY_NAME, clean).apply()
        server.rename(clean)
    }
    fun move(deviceId: String, position: Int) = server.move(deviceId, position)
    fun setMuted(deviceId: String, muted: Boolean) = server.setMuted(deviceId, muted)
    fun setVolumeLocked(deviceId: String, locked: Boolean) = server.setVolumeLocked(deviceId, locked)
    fun answerVolumeRequest(accept: Boolean) = server.answerVolumeRequest(accept)
    /** Removing a phone from the car screen also forgets it, so it needs a code to come back. */
    fun remove(deviceId: String) = server.remove(deviceId, forget = true)

    /** Turns the car side off; leaving the screen with Home keeps it running. */
    fun stop() = server.stop()

    /**
     * Head units often report the chip's codename ("sp7731e_1h10_native") as their name;
     * that means nothing to passengers, so it falls back to a plain name the driver can change.
     */
    private fun defaultName(): String {
        val name = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.trim().orEmpty()
        val codenames = setOf(Build.MODEL, Build.DEVICE, Build.PRODUCT, Build.BOARD, Build.HARDWARE)
        val readable = name.isNotEmpty() && '_' !in name && codenames.none { it.equals(name, ignoreCase = true) }
        return if (readable) name else "Mi auto"
    }

    private companion object {
        const val KEY_NAME = "name"
        const val MAX_NAME_LENGTH = 30
    }
}
