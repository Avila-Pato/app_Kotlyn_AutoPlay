package org.example.autoplay.car

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import javax.inject.Inject

/** Keeps the car's server and audio running while the driver uses other apps (maps, radio…). */
@AndroidEntryPoint
class CarService : Service() {
    @Inject lateinit var server: CarServer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            SessionNotification.ID,
            notification(notificationText(server.state.value)),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        scope.launch {
            server.state.map { it.running to notificationText(it) }.distinctUntilChanged().collect { (running, text) ->
                if (running) SessionNotification.update(this@CarService, notification(text)) else stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) server.stop()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(text: String) = SessionNotification.build(
        this,
        title = "AutoPlay activo en el auto",
        text = text,
        stopIntent = Intent(this, CarService::class.java).setAction(ACTION_STOP),
    )

    private fun notificationText(state: CarSessionState): String {
        val online = state.devices.count { it.online }
        val playing = state.playing?.name
        return when {
            online == 0 -> "Esperando celulares"
            playing != null -> "Suena la música de $playing · $online conectado(s)"
            else -> "$online celular(es) conectado(s)"
        }
    }

    companion object {
        private const val ACTION_STOP = "org.example.autoplay.car.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CarService::class.java))
        }
    }
}
