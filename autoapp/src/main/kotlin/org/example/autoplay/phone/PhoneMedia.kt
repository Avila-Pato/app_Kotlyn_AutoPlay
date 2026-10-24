package org.example.autoplay.phone

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Base64
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.autoplay.net.MediaAction
import org.example.autoplay.net.Message
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android only shows other apps' media sessions to a notification listener; the user enables it
 * once in the settings ("Acceso a notificaciones"). The service itself does nothing.
 */
class MediaListener : NotificationListenerService()

/**
 * The music app playing on this phone (Spotify, YouTube…): what it plays, for the car screen,
 * and the buttons pressed in the car. Without notification access the buttons still work
 * through media keys; only the song name and cover are missing.
 */
@Singleton
class PhoneMedia @Inject constructor(@ApplicationContext private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListener::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _nowPlaying = MutableStateFlow<Message.NowPlaying?>(null)
    val nowPlaying: StateFlow<Message.NowPlaying?> = _nowPlaying.asStateFlow()

    private var controller: MediaController? = null
    private var watching = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { follow(it.orEmpty()) }
    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
    }

    fun hasAccess(): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Called when a session with the car starts; again after the user grants access. */
    fun start() = main.post {
        if (watching || !hasAccess()) return@post
        val sessions = runCatching { sessionManager.getActiveSessions(listener) }.getOrNull() ?: return@post
        sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listener, main)
        watching = true
        follow(sessions)
    }

    fun stop() = main.post {
        if (watching) sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        watching = false
        controller?.unregisterCallback(controllerCallback)
        controller = null
        _nowPlaying.value = null
    }

    fun command(action: MediaAction) {
        val controls = controller?.transportControls
        if (controls != null) {
            when (action) {
                MediaAction.Next -> controls.skipToNext()
                MediaAction.Previous -> controls.skipToPrevious()
                MediaAction.PlayPause -> if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
            }
            return
        }
        // No access to the sessions: a media key reaches whichever app played last
        val code = when (action) {
            MediaAction.Next -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.Previous -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            MediaAction.PlayPause -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    /** The app that is playing wins; otherwise the most recent one (Android lists them newest first). */
    private fun follow(sessions: List<MediaController>) {
        val next = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
        if (next?.sessionToken == controller?.sessionToken) return publish()
        controller?.unregisterCallback(controllerCallback)
        controller = next
        next?.registerCallback(controllerCallback, main)
        publish()
    }

    private fun publish() {
        val current = controller ?: run {
            _nowPlaying.value = null
            return
        }
        val metadata = current.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val playing = current.playbackState?.state == PlaybackState.STATE_PLAYING
        val previous = _nowPlaying.value
        // Pausing or resuming must not re-encode the cover
        val art = if (previous != null && previous.title == title && previous.artist == artist) previous.art else encodeArt(metadata)
        _nowPlaying.value = Message.NowPlaying(title, artist, playing, art)
    }

    private fun encodeArt(metadata: MediaMetadata?): String {
        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: return ""
        val scaled = Bitmap.createScaledBitmap(bitmap, ART_SIZE, ART_SIZE, true)
        val jpeg = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        return Base64.encodeToString(jpeg, Base64.NO_WRAP)
    }

    private companion object {
        /** Big enough for the car screen, small enough for Bluetooth: about 15 KB. */
        const val ART_SIZE = 256
    }
}
