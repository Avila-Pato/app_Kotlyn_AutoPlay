package org.example.autoplay

import android.media.AudioManager
import org.example.autoplay.car.FocusAction
import org.example.autoplay.car.focusAction
import org.example.autoplay.car.spokenMessage
import org.example.autoplay.net.MAX_SAY_LENGTH
import org.example.autoplay.net.Message
import org.example.autoplay.net.Protocol
import org.example.autoplay.net.cleanSayText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class SayAndFocusTest {
    @Test fun aMessageIsOneCleanLine() {
        assertEquals("llegamos en 5 minutos", cleanSayText("  llegamos   en\n5 minutos  "))
    }

    @Test fun anEmptyMessageSendsNothing() {
        assertNull(cleanSayText(""))
        assertNull(cleanSayText(" \n\t "))
    }

    @Test fun aLongMessageIsCut() {
        assertEquals(MAX_SAY_LENGTH, cleanSayText("a".repeat(500))!!.length)
    }

    @Test fun theRadioNamesWhoSpeaks() {
        assertEquals("Ana dice: llegamos en 5", spokenMessage("Ana", "llegamos en 5"))
    }

    @Test fun aMessageCrossesTheWire() {
        val bytes = ByteArrayOutputStream().also { Protocol.write(DataOutputStream(it), Message.Say("hola, ¿cómo estás?")) }.toByteArray()
        assertEquals(Message.Say("hola, ¿cómo estás?"), Protocol.read(DataInputStream(ByteArrayInputStream(bytes))))
    }

    @Test fun openingTheRadioClosesTheMusic() {
        // The head unit's radio takes the audio for good
        assertEquals(FocusAction.Stop, focusAction(AudioManager.AUDIOFOCUS_LOSS))
    }

    @Test fun aCallOrASoundPausesTheMusicAndGivesItBack() {
        assertEquals(FocusAction.Pause, focusAction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertEquals(FocusAction.Resume, focusAction(AudioManager.AUDIOFOCUS_GAIN))
    }

    @Test fun duckingForOurOwnSpokenAlertsDoesNotPauseAnything() {
        assertEquals(FocusAction.None, focusAction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
    }
}
