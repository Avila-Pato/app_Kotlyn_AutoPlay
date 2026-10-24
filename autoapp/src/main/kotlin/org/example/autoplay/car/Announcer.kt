package org.example.autoplay.car

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

fun volumeRequestMessage(deviceName: String): String = "$deviceName pide más volumen"

/**
 * Says short alerts out loud on the car's speakers, even while the driver is in another app,
 * lowering the music while it talks. The speech engine starts on the first alert and is released
 * with [release]; a radio without a Spanish voice gets a short chime instead.
 */
@Singleton
class Announcer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val player: AudioPlayer,
) {
    private val prefs = context.getSharedPreferences("car", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_VOICE, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private enum class Engine { Idle, Starting, Ready, Failed }

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var engine = Engine.Idle
    private var pending: String? = null

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs.edit().putBoolean(KEY_VOICE, on).apply()
    }

    @Synchronized
    fun speak(text: String) {
        if (!_enabled.value) return
        when (engine) {
            Engine.Ready -> say(text)
            Engine.Failed -> chime()
            Engine.Starting -> pending = text
            Engine.Idle -> {
                pending = text
                engine = Engine.Starting
                tts = TextToSpeech(context) { status -> onInit(status) }
            }
        }
    }

    /** Frees the speech engine when the car side is turned off. */
    @Synchronized
    fun release() {
        tts?.shutdown()
        tts = null
        engine = Engine.Idle
        pending = null
        player.duck(false)
    }

    @Synchronized
    private fun onInit(status: Int) {
        val speech = tts
        val ready = status == TextToSpeech.SUCCESS && speech != null && configure(speech)
        engine = if (ready) Engine.Ready else Engine.Failed
        val text = pending ?: return
        pending = null
        if (ready) say(text) else chime()
    }

    /** Spanish (Chile first); false when the radio has no Spanish voice installed. */
    private fun configure(speech: TextToSpeech): Boolean {
        val locale = listOf(Locale.forLanguageTag("es-CL"), Locale.forLanguageTag("es"))
            .firstOrNull { speech.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
            ?: return false
        speech.language = locale
        speech.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        speech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = player.duck(false)

            @Deprecated("Replaced by onError(String?, Int)")
            override fun onError(utteranceId: String?) = player.duck(false)
        })
        return true
    }

    private fun say(text: String) {
        val speech = tts ?: return
        player.duck(true)
        if (speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) == TextToSpeech.SUCCESS) {
            // Never leave the music low if the engine forgets to say it finished
            main.postDelayed({ player.duck(false) }, MAX_DUCK_MILLIS)
        } else {
            player.duck(false)
            chime()
        }
    }

    private fun chime() {
        runCatching {
            RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
        }
    }

    private companion object {
        const val KEY_VOICE = "voice_alerts"
        const val UTTERANCE_ID = "alert"
        const val MAX_DUCK_MILLIS = 10_000L
    }
}
