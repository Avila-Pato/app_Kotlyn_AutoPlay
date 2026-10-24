package org.example.autoplay

import org.example.autoplay.mycar.DEFAULT_CAR_ID
import org.example.autoplay.mycar.Garage
import org.example.autoplay.mycar.MyCar
import org.example.autoplay.mycar.OilChange
import org.example.autoplay.mycar.add
import org.example.autoplay.mycar.carFromJson
import org.example.autoplay.mycar.carFromWire
import org.example.autoplay.mycar.edit
import org.example.autoplay.mycar.emptyCar
import org.example.autoplay.mycar.firstCarSource
import org.example.autoplay.mycar.garageFromJson
import org.example.autoplay.mycar.remove
import org.example.autoplay.mycar.replace
import org.example.autoplay.mycar.select
import org.example.autoplay.mycar.toJson
import org.example.autoplay.mycar.withDocument
import org.example.autoplay.mycar.withKm
import org.example.autoplay.mycar.withOilChange
import org.example.autoplay.mycar.withTask
import org.example.autoplay.mycar.withTaskToggled
import org.example.autoplay.mycar.withoutDocument
import org.example.autoplay.mycar.withoutOilChange
import org.example.autoplay.mycar.withoutOilHistory
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GarageTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val hyundai = sampleCar()
    private val garage = Garage(listOf(hyundai), DEFAULT_CAR_ID)

    @Test fun aCarSurvivesTheTripThroughJson() {
        assertEquals(hyundai, carFromJson(hyundai.toJson()))
    }

    @Test fun theWireDoesNotChangeACar() {
        // The line that crosses the network cannot hold a raw line break: the protocol splits fields on them
        val text = hyundai.toJson().toString()
        assertTrue('\n' !in text)
        assertEquals(hyundai, carFromWire(text))
    }

    @Test fun textThatIsNotACarIsIgnored() {
        assertNull(carFromWire("hola"))
        assertNull(carFromWire("{}"))
    }

    @Test fun aGarageSurvivesTheTripThroughJson() {
        val two = garage.add(emptyCar("Auto de papá", today, id = "papa"))
        assertEquals(two, garageFromJson(two.toJson()))
    }

    @Test fun aCarSavedBeforeThereWereSeveralBelongsToTheDefaultOne() {
        val old = hyundai.toJson().apply { remove("id") }
        assertEquals(DEFAULT_CAR_ID, carFromJson(old).id)
        assertEquals(hyundai, carFromJson(JSONObject(old.toString())))
    }

    @Test fun aNewCarIsEmptyButForItsName() {
        val car = emptyCar("  Auto de papá ", today, id = "papa")
        assertEquals("Auto de papá", car.profile.name)
        assertEquals(0, car.km)
        assertTrue(car.documents.isEmpty() && car.oilChanges.isEmpty() && car.tasks.isEmpty() && car.notes.isEmpty())
        assertEquals(2026, car.profile.year)
    }

    @Test fun addingACarPutsItInView() {
        val two = garage.add(emptyCar("Auto de papá", today, id = "papa"))
        assertEquals(listOf(DEFAULT_CAR_ID, "papa"), two.cars.map { it.id })
        assertEquals("papa", two.selected.id)
    }

    @Test fun selectingAnUnknownCarChangesNothing() {
        val two = garage.add(emptyCar("Auto de papá", today, id = "papa"))
        assertEquals(DEFAULT_CAR_ID, two.select(DEFAULT_CAR_ID).selected.id)
        assertSame(two, two.select("nadie"))
    }

    @Test fun theLastCarCannotBeRemoved() {
        assertSame(garage, garage.remove(DEFAULT_CAR_ID))
    }

    @Test fun removingTheCarInViewMovesToTheFirstThatIsLeft() {
        val two = garage.add(emptyCar("Auto de papá", today, id = "papa"))
        val left = two.remove("papa")
        assertEquals(listOf(DEFAULT_CAR_ID), left.cars.map { it.id })
        assertEquals(DEFAULT_CAR_ID, left.selected.id)
    }

    @Test fun editingTouchesOnlyTheCarInView() {
        val two = garage.add(emptyCar("Auto de papá", today, id = "papa"))
        val edited = two.edit { it.withKm(1_000, today) }!!
        assertEquals(1_000, edited.cars.first { it.id == "papa" }.km)
        assertEquals(hyundai.km, edited.cars.first { it.id == DEFAULT_CAR_ID }.km)
    }

    @Test fun aRefusedEditChangesNothing() {
        assertNull(garage.edit { it.withKm(10, today) })
    }

    @Test fun theRadiosEditIsKeptOnlyIfTheCarIsStillThere() {
        val changed = hyundai.copy(km = 90_000)
        assertEquals(90_000, garage.replace(changed).selected.km)
        assertSame(garage, garage.replace(changed.copy(id = "otro")))
    }

    @Test fun oilChangesAreKeptNewestFirstAndMoveTheOdometer() {
        val change = OilChange(today, 90_000, "5W-30", 100_000)
        val car = hyundai.withOilChange(change)
        assertEquals(change, car.oilChanges.first())
        assertEquals(90_000, car.km)
        assertEquals(today, car.kmDate)
        assertEquals(hyundai.oilChanges, car.withoutOilChange(change).oilChanges)
        assertTrue(car.withoutOilHistory().oilChanges.isEmpty())
    }

    @Test fun papersAndTasksCanBeAddedAndRemoved() {
        val withPaper = hyundai.withDocument("Seguro", today.plusDays(10), "", "", id = "seguro")!!
        assertTrue(withPaper.documents.any { it.id == "seguro" })
        assertTrue(withPaper.withoutDocument("seguro").documents.none { it.id == "seguro" })
        assertNull(hyundai.withDocument("   ", null, "", ""))

        val withTask = hyundai.withTask("  Lavar  ", id = "lavar")!!
        assertEquals("Lavar", withTask.tasks.last().text)
        assertTrue(withTask.withTaskToggled("lavar").tasks.last().done)
        assertNull(hyundai.withTask("  "))
    }

    @Test fun theRadioShowsTheFirstPhoneInLineThatSentACar() {
        val mine: MyCar = hyundai
        val other = emptyCar("Otro", today, id = "otro")
        assertEquals(1, firstCarSource(listOf(null, mine, other)))
        assertEquals(0, firstCarSource(listOf(other, mine)))
    }

    @Test fun noPhoneWithACarMeansNoSource() {
        assertNull(firstCarSource(emptyList()))
        assertNull(firstCarSource(listOf(null, null)))
    }

    @Test fun differentCarsAreNotEqual() {
        assertNotEquals(hyundai, emptyCar("Otro", today, id = "otro"))
    }
}
