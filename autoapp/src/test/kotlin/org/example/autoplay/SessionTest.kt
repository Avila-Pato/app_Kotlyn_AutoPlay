package org.example.autoplay

import org.example.autoplay.net.DeviceInfo
import org.example.autoplay.net.Message
import org.example.autoplay.net.Protocol
import org.example.autoplay.session.JoinLink
import org.example.autoplay.session.SessionCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class SessionTest {
    @Test fun codeIsSixDigits() {
        val code = SessionCode.generate(now = 0)
        assertEquals(6, code.value.length)
        assertTrue(code.value.all(Char::isDigit))
    }

    @Test fun codeCanBeRedeemedOnlyOnce() {
        val code = SessionCode("123456", createdAt = 0)
        assertFalse(code.redeem("000000", now = 1))
        assertTrue(code.redeem("123456", now = 1))
        assertFalse(code.redeem("123456", now = 2))
    }

    @Test fun codeExpiresAfterFiveMinutes() {
        val code = SessionCode("123456", createdAt = 0)
        assertFalse(code.redeem("123456", now = SessionCode.TTL_MILLIS))
    }

    @Test fun joinLinkRoundTrip() {
        val link = JoinLink("192.168.43.1", 47800, "012345")
        assertEquals(link, JoinLink.parse(link.toString()))
    }

    @Test fun joinLinkRejectsBadInput() {
        assertNull(JoinLink.parse("https://example.com"))
        assertNull(JoinLink.parse("autoplay://join?h=1.2.3.4&p=99999&c=123456"))
        assertNull(JoinLink.parse("autoplay://join?h=1.2.3.4&p=80&c=12ab56"))
    }

    @Test fun protocolRoundTrip() {
        val messages = listOf(
            Message.Hello(Protocol.VERSION, "123456", "Pixel 9"),
            Message.Resume("abcd"),
            Message.Accepted("abcd", "Auto X", "dev1"),
            Message.Rejected("Código incorrecto o vencido"),
            Message.Roster(
                listOf(
                    DeviceInfo("dev1", "Galaxy de Ana", online = true, sharing = true),
                    DeviceInfo("dev2", "Pixel 9", online = false, sharing = false, bluetooth = true),
                ),
                playingId = "dev1",
            ),
            Message.Roster(emptyList(), playingId = null),
            Message.AudioState(true),
            Message.Audio(byteArrayOf(1, 2, 3, 10, 0)),
            Message.AudioAac(byteArrayOf(0x21, 10, 0)),
            Message.Ping,
            Message.Pong,
            Message.Bye,
        )
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out -> messages.forEach { Protocol.write(out, it) } }

        val input = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(messages, messages.map { Protocol.read(input) })
    }
}
