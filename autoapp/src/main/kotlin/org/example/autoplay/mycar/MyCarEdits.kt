package org.example.autoplay.mycar

import java.time.LocalDate
import java.util.UUID

/*
 * Every change to a car as a pure function, so the phone (which keeps the cars) and the car screen
 * (which edits the phone's car and sends it back) change a car in exactly the same way.
 */

private const val MAX_DOCUMENT_NAME = 40

/** More pictures than this on one paper are not worth keeping on a phone. */
const val MAX_IMAGES_PER_DOCUMENT = 6

/** Null (nothing changes) when [km] is lower than a reading already on record. */
fun MyCar.withKm(km: Int, date: LocalDate): MyCar? = if (isValidKm(this, km)) copy(km = km, kmDate = date) else null

fun MyCar.withOilChange(change: OilChange): MyCar {
    val changes = (listOf(change) + oilChanges)
        .sortedWith(compareByDescending<OilChange> { it.date }.thenByDescending { it.km })
    // An oil change is also an odometer reading
    val newer = change.km > km
    return copy(
        oilChanges = changes,
        km = if (newer) change.km else km,
        kmDate = if (newer) change.date else kmDate,
    )
}

fun MyCar.withoutOilChange(change: OilChange): MyCar =
    copy(oilChanges = oilChanges.toMutableList().apply { remove(change) })

fun MyCar.withoutOilHistory(): MyCar = copy(oilChanges = emptyList())

/** A paper of the driver's own, like an insurance policy; null when the name is empty. */
fun MyCar.withDocument(
    name: String,
    expiresOn: LocalDate?,
    expiresText: String,
    note: String,
    id: String = UUID.randomUUID().toString(),
): MyCar? {
    val clean = name.trim().take(MAX_DOCUMENT_NAME)
    if (clean.isEmpty()) return null
    return copy(documents = documents + CarDocument(id, clean, expiresOn, expiresText, note))
}

fun MyCar.withoutDocument(id: String): MyCar = copy(documents = documents.filter { it.id != id })

fun MyCar.withDocumentUpdated(id: String, expiresOn: LocalDate?, expiresText: String, note: String): MyCar =
    copy(
        documents = documents.map {
            if (it.id == id) it.copy(expiresOn = expiresOn, expiresText = expiresText, note = note) else it
        },
    )

/** Null when there is no such paper or it already has [MAX_IMAGES_PER_DOCUMENT] pictures. */
fun MyCar.withImageAdded(docId: String, name: String): MyCar? {
    val document = documents.firstOrNull { it.id == docId } ?: return null
    if (document.images.size >= MAX_IMAGES_PER_DOCUMENT) return null
    return copy(documents = documents.map { if (it.id == docId) it.copy(images = it.images + name) else it })
}

fun MyCar.withImageRemoved(docId: String, name: String): MyCar =
    copy(documents = documents.map { if (it.id == docId) it.copy(images = it.images - name) else it })

fun MyCar.withTaskToggled(id: String): MyCar = copy(tasks = tasks.map { if (it.id == id) it.copy(done = !it.done) else it })

fun MyCar.withTask(text: String, id: String = UUID.randomUUID().toString()): MyCar? {
    val clean = text.trim()
    if (clean.isEmpty()) return null
    return copy(tasks = tasks + Task(id, clean, done = false))
}

fun MyCar.withoutTask(id: String): MyCar = copy(tasks = tasks.filter { it.id != id })

fun MyCar.withProfile(profile: CarProfile): MyCar = copy(profile = profile)

/** A car with nothing in it yet but its name: the driver fills in the rest. */
fun emptyCar(name: String, today: LocalDate, id: String = UUID.randomUUID().toString()): MyCar = MyCar(
    id = id,
    profile = CarProfile(
        name = name.trim(),
        brand = "",
        model = "",
        trim = "",
        year = today.year,
        plate = "",
        engine = "",
        vin = "",
        color = "",
        fuel = "",
        transmission = "",
    ),
    documents = emptyList(),
    oilChanges = emptyList(),
    tasks = emptyList(),
    notes = emptyList(),
    km = 0,
    kmDate = today,
)

fun Garage.select(id: String): Garage = if (cars.any { it.id == id }) copy(selectedId = id) else this

/** Adds [car] and makes it the one in view. */
fun Garage.add(car: MyCar): Garage = Garage(cars + car, car.id)

/** The last car cannot be removed; the one in view moves to the first that is left. */
fun Garage.remove(id: String): Garage {
    if (cars.size <= 1 || cars.none { it.id == id }) return this
    val left = cars.filter { it.id != id }
    return Garage(left, if (selectedId == id) left.first().id else selectedId)
}

/** Replaces the car with the same id; nothing changes when that car is no longer in the garage. */
fun Garage.replace(car: MyCar): Garage = if (cars.any { it.id == car.id }) copy(cars = cars.map { if (it.id == car.id) car else it }) else this

/**
 * The radio sent back its copy of a car. It knows nothing of the pictures, which live on this phone, so every paper
 * keeps the pictures it has here (a paper only the radio knows has none).
 */
fun Garage.replaceFromRadio(car: MyCar): Garage {
    val mine = cars.firstOrNull { it.id == car.id } ?: return this
    val merged = car.copy(
        documents = car.documents.map { paper ->
            paper.copy(images = mine.documents.firstOrNull { it.id == paper.id }?.images ?: paper.images)
        },
    )
    return replace(merged)
}

/** Names of every picture some paper of some car still points to. */
fun Garage.referencedImages(): Set<String> = cars.flatMap { car -> car.documents.flatMap { it.images } }.toSet()

/** Applies [transform] to the car in view; null from [transform] means "refused", and nothing changes. */
fun Garage.edit(transform: (MyCar) -> MyCar?): Garage? = transform(selected)?.let { replace(it) }

/**
 * The phone whose car the radio shows: the first one, in priority order, that is online and has sent its car.
 * [cars] has one entry per phone in that order, null for a phone that is offline or has not sent a car.
 */
fun firstCarSource(cars: List<MyCar?>): Int? = cars.indexOfFirst { it != null }.takeIf { it >= 0 }
