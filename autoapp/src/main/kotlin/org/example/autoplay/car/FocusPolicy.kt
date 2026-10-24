package org.example.autoplay.car

import android.media.AudioManager

/** What AutoPlay does when another app (the head unit's radio, a call…) takes or gives back the audio. */
enum class FocusAction {
    /** Nothing: short sounds, like our own spoken alerts, only ask the music to duck. */
    None,

    /** Another app took the audio for a moment: pause the music and carry on when it is given back. */
    Pause,

    /** Another app took the audio for good (the radio was opened): the music is closed and stays closed. */
    Stop,

    /** The audio is ours again after a [Pause]. */
    Resume,
}

fun focusAction(change: Int): FocusAction = when (change) {
    AudioManager.AUDIOFOCUS_LOSS -> FocusAction.Stop
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusAction.Pause
    AudioManager.AUDIOFOCUS_GAIN -> FocusAction.Resume
    else -> FocusAction.None
}

/** What the radio says out loud for a message typed on a phone. */
fun spokenMessage(deviceName: String, text: String): String = "$deviceName dice: $text"
