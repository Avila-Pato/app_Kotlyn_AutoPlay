package org.example.autoplay.phone

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import org.example.autoplay.net.CarDiscovery
import org.example.autoplay.net.DiscoveredCar
import org.example.autoplay.net.Protocol
import org.example.autoplay.session.JoinLink
import javax.inject.Inject

data class PairedDevice(val address: String, val name: String, val looksLikeCar: Boolean)

@HiltViewModel
class PhoneViewModel @Inject constructor(
    private val client: PhoneClient,
    discovery: CarDiscovery,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val status: StateFlow<PhoneStatus> = client.status
    val room: StateFlow<CarRoom?> = client.room
    val sharingAudio: StateFlow<Boolean> = client.sharingAudio

    val cars: StateFlow<List<DiscoveredCar>> =
        discovery.discover().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _manualAddress = MutableStateFlow("")
    val manualAddress: StateFlow<String> = _manualAddress.asStateFlow()

    /** Null until the Bluetooth permission is granted. */
    private val _pairedDevices = MutableStateFlow<List<PairedDevice>?>(null)
    val pairedDevices: StateFlow<List<PairedDevice>?> = _pairedDevices.asStateFlow()

    fun onCodeChange(value: String) {
        _code.value = value.filter(Char::isDigit).take(6)
    }

    fun onManualAddressChange(value: String) {
        _manualAddress.value = value.trim()
    }

    fun connectTo(car: DiscoveredCar) = connect(CarTarget.Wifi(car.host, car.port), _code.value)

    fun connectBluetooth(device: PairedDevice) = connect(CarTarget.Bluetooth(device.address, device.name), _code.value)

    fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Car radios first: the head unit is usually already paired with the phone for calls. */
    @SuppressLint("MissingPermission") // checked by hasBluetoothPermission()
    fun refreshPairedDevices() {
        if (!hasBluetoothPermission()) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val bonded = runCatching { adapter?.bondedDevices.orEmpty() }.getOrDefault(emptySet())
        _pairedDevices.value = bonded
            .map { device ->
                val major = device.bluetoothClass?.majorDeviceClass
                val isCar = device.bluetoothClass?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                    major == BluetoothClass.Device.Major.AUDIO_VIDEO
                PairedDevice(device.address, device.name ?: device.address, isCar)
            }
            .sortedWith(compareByDescending<PairedDevice> { it.looksLikeCar }.thenBy { it.name })
    }

    /** Accepts `192.168.43.1` or `192.168.43.1:47800`. */
    fun connectManually() {
        val host = _manualAddress.value.substringBefore(':')
        val port = _manualAddress.value.substringAfter(':', "").toIntOrNull() ?: Protocol.DEFAULT_PORT
        if (host.isNotEmpty()) connect(CarTarget.Wifi(host, port), _code.value)
    }

    /** Used by the QR scanner; the link already carries host, port and code. */
    fun connectWithLink(link: JoinLink) {
        _code.value = link.code
        connect(CarTarget.Wifi(link.host, link.port), link.code)
    }

    /** The user accepted Android's capture dialog; the service turns it into an audio stream. */
    fun shareAudio(resultCode: Int, data: Intent) = PhoneService.shareAudio(context, resultCode, data)

    fun setCarVolume(level: Int) = client.setCarVolume(level)

    fun disconnect() = client.disconnect()

    private fun connect(target: CarTarget, code: String) {
        client.connect(target, code, deviceName())
        // Keeps the connection alive while the user is in YouTube or Spotify
        PhoneService.start(context)
    }

    /** The name the owner gave the phone ("Galaxy de Ana") tells people apart better than the model. */
    private fun deviceName(): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER.replaceFirstChar(Char::uppercase)} ${Build.MODEL}"
}
