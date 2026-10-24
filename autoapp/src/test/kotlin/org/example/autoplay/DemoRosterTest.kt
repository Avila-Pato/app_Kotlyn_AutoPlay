package org.example.autoplay

import org.example.autoplay.car.CarSessionState
import org.example.autoplay.car.demoDevices
import org.example.autoplay.car.demoLock
import org.example.autoplay.car.demoMove
import org.example.autoplay.car.demoMute
import org.example.autoplay.car.demoPlayingId
import org.example.autoplay.car.demoRemove
import org.example.autoplay.car.demoRename
import org.example.autoplay.net.cleanDeviceName
import org.example.autoplay.car.demoState
import org.example.autoplay.car.demoVolumeRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DemoRosterTest {
    private fun ids(devices: List<org.example.autoplay.net.DeviceInfo>) = devices.map { it.id }

    @Test fun theFirstOnlinePhoneWithAudioPlays() {
        assertEquals("demo-1", demoPlayingId(demoDevices()))
    }

    @Test fun mutingThePlayingPhonePassesTheMusicOn() {
        val muted = demoMute(demoDevices(), "demo-1", true)
        // demo-3 is muted from the start and demo-4 is offline, so the next one in line is demo-2
        assertEquals("demo-2", demoPlayingId(muted))
        assertEquals("demo-1", demoPlayingId(demoMute(muted, "demo-1", false)))
    }

    @Test fun nobodyPlaysWhenEveryoneIsMutedOrOffline() {
        var devices = demoDevices()
        listOf("demo-1", "demo-2").forEach { devices = demoMute(devices, it, true) }
        assertNull(demoPlayingId(devices))
        assertNull(demoState(CarSessionState(), devices).track)
    }

    @Test fun lockingVolumeKeepsWhoPlays() {
        val locked = demoLock(demoDevices(), "demo-1", true)
        assertEquals("demo-1", demoPlayingId(locked))
        assertEquals(true, locked.first { it.id == "demo-1" }.volumeLocked)
    }

    @Test fun movingUpAndDownKeepsThePositions() {
        val devices = demoDevices()
        assertEquals(listOf("demo-2", "demo-1", "demo-3", "demo-4"), ids(demoMove(devices, "demo-2", 0)))
        assertEquals(listOf("demo-2", "demo-3", "demo-1", "demo-4"), ids(demoMove(demoMove(devices, "demo-2", 0), "demo-1", 2)))
        assertEquals(listOf("demo-1", "demo-3", "demo-4", "demo-2"), ids(demoMove(devices, "demo-2", 99)))
    }

    @Test fun putTheirMusicOnMakesThatPhonePlay() {
        assertEquals("demo-2", demoPlayingId(demoMove(demoDevices(), "demo-2", 0)))
    }

    @Test fun removingThePlayingPhoneHandsOverTheMusic() {
        val removed = demoRemove(demoDevices(), "demo-1")
        assertEquals(listOf("demo-2", "demo-3", "demo-4"), ids(removed))
        assertEquals("demo-2", demoPlayingId(removed))
    }

    @Test fun theVolumeRequestStopsWhenItsPhoneIsUnlockedOrGone() {
        assertNotNull(demoVolumeRequest(demoDevices()))
        assertNull(demoVolumeRequest(demoLock(demoDevices(), "demo-2", false)))
        assertNull(demoVolumeRequest(demoRemove(demoDevices(), "demo-2")))
    }

    @Test fun renamingChangesOnlyThatPhone() {
        val renamed = demoRename(demoDevices(), "demo-2", "  Celular de Tomás ")
        assertEquals("Celular de Tomás", renamed.first { it.id == "demo-2" }.name)
        assertEquals("Galaxy de Ana", renamed.first { it.id == "demo-1" }.name)
        assertEquals(ids(demoDevices()), ids(renamed))
    }

    @Test fun anEmptyNameChangesNothing() {
        assertEquals(demoDevices(), demoRename(demoDevices(), "demo-2", "   "))
    }

    @Test fun deviceNamesAreTrimmedAndCapped() {
        assertEquals("Ana", cleanDeviceName("  Ana "))
        assertEquals(30, cleanDeviceName("x".repeat(80))!!.length)
        assertNull(cleanDeviceName(""))
        assertNull(cleanDeviceName("   "))
    }
}
