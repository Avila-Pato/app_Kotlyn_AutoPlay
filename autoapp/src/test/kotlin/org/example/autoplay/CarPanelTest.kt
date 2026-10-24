package org.example.autoplay

import org.example.autoplay.car.DEFAULT_CAR_APPS
import org.example.autoplay.car.NAVIGATION_APPS
import org.example.autoplay.car.togglePackage
import org.example.autoplay.car.volumeRequestMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CarPanelTest {
    @Test fun togglingAnAppAddsItAtTheEnd() {
        assertEquals(listOf("a", "b", "c"), togglePackage(listOf("a", "b"), "c"))
    }

    @Test fun togglingAChosenAppRemovesItAndKeepsTheOrder() {
        assertEquals(listOf("a", "c"), togglePackage(listOf("a", "b", "c"), "b"))
    }

    @Test fun togglingTwiceGoesBackToTheStart() {
        val start = listOf("a", "b")
        assertEquals(start, togglePackage(togglePackage(start, "c"), "c"))
    }

    @Test fun defaultsKeepTheFourOriginalShortcuts() {
        assertEquals(4, DEFAULT_CAR_APPS.size)
        assertEquals(DEFAULT_CAR_APPS.distinct(), DEFAULT_CAR_APPS)
        assertTrue(NAVIGATION_APPS.all { it in DEFAULT_CAR_APPS })
    }

    @Test fun volumeAlertNamesThePhone() {
        assertEquals("iPhone de Tomás pide más volumen", volumeRequestMessage("iPhone de Tomás"))
    }
}
