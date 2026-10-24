package org.example.autoplay

import org.example.autoplay.mycar.carFromWire
import org.example.autoplay.mycar.toJson
import org.example.autoplay.net.Message
import org.example.autoplay.net.Protocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class CarDataProtocolTest {
    private fun roundTrip(message: Message): Message {
        val bytes = ByteArrayOutputStream().also { Protocol.write(DataOutputStream(it), message) }.toByteArray()
        return Protocol.read(DataInputStream(ByteArrayInputStream(bytes)))
    }

    @Test fun theCarCrossesTheWireFromPhoneToRadio() {
        val car = sampleCar()
        val received = roundTrip(Message.CarData(car.toJson().toString())) as Message.CarData
        assertEquals(car, carFromWire(received.json))
    }

    @Test fun theRadiosEditCrossesTheWireBackToThePhone() {
        val edited = sampleCar().copy(km = 90_000)
        val received = roundTrip(Message.CarUpdate(edited.toJson().toString())) as Message.CarUpdate
        assertEquals(edited, carFromWire(received.json))
    }

    @Test fun phoneAndRadioMustRunTheSameProtocol() {
        // A phone of an older version is turned away by the radio, so both are updated together
        assertEquals(6, Protocol.VERSION)
    }
}
