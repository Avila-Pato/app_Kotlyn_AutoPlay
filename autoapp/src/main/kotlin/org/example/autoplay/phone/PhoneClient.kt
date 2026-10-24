package org.example.autoplay.phone

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.example.autoplay.net.AacEncoder
import org.example.autoplay.net.Connection
import org.example.autoplay.net.DeviceInfo
import org.example.autoplay.net.Message
import org.example.autoplay.net.Protocol
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PhoneStatus {
    data object Disconnected : PhoneStatus
    data object Connecting : PhoneStatus

    /** Connected with a valid code; waiting for the driver to press "Permitir". */
    data object WaitingApproval : PhoneStatus

    data class Connected(val carName: String) : PhoneStatus
    data class Reconnecting(val carName: String, val attempt: Int) : PhoneStatus
    data class Failed(val reason: String) : PhoneStatus
}

/** Where the car is: on the local network, or a paired Bluetooth device when there is no Wi-Fi. */
sealed interface CarTarget {
    val label: String

    data class Wifi(val host: String, val port: Int) : CarTarget {
        override val label get() = "$host:$port"
    }

    data class Bluetooth(val address: String, val name: String) : CarTarget {
        override val label get() = name
    }
}

/** The other phones in the car, as the car last reported them. */
data class CarRoom(
    val selfId: String,
    val devices: List<DeviceInfo> = emptyList(),
    val playingId: String? = null,
    val volume: Int = 100,
) {
    val isPlaying: Boolean get() = playingId == selfId
    val self: DeviceInfo? get() = devices.find { it.id == selfId }
    val playing: DeviceInfo? get() = devices.find { it.id == playingId }
}

/** Phone side of a session: joins the car with a code, keeps the connection alive and streams audio. */
@Singleton
class PhoneClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val knownCars: KnownCars,
    private val media: PhoneMedia,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow<PhoneStatus>(PhoneStatus.Disconnected)
    val status: StateFlow<PhoneStatus> = _status.asStateFlow()

    private val _room = MutableStateFlow<CarRoom?>(null)
    val room: StateFlow<CarRoom?> = _room.asStateFlow()

    /** The driver's latest one-liner; the screen clears it after showing it. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _sharingAudio = MutableStateFlow(false)
    val sharingAudio: StateFlow<Boolean> = _sharingAudio.asStateFlow()

    private var job: Job? = null

    /** The phone reconnects to its last car by itself only once per app start, not after the user leaves. */
    var autoConnectDone = false

    @Volatile private var connection: Connection? = null

    /** Created on first use over Bluetooth; guarded by itself because the capture thread and [disconnect] both touch it. */
    private val encoderLock = Any()
    private var encoder: AacEncoder? = null

    fun connect(target: CarTarget, code: String, deviceName: String) {
        disconnect()
        // Set right away so PhoneService, started next, does not see Disconnected and stop itself
        _status.value = PhoneStatus.Connecting
        job = scope.launch { run(target, code, deviceName) }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        val last = connection
        connection = null
        scope.launch { last?.closeWith(Message.Bye) }
        synchronized(encoderLock) {
            encoder?.release()
            encoder = null
        }
        _room.value = null
        _status.value = PhoneStatus.Disconnected
        media.stop()
    }

    /** The user just granted notification access: start sending the song to the car. */
    fun onMediaAccessChanged() {
        if (connection != null) media.start()
    }

    /** Called by [PhoneService] when audio capture starts or stops. */
    fun setSharingAudio(sharing: Boolean) {
        _sharingAudio.value = sharing
        sendInBackground(Message.AudioState(sharing))
    }

    /** Called from the capture thread; audio only goes out while this phone has the music. */
    fun sendAudio(pcm: ByteArray) {
        if (_room.value?.isPlaying != true) return
        val conn = connection ?: return
        try {
            if (conn.isBluetooth) {
                synchronized(encoderLock) {
                    val aac = encoder ?: AacEncoder().also { encoder = it }
                    aac.encode(pcm) { frame -> conn.send(Message.AudioAac(frame)) }
                }
            } else {
                conn.send(Message.Audio(pcm))
            }
        } catch (e: IOException) {
            conn.close() // the session loop notices and reconnects
        }
    }

    /** Only the phone that is playing can change the car's volume; the car ignores the rest. */
    fun setCarVolume(level: Int) {
        val value = level.coerceIn(0, 100)
        _room.value = _room.value?.copy(volume = value) // show it right away; the car's roster confirms it
        sendInBackground(Message.Volume(value))
    }

    /** With the volume locked, all a passenger can do is ask nicely. */
    fun askForMoreVolume() = sendInBackground(Message.VolumeRequest)

    fun clearNotice() {
        _notice.value = null
    }

    private fun sendInBackground(message: Message) {
        val conn = connection ?: return
        scope.launch {
            try {
                conn.send(message)
            } catch (e: IOException) {
                conn.close()
            }
        }
    }

    private suspend fun run(target: CarTarget, code: String, deviceName: String) {
        publish(PhoneStatus.Connecting)
        val first = try {
            open(target)
        } catch (e: Exception) {
            val hint = if (target is CarTarget.Bluetooth) ". ¿Está AutoPlay abierto en el auto en «Soy el Auto»?" else ""
            return publish(PhoneStatus.Failed("No se pudo conectar con ${target.label}$hint"))
        }

        val trust = knownCars.trustFor(target)
        val accepted = try {
            first.send(Message.Hello(Protocol.VERSION, code, deviceName, trust))
            publish(PhoneStatus.WaitingApproval)
            first.readTimeoutMillis = APPROVAL_TIMEOUT_MILLIS
            first.receive()
        } catch (e: IOException) {
            first.close()
            return publish(PhoneStatus.Failed("El auto no respondió"))
        }
        if (accepted !is Message.Accepted) {
            first.close()
            // The car forgot us (the driver removed this phone): drop the stale token so the code is asked next time
            if (trust.isNotEmpty() && accepted is Message.Rejected) knownCars.forgetTrust(trust)
            val reason = (accepted as? Message.Rejected)?.reason ?: "Respuesta inesperada del auto"
            return publish(PhoneStatus.Failed(reason))
        }
        if (accepted.carId.isNotEmpty() && accepted.trust.isNotEmpty()) {
            knownCars.save(KnownCar(accepted.carId, accepted.carName, accepted.trust, target, System.currentTimeMillis()))
        }
        _room.value = CarRoom(accepted.deviceId)

        var current = first
        while (true) {
            publish(PhoneStatus.Connected(accepted.carName))
            if (runSession(current)) {
                connection = null
                _room.value = null
                return publish(PhoneStatus.Failed("El auto cerró la conexión"))
            }
            current = reconnect(target, accepted) ?: run {
                _room.value = null
                return publish(PhoneStatus.Failed("Se perdió la conexión con el auto"))
            }
        }
    }

    /** Returns true if the car ended the session, false if the connection was lost. */
    private suspend fun runSession(conn: Connection): Boolean = coroutineScope {
        conn.readTimeoutMillis = SESSION_READ_TIMEOUT_MILLIS
        val pinger = launch {
            try {
                // After a reconnect the car has to learn again whether this phone shares audio
                conn.send(Message.AudioState(_sharingAudio.value))
                while (isActive) {
                    delay(PING_INTERVAL_MILLIS)
                    conn.send(Message.Ping)
                }
            } catch (e: IOException) {
                conn.close()
            }
        }
        // The song on this phone, for the car screen; also re-sent after a reconnect
        media.start()
        val songs = launch {
            try {
                media.nowPlaying.collect { song -> conn.send(song ?: Message.NowPlaying("", "", playing = false)) }
            } catch (e: IOException) {
                conn.close()
            }
        }
        try {
            while (true) {
                val message = conn.receive()
                if (message == Message.Bye) break
                // Pongs only keep the read timeout from firing
                if (message is Message.Notice) _notice.value = message.text
                if (message is Message.MediaCommand) media.command(message.action)
                if (message is Message.Roster) {
                    _room.value = _room.value?.copy(devices = message.devices, playingId = message.playingId, volume = message.volume)
                    // The driver may rename the car while we are connected
                    val current = _status.value
                    if (message.carName.isNotEmpty() && current is PhoneStatus.Connected && current.carName != message.carName) {
                        _status.value = PhoneStatus.Connected(message.carName)
                    }
                }
            }
            true
        } catch (e: IOException) {
            false
        } finally {
            pinger.cancel()
            songs.cancel()
            conn.close()
        }
    }

    private suspend fun reconnect(target: CarTarget, accepted: Message.Accepted): Connection? {
        for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
            publish(PhoneStatus.Reconnecting(accepted.carName, attempt))
            delay(minOf(attempt * 1_000L, 5_000L))
            val conn = try {
                open(target)
            } catch (e: Exception) {
                continue
            }
            try {
                conn.send(Message.Resume(accepted.token))
                conn.readTimeoutMillis = HANDSHAKE_TIMEOUT_MILLIS
                when (conn.receive()) {
                    is Message.Accepted -> return conn
                    else -> {
                        conn.close()
                        return null // the car dropped the session; retrying will not help
                    }
                }
            } catch (e: IOException) {
                conn.close()
            }
        }
        return null
    }

    @SuppressLint("MissingPermission") // the Bluetooth option is offered only after BLUETOOTH_CONNECT is granted
    private fun open(target: CarTarget): Connection {
        val conn = when (target) {
            is CarTarget.Wifi -> {
                val socket = Socket()
                socket.connect(InetSocketAddress(target.host, target.port), CONNECT_TIMEOUT_MILLIS)
                Connection(socket)
            }
            is CarTarget.Bluetooth -> {
                val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                    ?: throw IOException("No Bluetooth")
                val socket = adapter.getRemoteDevice(target.address).createRfcommSocketToServiceRecord(Protocol.BLUETOOTH_UUID)
                socket.connect()
                Connection(socket)
            }
        }
        return conn.also { connection = it }
    }

    /** Ignores updates from a session the user already cancelled. */
    private suspend fun publish(status: PhoneStatus) {
        currentCoroutineContext().ensureActive()
        _status.value = status
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val HANDSHAKE_TIMEOUT_MILLIS = 10_000
        const val APPROVAL_TIMEOUT_MILLIS = 70_000
        const val SESSION_READ_TIMEOUT_MILLIS = 10_000
        const val PING_INTERVAL_MILLIS = 3_000L
        const val MAX_RECONNECT_ATTEMPTS = 20
    }
}
