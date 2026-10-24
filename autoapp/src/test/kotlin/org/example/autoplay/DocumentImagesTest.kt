package org.example.autoplay

import org.example.autoplay.mycar.CarDocument
import org.example.autoplay.mycar.DEFAULT_CAR_ID
import org.example.autoplay.mycar.Garage
import org.example.autoplay.mycar.MAX_IMAGES_PER_DOCUMENT
import org.example.autoplay.mycar.carFromJson
import org.example.autoplay.mycar.fitSize
import org.example.autoplay.mycar.mostUrgent
import org.example.autoplay.mycar.orphanImages
import org.example.autoplay.mycar.referencedImages
import org.example.autoplay.mycar.replaceFromRadio
import org.example.autoplay.mycar.sampleSizeFor
import org.example.autoplay.mycar.shortName
import org.example.autoplay.mycar.toJson
import org.example.autoplay.mycar.withDocument
import org.example.autoplay.mycar.withImageAdded
import org.example.autoplay.mycar.withImageRemoved
import org.example.autoplay.ui.Tone
import org.example.autoplay.mycar.documentTone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DocumentImagesTest {
    private val car = sampleCar()
    private val today = LocalDate.of(2026, 10, 24)

    @Test fun picturesSurviveTheTripThroughJson() {
        val withPictures = car.withImageAdded("permit", "a.jpg")!!.withImageAdded("permit", "b.jpg")!!
        assertEquals(listOf("a.jpg", "b.jpg"), carFromJson(withPictures.toJson()).documents.first { it.id == "permit" }.images)
    }

    @Test fun aPaperSavedBeforePicturesExistedHasNone() {
        val old = car.toJson()
        val papers = old.getJSONArray("documents")
        for (i in 0 until papers.length()) papers.getJSONObject(i).remove("images")
        assertTrue(carFromJson(old).documents.all { it.images.isEmpty() })
    }

    @Test fun aPaperHoldsAtMostSixPictures() {
        var current = car
        repeat(MAX_IMAGES_PER_DOCUMENT) { current = current.withImageAdded("soap", "p$it.jpg")!! }
        assertEquals(6, current.documents.first { it.id == "soap" }.images.size)
        assertNull(current.withImageAdded("soap", "extra.jpg"))
    }

    @Test fun aPictureNeedsItsPaper() {
        assertNull(car.withImageAdded("no-existe", "a.jpg"))
    }

    @Test fun removingAPictureKeepsTheOthers() {
        val two = car.withImageAdded("permit", "a.jpg")!!.withImageAdded("permit", "b.jpg")!!
        assertEquals(listOf("b.jpg"), two.withImageRemoved("permit", "a.jpg").documents.first { it.id == "permit" }.images)
    }

    @Test fun theRadioNeverTakesThePicturesOfThePhone() {
        val phone = Garage(listOf(car.withImageAdded("permit", "a.jpg")!!), DEFAULT_CAR_ID)
        // The radio's copy has no picture names (or stale ones) and a paper only it knows
        val fromRadio = car.copy(
            km = 99_000,
            documents = car.documents + CarDocument("solo-radio", "Licencia", null, "", ""),
        )
        val merged = phone.replaceFromRadio(fromRadio).selected
        assertEquals(99_000, merged.km)
        assertEquals(listOf("a.jpg"), merged.documents.first { it.id == "permit" }.images)
        assertTrue(merged.documents.first { it.id == "solo-radio" }.images.isEmpty())
    }

    @Test fun aPaperTheRadioDeletedLosesItsPictures() {
        val phone = Garage(listOf(car.withImageAdded("permit", "a.jpg")!!), DEFAULT_CAR_ID)
        val merged = phone.replaceFromRadio(car.copy(documents = car.documents.filter { it.id != "permit" }))
        assertTrue("a.jpg" !in merged.referencedImages())
    }

    @Test fun everyPicturePointedToIsReferenced() {
        val garage = Garage(listOf(car.withImageAdded("permit", "a.jpg")!!.withImageAdded("soap", "b.jpg")!!), DEFAULT_CAR_ID)
        assertEquals(setOf("a.jpg", "b.jpg"), garage.referencedImages())
    }

    @Test fun onlyOldUnreferencedPicturesAreSweptAway() {
        val files = listOf("kept.jpg" to 500_000L, "old-orphan.jpg" to 500_000L, "just-imported.jpg" to 5_000L)
        assertEquals(listOf("old-orphan.jpg"), orphanImages(files, referenced = setOf("kept.jpg"), minAgeMillis = 60_000L))
    }

    @Test fun picturesAreScaledDownNeverUp() {
        assertEquals(1600 to 1200, fitSize(4000, 3000, 1600))
        assertEquals(1200 to 1600, fitSize(3000, 4000, 1600))
        assertEquals(800 to 600, fitSize(800, 600, 1600))
    }

    @Test fun aPictureIsReadSmallerOnlyAsFarAsItStaysBigEnough() {
        // the shorter side (3000) cannot be halved and stay above 1600
        assertEquals(1, sampleSizeFor(4000, 3000, 1600))
        assertEquals(2, sampleSizeFor(8000, 6000, 1600))
        assertEquals(1, sampleSizeFor(1600, 1200, 1600))
        assertEquals(8, sampleSizeFor(4000, 3000, 240))
    }

    @Test fun certificatesAreNamedByWhatTheyCertify() {
        fun short(name: String) = CarDocument("x", name, null, "", "").shortName()
        assertEquals("Inscripción", short("Certificado de inscripción"))
        assertEquals("Revisión técnica", short("Certificado de revisión técnica"))
        assertEquals("Licencia", short("Licencia de conducir"))
        assertEquals("Permiso", short("Permiso de circulación"))
        assertEquals("SOAP", short("SOAP"))
    }

    @Test fun aPaperWithoutExpiryNeverMakesNoise() {
        val withLicense = car.withDocument("Licencia de conducir", null, "", "")!!
        assertEquals(Tone.Neutral, documentTone(null))
        // Same most urgent thing as without it
        assertEquals(mostUrgent(car, today)?.text, mostUrgent(withLicense, today)?.text)
    }
}
