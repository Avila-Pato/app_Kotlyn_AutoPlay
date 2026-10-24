package org.example.autoplay.car

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.example.autoplay.net.AacDecoder
import org.example.autoplay.net.CarDiscovery
import org.example.autoplay.net.Connection
import org.example.autoplay.net.DeviceInfo
import org.example.autoplay.net.MediaAction
import org.example.autoplay.mycar.MyCar
import org.example.autoplay.mycar.carFromWire
import org.example.autoplay.mycar.firstCarSource
import org.example.autoplay.mycar.toJson
import org.example.autoplay.net.cleanDeviceName
import org.example.autoplay.net.cleanSayText
import org.example.autoplay.net.Message
import org.example.autoplay.net.Protocol
import org.example.autoplay.net.localIpv4Address
import org.example.autoplay.session.JoinLink
import org.example.autoplay.session.SessionCode
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import javax.inject.Inject
import javax.inject.Singleton

/** A phone sent the right code; the driver must press "Permitir". */
data class JoinRequest(val deviceName: String, val address: String?)

/** The song on the phone that has the music; empty fields when its app shares nothing. */
data class Track(val title: String, val artist: String, val playing: Boolean, val art: Bitmap?)

/** A line a passenger typed on their phone; shown on the radio until it is dismissed or times out. */
data class IncomingMessage(val id: Long, val deviceName: String, val text: String)

/** A phone with a locked volume asked for more; the driver decides. */
data class VolumeRequest(val deviceId: String, val deviceName: String)

enum class CarBluetooth {
    /** This head unit has no Bluetooth that apps can use (some radios handle it in a separate module). */
    Unavailable,
    Off,
    NeedsPermission,
    Ready,
}

data class CarSessionState(
    val running: Boolean = false,
    val carName: String = "",
    val code: String = "",
    val codeExpiresAt: Long = 0,
    val host: String? = null,
    val port: Int = 0,
    val request: JoinRequest? = null,
    /** In priority order. */
    val devices: List<DeviceInfo> = emptyList(),
    val playingId: String? = null,
    val bluetooth: CarBluetooth = CarBluetooth.Unavailable,
    /** The name phones see in their list of paired Bluetooth devices. */
    val bluetoothName: String? = null,
    /** Music volume, 0..100; changed by the phone that plays or on the car screen. */
    val volume: Int = CarServer.DEFAULT_VOLUME,
    val volumeRequest: VolumeRequest? = null,
    val message: IncomingMessage? = null,
    /** The car of the first phone in line that sent one; what "Mi auto" shows on the radio. */
    val myCar: MyCar? = null,
    val track: Track? = null,
) {
    val joinLink: JoinLink? get() = host?.takeIf { code.isNotEmpty() }?.let { JoinLink(it, port, code) }
    val playing: DeviceInfo? get() = devices.find { it.id == playingId }
}

/**
 * Car side of a session: publishes the service, checks codes and keeps several phones connected.
 * The driver orders the phones by priority on the car screen; the first one that is online and
 * sharing its audio plays music.
 */
@Singleton
class CarServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val discovery: CarDiscovery,
    private val player: AudioPlayer,
    private val trustedPhones: TrustedPhones,
) {
    private class Session(val id: String, var name: String, val token: String, val trust: String) {
        /** The car this phone has in view, as it last sent it. */
        var carData: MyCar? = null
        var connection: Connection? = null
        var sharing = false
        var bluetooth = false
        var muted = false
        var volumeLocked = false
        var lastVolumeRequest = 0L
        var lastSay = 0L
        var track: Track? = null
        var artKey = ""
        var resumeTimeout: Job? = null
    }

    private val _state = MutableStateFlow(CarSessionState())
    val state: StateFlow<CarSessionState> = _state.asStateFlow()

    private val lock = Any()

    init {
        player.onFocusChange = { change -> onFocusChange(change) }
    }
    private var scope: CoroutineScope? = null
    private var serverSocket: ServerSocket? = null
    private var bluetoothServer: BluetoothServerSocket? = null
    private var nsdHandle: Closeable? = null
    private var carName = ""

    @Volatile private var code = SessionCode.generate(0)
    private var failedAttempts = 0
    private var pendingDecision: CompletableDeferred<Boolean>? = null

    /** Priority order. */
    private val sessions = mutableListOf<Session>()
    private var rosterChanged = Channel<Unit>(Channel.CONFLATED)

    fun start(name: String) {
        synchronized(lock) {
            if (scope == null) startLocked(name)
        }
    }

    private fun startLocked(name: String) {
        carName = name
        val socket = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(Protocol.DEFAULT_PORT))
            }
        } catch (e: IOException) {
            ServerSocket(0) // default port taken: any free port, discovery and the QR carry it anyway
        }
        serverSocket = socket
        nsdHandle = discovery.register(name, socket.localPort)
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        rosterChanged = Channel(Channel.CONFLATED)

        _state.update { it.copy(running = true, carName = name, host = localIpv4Address(), port = socket.localPort) }
        newCode()
        newScope.launch { acceptLoop(socket) }
        newScope.launch { renewExpiredCodes() }
        newScope.launch { broadcastRoster() }
        player.setGain(gainFor(_state.value.volume))
        startBluetoothLocked()
    }

    /** Called again after the driver grants the Bluetooth permission; also retried every few seconds. */
    fun startBluetooth() = synchronized(lock) { startBluetoothLocked() }

    @SuppressLint("MissingPermission") // checked just below
    private fun startBluetoothLocked() {
        val scope = scope ?: return
        if (bluetoothServer != null) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val status = when {
            adapter == null -> CarBluetooth.Unavailable
            Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ->
                CarBluetooth.NeedsPermission
            !adapter.isEnabled -> CarBluetooth.Off
            else -> CarBluetooth.Ready
        }
        val server = if (status == CarBluetooth.Ready) {
            try {
                adapter!!.listenUsingRfcommWithServiceRecord("AutoPlay", Protocol.BLUETOOTH_UUID)
            } catch (e: Exception) {
                null // IOException or SecurityException: the radio's Bluetooth is not usable by apps
            }
        } else {
            null
        }
        bluetoothServer = server
        val name = if (server != null) runCatching { adapter?.name }.getOrNull() else null
        _state.update {
            it.copy(bluetooth = if (status == CarBluetooth.Ready && server == null) CarBluetooth.Unavailable else status, bluetoothName = name)
        }
        if (server != null) scope.launch { bluetoothAcceptLoop(server) }
    }

    fun stop() {
        val connections = synchronized(lock) {
            scope?.cancel()
            scope = null
            pendingDecision?.complete(false)
            pendingDecision = null
            runCatching { serverSocket?.close() }
            serverSocket = null
            runCatching { bluetoothServer?.close() }
            bluetoothServer = null
            nsdHandle?.close()
            nsdHandle = null
            sessions.mapNotNull { it.connection }.also { sessions.clear() }
        }
        _state.value = CarSessionState()
        player.stop()
        // Sending Bye may touch the network, so never on the caller's (possibly main) thread
        Thread { connections.forEach { it.closeWith(Message.Bye) } }.start()
    }

    /** Answer from the driver to [CarSessionState.request]. */
    fun respond(allow: Boolean) {
        synchronized(lock) { pendingDecision }?.complete(allow)
    }

    /** Moves a phone in the priority list; position 0 is the one playing music. */
    fun move(deviceId: String, position: Int) {
        synchronized(lock) {
            val session = sessions.find { it.id == deviceId } ?: return
            sessions.remove(session)
            sessions.add(position.coerceIn(0, sessions.size), session)
        }
        rosterChanged.trySend(Unit)
    }

    /** The driver renamed the car; phones see it in their next roster and nearby lists after re-announcing. */
    fun rename(name: String) {
        synchronized(lock) {
            if (scope == null) return
            carName = name
            nsdHandle?.close()
            nsdHandle = serverSocket?.let { discovery.register(name, it.localPort) }
        }
        _state.update { it.copy(carName = name) }
        rosterChanged.trySend(Unit)
    }

    fun setVolume(level: Int) {
        val value = level.coerceIn(0, 100)
        player.setGain(gainFor(value))
        _state.update { it.copy(volume = value) }
        rosterChanged.trySend(Unit)
    }

    /** The driver silences a phone: its music stops and the next one in line plays. */
    fun setMuted(deviceId: String, muted: Boolean) {
        synchronized(lock) { sessions.find { it.id == deviceId }?.muted = muted }
        rosterChanged.trySend(Unit)
    }

    /** The driver gives a phone a name of their own; it is kept for when the phone comes back. */
    fun renameDevice(deviceId: String, name: String) {
        val clean = cleanDeviceName(name) ?: return
        synchronized(lock) {
            val session = sessions.find { it.id == deviceId } ?: return
            session.name = clean
            trustedPhones.setAlias(session.trust, clean)
        }
        rosterChanged.trySend(Unit)
    }

    /** A locked phone cannot move the car's volume, only ask for more. */
    fun setVolumeLocked(deviceId: String, locked: Boolean) {
        synchronized(lock) { sessions.find { it.id == deviceId }?.volumeLocked = locked }
        rosterChanged.trySend(Unit)
    }

    /** The driver's answer to [CarSessionState.volumeRequest]; the phone gets a cheeky reply either way. */
    fun answerVolumeRequest(accept: Boolean) {
        val request = _state.value.volumeRequest ?: return
        _state.update { it.copy(volumeRequest = null) }
        if (accept) setVolume(_state.value.volume + VOLUME_REQUEST_STEP)
        val connection = synchronized(lock) { sessions.find { it.id == request.deviceId }?.connection } ?: return
        val reply = (if (accept) ACCEPTED_REPLIES else DENIED_REPLIES).random()
        scope?.launch { runCatching { connection.send(Message.Notice(reply)) } }
    }

    /** ⏮ ⏯ ⏭ from the screen or the steering wheel go to the phone that has the music. */
    fun mediaCommand(action: MediaAction) {
        audioBlocked = false
        resumeAfterBlock = false
        val connection = synchronized(lock) { currentPlayerLocked()?.connection } ?: return
        scope?.launch { runCatching { connection.send(Message.MediaCommand(action)) } }
    }

    /** A passenger typed a line: one at a time per phone, so nobody can flood the radio. */
    private fun onSay(session: Session, raw: String) {
        val text = cleanSayText(raw) ?: return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - session.lastSay < SAY_COOLDOWN_MILLIS) return
            session.lastSay = now
        }
        val incoming = IncomingMessage(now, session.name, text)
        _state.update { it.copy(message = incoming) }
        // Nobody read it: it quietly goes away
        scope?.launch {
            delay(SAY_TIMEOUT_MILLIS)
            _state.update { if (it.message === incoming) it.copy(message = null) else it }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    /**
     * While another app has the audio (the head unit's radio), the phone's music is paused and what it still sends
     * is dropped. It is let through again when the audio is given back, or when someone presses play.
     */
    @Volatile private var audioBlocked = false
    private var resumeAfterBlock = false
    private var blockedAt = 0L

    private fun onFocusChange(change: Int) {
        when (focusAction(change)) {
            FocusAction.None -> Unit
            FocusAction.Pause, FocusAction.Stop -> {
                val stop = focusAction(change) == FocusAction.Stop
                if (audioBlocked && !stop) return
                val wasPlaying = synchronized(lock) { currentPlayerLocked() != null } && _state.value.track?.playing != false
                audioBlocked = true
                resumeAfterBlock = !stop && wasPlaying
                blockedAt = System.currentTimeMillis()
                if (stop) player.stop() else player.pause()
                // The phone's own player stops too, or it would keep going unheard
                if (wasPlaying) mediaCommandUnblocked(MediaAction.PlayPause)
            }
            FocusAction.Resume -> {
                if (!audioBlocked) return
                audioBlocked = false
                if (resumeAfterBlock) mediaCommandUnblocked(MediaAction.PlayPause)
                resumeAfterBlock = false
            }
        }
    }

    private fun mediaCommandUnblocked(action: MediaAction) {
        val connection = synchronized(lock) { currentPlayerLocked()?.connection } ?: return
        scope?.launch { runCatching { connection.send(Message.MediaCommand(action)) } }
    }

    /** The phone sent the car it has in view; a message that is not a car is ignored. */
    private fun onCarData(session: Session, message: Message.CarData) {
        val car = carFromWire(message.json) ?: return
        synchronized(lock) { session.carData = car }
        rosterChanged.trySend(Unit)
    }

    /**
     * The driver edited the car on the radio: the change is applied here at once and sent to the phone that
     * owns it, which keeps it. False when there is no car or [transform] refused (returned null).
     */
    fun editMyCar(transform: (MyCar) -> MyCar?): Boolean {
        val (connection, car) = synchronized(lock) {
            val source = carSourceLocked() ?: return false
            val next = transform(source.carData ?: return false) ?: return false
            source.carData = next
            source.connection to next
        }
        rosterChanged.trySend(Unit)
        if (connection != null) scope?.launch { runCatching { connection.send(Message.CarUpdate(car.toJson().toString())) } }
        return true
    }

    private fun onNowPlaying(session: Session, song: Message.NowPlaying) {
        // The cover only changes with the song, so pause and play reuse the decoded one
        val art = if (song.art == session.artKey) {
            session.track?.art
        } else {
            runCatching { Base64.decode(song.art, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull()
        }
        synchronized(lock) {
            session.artKey = song.art
            session.track = Track(song.title, song.artist, song.playing, art).takeIf { song.title.isNotEmpty() }
        }
        // Someone pressed play on the phone after the radio took the audio: let it through, but ignore what was in flight
        if (audioBlocked && song.playing && System.currentTimeMillis() - blockedAt > BLOCK_GRACE_MILLIS) {
            audioBlocked = false
            resumeAfterBlock = false
        }
        rosterChanged.trySend(Unit)
    }

    private fun onVolumeRequest(session: Session) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (!session.volumeLocked || now - session.lastVolumeRequest < VOLUME_REQUEST_COOLDOWN_MILLIS) return
            session.lastVolumeRequest = now
        }
        val request = VolumeRequest(session.id, session.name)
        _state.update { it.copy(volumeRequest = request) }
        // Nobody answered: the request quietly goes away
        scope?.launch {
            delay(VOLUME_REQUEST_TIMEOUT_MILLIS)
            _state.update { if (it.volumeRequest === request) it.copy(volumeRequest = null) else it }
        }
    }

    /** Loudness feels roughly logarithmic, so a squared curve makes the slider steps feel even. */
    private fun gainFor(level: Int) = (level / 100f).let { it * it }

    /**
     * Disconnects a phone. When the driver does it, [forget] also drops its trust,
     * so it has to join again with a code.
     */
    fun remove(deviceId: String, forget: Boolean = false) {
        val connection = synchronized(lock) {
            val session = sessions.find { it.id == deviceId } ?: return
            sessions.remove(session)
            if (forget) trustedPhones.forget(session.trust)
            session.resumeTimeout?.cancel()
            session.connection.also { session.connection = null }
        }
        rosterChanged.trySend(Unit)
        scope?.launch { connection?.closeWith(Message.Bye) }
    }

    fun newCode() {
        val fresh = SessionCode.generate(System.currentTimeMillis())
        synchronized(lock) {
            code = fresh
            failedAttempts = 0
        }
        _state.update { it.copy(code = fresh.value, codeExpiresAt = fresh.expiresAt) }
    }

    private suspend fun acceptLoop(socket: ServerSocket) {
        val scope = scope ?: return
        while (scope.isActive) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                break // server socket closed by stop()
            }
            scope.launch { handle(Connection(client)) }
        }
    }

    private suspend fun bluetoothAcceptLoop(server: BluetoothServerSocket) {
        val scope = scope ?: return
        while (scope.isActive) {
            val client = try {
                server.accept()
            } catch (e: IOException) {
                break // closed by stop(), or Bluetooth was turned off
            }
            scope.launch { handle(Connection(client)) }
        }
        synchronized(lock) { if (bluetoothServer === server) bluetoothServer = null }
        runCatching { server.close() }
    }

    /** Housekeeping every second: expired codes, a new IP after Wi-Fi comes back, Bluetooth turned on. */
    private suspend fun renewExpiredCodes() {
        var tick = 0
        while (true) {
            delay(1_000)
            if (!code.isValid(System.currentTimeMillis())) newCode()
            val host = localIpv4Address()
            if (host != _state.value.host) _state.update { it.copy(host = host) }
            if (++tick % BLUETOOTH_RETRY_SECONDS == 0) startBluetooth()
        }
    }

    /** Publishes the roster to the UI, the audio player and every phone; conflated so only the latest state is sent. */
    private suspend fun broadcastRoster() {
        var lastPlaying: String? = null
        for (signal in rosterChanged) {
            var track: Track? = null
            var source: Session? = null
            val (devices, playingId, connections) = synchronized(lock) {
                val playing = currentPlayerLocked()
                track = playing?.track
                source = carSourceLocked()
                Triple(
                    sessions.map {
                        DeviceInfo(it.id, it.name, it.connection != null, it.sharing, it.bluetooth, it.muted, it.volumeLocked)
                    },
                    playing?.id,
                    sessions.mapNotNull { it.connection },
                )
            }
            _state.update { it.copy(devices = devices, playingId = playingId, track = track, myCar = source?.carData) }
            val volume = _state.value.volume
            if (playingId == null && lastPlaying != null) player.stop()
            lastPlaying = playingId

            val roster = Message.Roster(devices, playingId, volume, synchronized(lock) { carName })
            connections.forEach { connection ->
                try {
                    connection.send(roster)
                } catch (e: IOException) {
                    connection.close() // its read loop notices and marks the phone as reconnecting
                }
            }
        }
    }

    private suspend fun handle(connection: Connection) {
        connection.readTimeoutMillis = HANDSHAKE_TIMEOUT_MILLIS
        val first = try {
            connection.receive()
        } catch (e: IOException) {
            connection.close()
            return
        }
        when (first) {
            is Message.Hello -> handleHello(connection, first)
            is Message.Resume -> handleResume(connection, first)
            else -> connection.close()
        }
    }

    private suspend fun handleHello(connection: Connection, hello: Message.Hello) {
        if (hello.version != Protocol.VERSION) {
            return connection.closeWith(Message.Rejected("Versión incompatible: actualiza la app en ambos equipos"))
        }
        val trusted = trustedPhones.isTrusted(hello.trust)
        // Bluetooth pairing already proved the phone is in the car; the driver still approves it
        val needsCode = !trusted && !connection.isBluetooth
        if (trusted) {
            // The same phone is back (app restarted) while its old session waits to resume: replace it
            synchronized(lock) { sessions.find { it.trust == hello.trust } }?.let { remove(it.id) }
        }
        val decision = CompletableDeferred<Boolean>()
        val rejection = synchronized(lock) {
            when {
                sessions.size >= MAX_DEVICES -> "El auto ya tiene $MAX_DEVICES celulares conectados"
                trusted -> null
                pendingDecision != null -> "El auto está atendiendo otra solicitud; intenta en unos segundos"
                needsCode && !code.redeem(hello.code, System.currentTimeMillis()) -> {
                    failedAttempts++
                    "Código incorrecto o vencido"
                }
                else -> {
                    pendingDecision = decision
                    null
                }
            }
        }
        if (rejection != null) {
            // Too many wrong guesses burn the code so it cannot be brute-forced
            if (synchronized(lock) { failedAttempts >= MAX_FAILED_ATTEMPTS }) newCode()
            return connection.closeWith(Message.Rejected(rejection))
        }

        if (!trusted) {
            _state.update { it.copy(request = JoinRequest(hello.deviceName, connection.remoteAddress)) }
            val allowed = withTimeoutOrNull(APPROVAL_TIMEOUT_MILLIS) { decision.await() } ?: false
            synchronized(lock) { pendingDecision = null }
            _state.update { it.copy(request = null) }
            if (needsCode) newCode() // the old one was used; the next phone needs a fresh one
            if (!allowed) return connection.closeWith(Message.Rejected("El auto rechazó la conexión"))
        }

        val trust = if (trusted) hello.trust else trustedPhones.trust(hello.deviceName)
        val session = synchronized(lock) {
            val name = trustedPhones.aliasFor(trust) ?: hello.deviceName
            Session(SessionCode.newResumeToken().take(8), name, SessionCode.newResumeToken(), trust)
                .also { sessions.add(it) }
        }
        try {
            connection.send(Message.Accepted(session.token, carName, session.id, trustedPhones.carId, trust))
        } catch (e: IOException) {
            connection.close()
            onConnectionLost(session)
            return
        }
        runSession(session, connection)
    }

    private fun handleResume(connection: Connection, resume: Message.Resume) {
        val session = synchronized(lock) {
            sessions.find { it.token == resume.token }?.also { session ->
                session.resumeTimeout?.cancel()
                // The phone noticed the drop before we did: the old socket is dead, replace it
                session.connection?.close()
                session.connection = null
            }
        } ?: return connection.closeWith(Message.Rejected("La sesión ya no existe; únete con un código nuevo"))
        try {
            connection.send(Message.Accepted(session.token, carName, session.id, trustedPhones.carId, session.trust))
        } catch (e: IOException) {
            connection.close()
            onConnectionLost(session)
            return
        }
        runSession(session, connection)
    }

    private fun runSession(session: Session, connection: Connection) {
        synchronized(lock) {
            session.connection = connection
            session.bluetooth = connection.isBluetooth
        }
        rosterChanged.trySend(Unit)
        connection.readTimeoutMillis = SESSION_READ_TIMEOUT_MILLIS

        var endedByPhone = false
        var decoder: AacDecoder? = null
        try {
            while (true) {
                when (val message = connection.receive()) {
                    is Message.Audio -> if (isPlaying(session) && !audioBlocked) player.write(session.id, message.pcm)
                    is Message.AudioAac -> if (isPlaying(session) && !audioBlocked) {
                        val aac = decoder ?: AacDecoder().also { decoder = it }
                        aac.decode(message.frame) { pcm -> player.write(session.id, pcm) }
                    }
                    Message.Ping -> connection.send(Message.Pong)
                    is Message.Volume -> if (isPlaying(session) && !synchronized(lock) { session.volumeLocked }) setVolume(message.level)
                    Message.VolumeRequest -> onVolumeRequest(session)
                    is Message.NowPlaying -> onNowPlaying(session, message)
                    is Message.CarData -> onCarData(session, message)
                    is Message.Say -> onSay(session, message.text)
                    is Message.AudioState -> {
                        synchronized(lock) { session.sharing = message.sharing }
                        rosterChanged.trySend(Unit)
                    }
                    Message.Bye -> {
                        endedByPhone = true
                        break
                    }
                    else -> Unit
                }
            }
        } catch (e: IOException) {
            // Network drop or timeout: handled below
        } finally {
            connection.close()
            decoder?.release()
        }

        // remove() or a resume may already have taken the connection
        val stillOurs = synchronized(lock) {
            (session.connection === connection).also { if (it) session.connection = null }
        }
        if (!stillOurs) return
        if (endedByPhone) remove(session.id) else onConnectionLost(session)
    }

    private fun isPlaying(session: Session) = synchronized(lock) { currentPlayerLocked() === session }

    private fun currentPlayerLocked() = sessions.firstOrNull { it.connection != null && it.sharing && !it.muted }

    /** First phone in priority order that is online and has sent its car. */
    private fun carSourceLocked(): Session? =
        firstCarSource(sessions.map { if (it.connection != null) it.carData else null })?.let { sessions[it] }

    /** Keeps the phone's place for a while so it can come back with its resume token. */
    private fun onConnectionLost(session: Session) {
        rosterChanged.trySend(Unit)
        val job = scope?.launch {
            delay(RESUME_WINDOW_MILLIS)
            if (synchronized(lock) { session.connection == null }) remove(session.id)
        }
        synchronized(lock) { session.resumeTimeout = job }
    }

    companion object {
        const val DEFAULT_VOLUME = 80

        private const val HANDSHAKE_TIMEOUT_MILLIS = 10_000
        private const val SESSION_READ_TIMEOUT_MILLIS = 10_000
        private const val APPROVAL_TIMEOUT_MILLIS = 60_000L
        private const val RESUME_WINDOW_MILLIS = 90_000L
        private const val MAX_DEVICES = 8
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val BLUETOOTH_RETRY_SECONDS = 5
        private const val VOLUME_REQUEST_STEP = 10
        private const val VOLUME_REQUEST_COOLDOWN_MILLIS = 30_000L
        private const val SAY_COOLDOWN_MILLIS = 5_000L
        private const val SAY_TIMEOUT_MILLIS = 20_000L
        private const val BLOCK_GRACE_MILLIS = 1_500L
        private const val VOLUME_REQUEST_TIMEOUT_MILLIS = 20_000L

        private val ACCEPTED_REPLIES = listOf(
            "¡Concedido! +10 de volumen 🎉",
            "El conductor está de buen humor hoy 😄",
            "Subido. No te acostumbres 😏",
        )
        private val DENIED_REPLIES = listOf(
            "El conductor dijo que ni lo sueñes 😎",
            "Mi auto, mis reglas 🚗",
            "Rechazado por el DJ oficial 🎧",
            "Los oídos del conductor agradecen tu comprensión 🙉",
        )
    }
}
