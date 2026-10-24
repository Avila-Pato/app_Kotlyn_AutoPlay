package org.example.autoplay.car

import android.content.Context
import android.os.Build
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

    init {
        viewModelScope.launch(Dispatchers.IO) {
            server.start(name = "Auto ${Build.MODEL}")
            // The service outlives this screen, so music keeps playing when the driver opens another app
            CarService.start(context)
        }
    }

    fun allow() = server.respond(allow = true)
    fun deny() = server.respond(allow = false)
    fun newCode() = server.newCode()
    fun startBluetooth() = server.startBluetooth()
    fun setVolume(level: Int) = server.setVolume(level)
    fun move(deviceId: String, position: Int) = server.move(deviceId, position)
    fun remove(deviceId: String) = server.remove(deviceId)

    /** Turns the car side off; leaving the screen with Home keeps it running. */
    fun stop() = server.stop()
}
