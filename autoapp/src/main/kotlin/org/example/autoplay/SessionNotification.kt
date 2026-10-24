package org.example.autoplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** The ongoing notification that keeps a session visible while the app is in the background. */
object SessionNotification {
    const val ID = 1
    private const val CHANNEL = "session"

    fun build(
        context: Context,
        title: String,
        text: String,
        stopIntent: Intent,
        extraActions: List<Pair<String, Intent>> = emptyList(),
    ): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Conexión con el auto", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Muestra que AutoPlay sigue conectado en segundo plano"
            },
        )
        // Same intent as the launcher icon, so it brings back the existing task instead of opening a second one
        val open = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(PendingIntent.getActivity(context, 0, open, flags))
        extraActions.forEachIndexed { i, (label, intent) ->
            builder.addAction(0, label, PendingIntent.getService(context, 2 + i, intent, flags))
        }
        return builder
            .addAction(0, "Desconectar", PendingIntent.getService(context, 1, stopIntent, flags))
            .build()
    }

    fun update(service: Service, notification: Notification) {
        service.getSystemService(NotificationManager::class.java).notify(ID, notification)
    }
}
