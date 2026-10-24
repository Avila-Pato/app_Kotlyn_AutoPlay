package org.example.autoplay.phone

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.example.autoplay.net.CarDiscovery
import org.example.autoplay.net.DiscoveredCar
import org.example.autoplay.net.Protocol
import org.example.autoplay.session.JoinLink
import javax.inject.Inject

data class PairedDevice(
    val address: String,
    val name: String,
    val looksLikeCar: Boolean,
    /** Linked right now for calls or music: the car is in range with Bluetooth on. */
    val connected: Boolean = false,
    /** When this phone last joined it through AutoPlay, 0 if never. */
    val lastUsed: Long = 0,
) {
    /** Headphones, watches and TVs stay out of the way unless the user asks for them. */
    val relevant: Boolean get() = connected || lastUsed > 0 || looksLikeCar
}

@HiltViewModel
class PhoneViewModel @Inject constructor(
    private val client: PhoneClient,
    private val knownCars: KnownCars,
    private val media: PhoneMedia,
    discovery: CarDiscovery,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val status: StateFlow<PhoneStatus> = client.status
    val room: StateFlow<CarRoom?> = client.room
    val sharingAudio: StateFlow<Boolean> = client.sharingAudio
    val notice: StateFlow<String?> = client.notice
    val myCars: StateFlow<List<KnownCar>> = knownCars.cars

    val cars: StateFlow<List<DiscoveredCar>> =
        discovery.discover().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Compose state, not StateFlow: a text field fed asynchronously makes the keyboard restart while typing
    var code by mutableStateOf("")
        private set

    var manualAddress by mutableStateOf("")
        private set

    /** Null until the Bluetooth permission is granted. */
    private val _pairedDevices = MutableStateFlow<List<PairedDevice>?>(null)
    val pairedDevices: StateFlow<List<PairedDevice>?> = _pairedDevices.asStateFlow()

    init {
        // Opening the app near a car it already knows reconnects without any taps
        val last = knownCars.cars.value.firstOrNull()
        if (last != null && !client.autoConnectDone && client.status.value == PhoneStatus.Disconnected) {
            client.autoConnectDone = true
            connectKnown(last)
        }
    }

    fun connectKnown(car: KnownCar) = connect(car.target, code = "")

    fun forgetCar(car: KnownCar) = knownCars.forget(car.id)

    fun onCodeChange(value: String) {
        code = value.filter(Char::isDigit).take(6)
    }

    fun onManualAddressChange(value: String) {
        manualAddress = value.trim()
    }

    fun connectTo(car: DiscoveredCar) = connect(CarTarget.Wifi(car.host, car.port), code)

    /** No code over Bluetooth: pairing proves the phone is in the car, and the car still asks the driver. */
    fun connectBluetooth(device: PairedDevice) = connect(CarTarget.Bluetooth(device.address, device.name), code = "")

    fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Connected now first, then the cars used most recently, then any other car radio. */
    @SuppressLint("MissingPermission") // checked by hasBluetoothPermission()
    fun refreshPairedDevices() {
        if (!hasBluetoothPermission()) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val bonded = runCatching { adapter?.bondedDevices.orEmpty() }.getOrDefault(emptySet())
        val lastUsed = knownCars.cars.value
            .mapNotNull { car -> (car.target as? CarTarget.Bluetooth)?.let { it.address to car.lastUsed } }
            .toMap()
        _pairedDevices.value = bonded
            .map { device ->
                val major = device.bluetoothClass?.majorDeviceClass
                val isCar = device.bluetoothClass?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                    major == BluetoothClass.Device.Major.AUDIO_VIDEO
                PairedDevice(device.address, device.name ?: device.address, isCar, lastUsed = lastUsed[device.address] ?: 0)
            }
            .sortedWith(pairedOrder)

        // Android cannot tell whether a paired device is in range, but the radio is usually
        // already linked for calls or music when the car is on
        for (profile in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(type: Int, proxy: BluetoothProfile) {
                    val addresses = runCatching { proxy.connectedDevices.map { it.address }.toSet() }.getOrDefault(emptySet())
                    adapter.closeProfileProxy(type, proxy)
                    if (addresses.isEmpty()) return
                    _pairedDevices.update { list ->
                        list?.map { if (it.address in addresses) it.copy(connected = true) else it }?.sortedWith(pairedOrder)
                    }
                }

                override fun onServiceDisconnected(type: Int) {}
            }, profile)
        }
    }

    private val pairedOrder = compareByDescending<PairedDevice> { it.connected }
        .thenByDescending { it.lastUsed }
        .thenByDescending { it.looksLikeCar }
        .thenBy { it.name }

    /** Accepts `192.168.43.1` or `192.168.43.1:47800`. */
    fun connectManually() {
        val host = manualAddress.substringBefore(':')
        val port = manualAddress.substringAfter(':', "").toIntOrNull() ?: Protocol.DEFAULT_PORT
        if (host.isNotEmpty()) connect(CarTarget.Wifi(host, port), code)
    }

    /** Used by the QR scanner; the link already carries host, port and code. */
    fun connectWithLink(link: JoinLink) {
        code = link.code
        connect(CarTarget.Wifi(link.host, link.port), link.code)
    }

    /** The user accepted Android's capture dialog; the service turns it into an audio stream. */
    fun shareAudio(resultCode: Int, data: Intent) = PhoneService.shareAudio(context, resultCode, data)

    fun setCarVolume(level: Int) = client.setCarVolume(level)
    fun askForMoreVolume() = client.askForMoreVolume()
    fun clearNotice() = client.clearNotice()

    fun hasMediaAccess() = media.hasAccess()

    /** Back from the settings: if access was granted, the song starts showing in the car. */
    fun onMediaAccessChanged() = client.onMediaAccessChanged()

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
