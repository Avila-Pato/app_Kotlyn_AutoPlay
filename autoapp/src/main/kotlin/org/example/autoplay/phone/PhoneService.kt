package org.example.autoplay.phone

import android.Manifest
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.example.autoplay.SessionNotification
import javax.inject.Inject

/**
 * Keeps the phone connected to the car while the user is in YouTube, Spotify or with the screen off,
 * and sends whatever is playing on the phone to the car.
 */
@AndroidEntryPoint
class PhoneService : Service() {
    @Inject lateinit var client: PhoneClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var projection: MediaProjection? = null
    private var capture: AudioCapture? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var notificationText = "Conectando con el auto…" to ""
    private var playingInCar = false
    private lateinit var volume: CarVolumeRouter

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground(withProjection = false)
        // Without these, the phone drops Wi-Fi or stops the CPU a few minutes after the screen turns off
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AutoPlay:session")
            .apply { acquire() }
        @Suppress("DEPRECATION")
        wifiLock = getSystemService(WifiManager::class.java)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AutoPlay:session")
            .apply { acquire() }

        volume = CarVolumeRouter(this, client)
        scope.launch {
            combine(client.status, client.room, client.sharingAudio) { status, room, sharing ->
                status is PhoneStatus.Connected && sharing && room?.isPlaying == true
            }.distinctUntilChanged().collect { playing ->
                playingInCar = playing
                if (playing) volume.start() else volume.stop()
                SessionNotification.update(this@PhoneService, notification())
            }
        }
        scope.launch {
            client.room.map { it?.volume }.filterNotNull().distinctUntilChanged().collect(volume::onCarVolumeChanged)
        }
        scope.launch {
            combine(client.status, client.room, client.sharingAudio, ::describe)
                .distinctUntilChanged()
                .collect { text ->
                    if (text == null) {
                        stopSelf()
                    } else {
                        notificationText = text
                        SessionNotification.update(this@PhoneService, notification())
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> client.disconnect()
            ACTION_VOLUME_UP -> volume.step(1)
            ACTION_VOLUME_DOWN -> volume.step(-1)
            ACTION_SHARE_AUDIO -> {
                val data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java)
                if (data != null) startCapture(intent.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED), data)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        volume.stop()
        stopCapture()
        scope.cancel()
        wakeLock?.release()
        wifiLock?.release()
        super.onDestroy()
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        if (Build.VERSION.SDK_INT < 29 || capture != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        // Android 14+ requires the mediaProjection type to be active before the projection is created
        goForeground(withProjection = true)
        val newProjection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
        if (newProjection == null) {
            goForeground(withProjection = false)
            return
        }
        newProjection.registerCallback(
            object : MediaProjection.Callback() {
                // The user tapped "stop sharing" in the status bar
                override fun onStop() = stopCapture()
            },
            Handler(Looper.getMainLooper()),
        )
        projection = newProjection
        capture = AudioCapture(newProjection, client::sendAudio).also { it.start() }
        client.setSharingAudio(true)
    }

    private fun stopCapture() {
        val current = capture ?: return
        capture = null
        current.stop()
        projection?.stop()
        projection = null
        client.setSharingAudio(false)
        goForeground(withProjection = false)
    }

    private fun goForeground(withProjection: Boolean) {
        var type = 0
        if (Build.VERSION.SDK_INT >= 29) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (withProjection) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }
        ServiceCompat.startForeground(this, SessionNotification.ID, notification(), type)
    }

    private fun notification() = SessionNotification.build(
        this,
        title = notificationText.first,
        text = notificationText.second,
        stopIntent = Intent(this, PhoneService::class.java).setAction(ACTION_STOP),
        extraActions = if (playingInCar) {
            listOf(
                "Vol −" to Intent(this, PhoneService::class.java).setAction(ACTION_VOLUME_DOWN),
                "Vol +" to Intent(this, PhoneService::class.java).setAction(ACTION_VOLUME_UP),
            )
        } else {
            emptyList()
        },
    )

    /** Title and text for the notification, or null when the session is over and the service should stop. */
    private fun describe(status: PhoneStatus, room: CarRoom?, sharing: Boolean): Pair<String, String>? = when (status) {
        PhoneStatus.Disconnected, is PhoneStatus.Failed -> null
        PhoneStatus.Connecting -> "Conectando con el auto…" to ""
        PhoneStatus.WaitingApproval -> "Esperando que el auto acepte…" to "Pulsa «Permitir» en la pantalla del auto"
        is PhoneStatus.Reconnecting -> "Reconectando con ${status.carName}…" to "Se cortó la señal; volviendo a conectar"
        is PhoneStatus.Connected -> "Estás conectado a ${status.carName}" to when {
            room?.isPlaying == true -> "Tu música suena en el auto (volumen ${room.volume}%). Las teclas de volumen controlan el auto."
            !sharing -> "Abre AutoPlay y toca «Activar audio» para poner música"
            room?.playing != null -> "Ahora suena la música de ${room.playing?.name}"
            else -> "Conectado"
        }
    }

    companion object {
        private const val ACTION_STOP = "org.example.autoplay.phone.STOP"
        private const val ACTION_SHARE_AUDIO = "org.example.autoplay.phone.SHARE_AUDIO"
        private const val ACTION_VOLUME_UP = "org.example.autoplay.phone.VOLUME_UP"
        private const val ACTION_VOLUME_DOWN = "org.example.autoplay.phone.VOLUME_DOWN"
        private const val EXTRA_RESULT = "result"
        private const val EXTRA_DATA = "data"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PhoneService::class.java))
        }

        /** Hands the user's screen-capture consent to the service, which uses it only for audio. */
        fun shareAudio(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, PhoneService::class.java)
                .setAction(ACTION_SHARE_AUDIO)
                .putExtra(EXTRA_RESULT, resultCode)
                .putExtra(EXTRA_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
