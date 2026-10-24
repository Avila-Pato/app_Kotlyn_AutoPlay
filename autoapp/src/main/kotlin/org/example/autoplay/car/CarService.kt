package org.example.autoplay.car

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.example.autoplay.SessionNotification
import org.example.autoplay.net.MediaAction
import javax.inject.Inject

/** Keeps the car's server and audio running while the driver uses other apps (maps, radio…). */
@AndroidEntryPoint
class CarService : Service() {
    @Inject lateinit var server: CarServer
    @Inject lateinit var announcer: Announcer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Makes AutoPlay the radio's media player, so the steering wheel buttons and the
     * radio's own ⏮ ⏯ ⏭ (and its now-playing widget) reach the phone that has the music.
     */
    private lateinit var mediaSession: MediaSession

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            SessionNotification.ID,
            notification(notificationText(server.state.value)),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        mediaSession = MediaSession(this, "AutoPlay").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = server.mediaCommand(MediaAction.PlayPause)
                override fun onPause() = server.mediaCommand(MediaAction.PlayPause)
                override fun onSkipToNext() = server.mediaCommand(MediaAction.Next)
                override fun onSkipToPrevious() = server.mediaCommand(MediaAction.Previous)
            })
            isActive = true
        }
        scope.launch {
            server.state.map { Triple(it.track, it.playing?.name, it.playingId != null) }.distinctUntilChanged().collect { (track, phone, active) ->
                updateMediaSession(track, phone, active)
            }
        }
        // Out loud, so the driver hears it even with a map open on top
        scope.launch {
            server.state.map { it.volumeRequest }.distinctUntilChanged().collect { request ->
                if (request != null) announcer.speak(volumeRequestMessage(request.deviceName))
            }
        }
        // A line typed on a phone is read aloud too, so the driver does not have to look
        scope.launch {
            server.state.map { it.message }.distinctUntilChanged().collect { message ->
                if (message != null) announcer.speak(spokenMessage(message.deviceName, message.text))
            }
        }
        scope.launch {
            server.state.map { it.running to "${it.carName}\n${notificationText(it)}" }.distinctUntilChanged().collect { (running, text) ->
                if (running) SessionNotification.update(this@CarService, notification(text.substringAfter('\n'))) else stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> server.stop()
            // Buttons of the floating mini player in navigation mode
            ACTION_PLAY_PAUSE -> server.mediaCommand(MediaAction.PlayPause)
            ACTION_NEXT -> server.mediaCommand(MediaAction.Next)
            ACTION_PREVIOUS -> server.mediaCommand(MediaAction.Previous)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        announcer.release()
        mediaSession.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun updateMediaSession(track: Track?, phone: String?, active: Boolean) {
        val playing = active && track?.playing != false
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                )
                .build(),
        )
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track?.title ?: phone ?: "AutoPlay")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track?.artist ?: phone.orEmpty())
                .apply { track?.art?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build(),
        )
    }

    private fun notification(text: String) = SessionNotification.build(
        this,
        title = server.state.value.carName.ifEmpty { "AutoPlay" },
        text = text,
        stopIntent = Intent(this, CarService::class.java).setAction(ACTION_STOP),
    )

    private fun notificationText(state: CarSessionState): String {
        val online = state.devices.count { it.online }
        val playing = state.playing?.name
        return when {
            online == 0 -> "Esperando celulares"
            playing != null -> "Sonando: $playing"
            else -> "$online conectado${if (online == 1) "" else "s"}"
        }
    }

    companion object {
        private const val ACTION_STOP = "org.example.autoplay.car.STOP"
        const val ACTION_PLAY_PAUSE = "org.example.autoplay.car.PLAY_PAUSE"
        const val ACTION_NEXT = "org.example.autoplay.car.NEXT"
        const val ACTION_PREVIOUS = "org.example.autoplay.car.PREVIOUS"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CarService::class.java))
        }
    }
}
