package org.example.autoplay.phone

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.example.autoplay.car.CarServer

/**
 * While this phone plays in the car: keeps the phone's own speaker silent and turns volume keys into car volume.
 * Android captures audio before the phone's volume is applied, so muting the phone does not mute the car.
 */
class CarVolumeRouter(private val context: Context, private val client: PhoneClient) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var savedVolume: Int? = null
    private var session: MediaSession? = null
    private var provider: VolumeProvider? = null

    /** A volume key reached the phone's own stream instead of our session: move the car instead and stay silent. */
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (savedVolume == null) return
            val level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (level > 0) {
                step(level)
                silencePhone()
                bumpSession()
            }
        }
    }

    fun start() {
        if (savedVolume != null) return
        savedVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        silencePhone()
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)

        val remote = object : VolumeProvider(VOLUME_CONTROL_ABSOLUTE, 100, carVolume()) {
            override fun onAdjustVolume(direction: Int) {
                if (direction == AudioManager.ADJUST_RAISE || direction == AudioManager.ADJUST_LOWER) step(direction)
            }

            override fun onSetVolumeTo(volume: Int) = setCarVolume(volume)
        }
        provider = remote
        // Like a Cast session: while it is the active one, the volume keys and slider control the car
        session = MediaSession(context, "AutoPlay").apply {
            setPlaybackToRemote(remote)
            isActive = true
        }
        bumpSession()
    }

    /** Gives the phone its own volume back. */
    fun stop() {
        val saved = savedVolume ?: return
        savedVolume = null
        context.contentResolver.unregisterContentObserver(observer)
        session?.release()
        session = null
        provider = null
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0) }
    }

    /** Keeps the system volume slider in sync when the car's volume changes from the car screen. */
    fun onCarVolumeChanged(level: Int) {
        provider?.currentVolume = level
    }

    /** One step per key press, used by the notification buttons too. */
    fun step(direction: Int) = setCarVolume(carVolume() + direction * STEP)

    private fun setCarVolume(level: Int) {
        client.setCarVolume(level)
        provider?.currentVolume = carVolume()
    }

    private fun carVolume() = client.room.value?.volume ?: CarServer.DEFAULT_VOLUME

    private fun silencePhone() {
        // Some phones refuse volume changes in Do Not Disturb; the music then also plays on the phone
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
    }

    /** Volume keys go to the session that most recently started playing; restarting ours takes them back from the music app. */
    private fun bumpSession() {
        session?.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        session?.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
    }

    companion object {
        const val STEP = 5
    }
}
