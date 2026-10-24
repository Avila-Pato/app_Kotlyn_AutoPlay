package org.example.autoplay

import org.example.autoplay.mycar.CarDocument
import org.example.autoplay.mycar.MyCar
import org.example.autoplay.mycar.OilChange
import org.example.autoplay.mycar.documentDaysLeft
import org.example.autoplay.mycar.documentRemainingFraction
import org.example.autoplay.mycar.documentTone
import org.example.autoplay.mycar.estimatedNextOilDate
import org.example.autoplay.mycar.formatKm
import org.example.autoplay.mycar.isValidKm
import org.example.autoplay.mycar.kmToNextOil
import org.example.autoplay.mycar.mostUrgent
import org.example.autoplay.mycar.oilInterval
import org.example.autoplay.mycar.oilRemainingFraction
import org.example.autoplay.mycar.oilTone
import org.example.autoplay.mycar.parseDue
import org.example.autoplay.ui.Tone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MyCarStatusTest {
    private val car = sampleCar()

    @Test fun kmToNextOilCountsFromTheLastChange() {
        assertEquals(3_662, kmToNextOil(car))
    }

    @Test fun kmToNextOilIsNegativeWhenOverdue() {
        assertEquals(-404, kmToNextOil(car.copy(km = 88_000)))
    }

    @Test fun kmToNextOilIsNullWithoutChanges() {
        assertNull(kmToNextOil(car.copy(oilChanges = emptyList())))
    }

    @Test fun nextOilDateFollowsTheDrivingPace() {
        // 6.338 km in 150 days: the 3.662 km left take 87 more days, well before the 12-month limit
        assertEquals(LocalDate.of(2027, 1, 19), estimatedNextOilDate(car))
    }

    @Test fun nextOilDateStopsAtTwelveMonths() {
        // Barely driven: the km would take years, so time decides
        val parked = car.copy(km = 77_700, kmDate = LocalDate.of(2026, 10, 24))
        assertEquals(LocalDate.of(2027, 5, 27), estimatedNextOilDate(parked))
    }

    @Test fun nextOilDateWithoutPaceUsesTheTimeLimit() {
        val single = car.copy(
            oilChanges = listOf(OilChange(LocalDate.of(2026, 5, 27), 83_934, "5W-30", 93_934)),
            kmDate = LocalDate.of(2026, 5, 27),
        )
        assertEquals(LocalDate.of(2027, 5, 27), estimatedNextOilDate(single))
    }

    @Test fun lowerKmIsRejected() {
        assertFalse(isValidKm(car, 83_933))
        assertFalse(isValidKm(car, 70_000))
        assertTrue(isValidKm(car, 83_934))
        assertTrue(isValidKm(car, 90_000))
    }

    @Test fun documentToneThresholds() {
        assertEquals(Tone.Success, documentTone(61))
        assertEquals(Tone.Warning, documentTone(60))
        assertEquals(Tone.Warning, documentTone(15))
        assertEquals(Tone.Error, documentTone(14))
        assertEquals(Tone.Error, documentTone(0))
        assertEquals(Tone.Error, documentTone(-5))
        assertEquals(Tone.Neutral, documentTone(null))
    }

    @Test fun oilToneThresholds() {
        assertEquals(Tone.Success, oilTone(2_001))
        assertEquals(Tone.Warning, oilTone(2_000))
        assertEquals(Tone.Warning, oilTone(500))
        assertEquals(Tone.Error, oilTone(499))
        assertEquals(Tone.Error, oilTone(-100))
    }

    @Test fun documentDaysLeftCountsFromToday() {
        val permit = car.documents.first { it.id == "permit" }
        assertEquals(20L, documentDaysLeft(permit, LocalDate.of(2027, 3, 11)))
        assertNull(documentDaysLeft(CarDocument("x", "Sin fecha", null, "", ""), LocalDate.of(2027, 3, 11)))
    }

    @Test fun urgentIsOilWhenEverythingIsFine() {
        val urgent = mostUrgent(car, LocalDate.of(2026, 10, 24))!!
        assertEquals("Aceite en 3.662 km", urgent.text)
        assertEquals(Tone.Success, urgent.tone)
    }

    @Test fun urgentIsThePaperThatIsAboutToExpire() {
        val urgent = mostUrgent(car, LocalDate.of(2027, 3, 11))!!
        assertEquals("Permiso vence en 20 días", urgent.text)
        assertEquals(Tone.Warning, urgent.tone)
    }

    @Test fun monthOnlyExpiryUsesTheLastDayOfTheMonth() {
        val due = parseDue("04/2027")!!
        assertEquals(LocalDate.of(2027, 4, 30), due.date)
        assertEquals("Abril 2027", due.text)
        assertEquals(LocalDate.of(2028, 2, 29), parseDue("2/2028")!!.date)
    }

    @Test fun exactExpiryKeepsNoText() {
        val due = parseDue("31/03/2027")!!
        assertEquals(LocalDate.of(2027, 3, 31), due.date)
        assertEquals("", due.text)
    }

    @Test fun invalidExpiryIsNull() {
        assertNull(parseDue("31/02/2027"))
        assertNull(parseDue("13/2027"))
        assertNull(parseDue("pronto"))
    }

    @Test fun kmUsesDotsAsThousandsSeparator() {
        assertEquals("83.934", formatKm(83_934))
        assertEquals("999", formatKm(999))
        assertEquals("1.000.000", formatKm(1_000_000))
    }

    @Test fun preloadedCarMatchesTheRecords() {
        val c: MyCar = car
        assertEquals(83_934, c.km)
        assertEquals(4, c.oilChanges.size)
        assertEquals(3, c.documents.size)
        assertEquals(7, c.tasks.size)
    }

    @Test fun oilRingShowsWhatIsLeft() {
        // 77.596 -> 87.596 with the car at 83.934: 63,4 % used, 36,6 % left
        assertEquals(0.366f, oilRemainingFraction(car), 0.001f)
        assertEquals(0f, oilRemainingFraction(car.copy(km = 90_000)), 0f)
        assertEquals(0f, oilRemainingFraction(car.copy(oilChanges = emptyList())), 0f)
    }

    @Test fun paperRingIsAYearAtMost() {
        assertEquals(1f, documentRemainingFraction(400), 0f)
        assertEquals(0.5f, documentRemainingFraction(182), 0.01f)
        assertEquals(0f, documentRemainingFraction(-3), 0f)
        assertEquals(0f, documentRemainingFraction(null), 0f)
    }

    @Test fun intervalBetweenTwoOilChanges() {
        val (newer, older) = car.oilChanges[0] to car.oilChanges[1]
        // 11/09/2025 (67.777 km) to 27/05/2026 (77.596 km)
        assertEquals(9_819 to 258L, oilInterval(newer, older))
    }

    @Test fun customPapersCountForTheMostUrgentItem() {
        val withInsurance = car.copy(
            documents = car.documents + CarDocument("seguro", "Seguro de auto", LocalDate.of(2026, 11, 3), "", ""),
        )
        val urgent = mostUrgent(withInsurance, LocalDate.of(2026, 10, 24))!!
        assertEquals("Seguro vence en 10 días", urgent.text)
        assertEquals(Tone.Error, urgent.tone)
    }
}
