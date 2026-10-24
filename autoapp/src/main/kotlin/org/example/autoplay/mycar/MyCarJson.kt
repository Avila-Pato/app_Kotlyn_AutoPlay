package org.example.autoplay.mycar

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** The car every install starts with, and the one a record saved before there were several belongs to. */
const val DEFAULT_CAR_ID = "mi-auto"

/** All the cars of the driver, and the one being looked at. */
data class Garage(val cars: List<MyCar>, val selectedId: String) {
    val selected: MyCar get() = cars.firstOrNull { it.id == selectedId } ?: cars.first()
}

fun MyCar.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put(
        "profile",
        JSONObject()
            .put("name", profile.name)
            .put("brand", profile.brand)
            .put("model", profile.model)
            .put("trim", profile.trim)
            .put("year", profile.year)
            .put("plate", profile.plate)
            .put("engine", profile.engine)
            .put("vin", profile.vin)
            .put("color", profile.color)
            .put("fuel", profile.fuel)
            .put("transmission", profile.transmission),
    )
    .put(
        "documents",
        JSONArray().also { array ->
            documents.forEach {
                array.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("expiresOn", it.expiresOn?.toString() ?: "")
                        .put("expiresText", it.expiresText)
                        .put("note", it.note)
                        .put("images", JSONArray().also { names -> it.images.forEach { name -> names.put(name) } }),
                )
            }
        },
    )
    .put(
        "oilChanges",
        JSONArray().also { array ->
            oilChanges.forEach {
                array.put(
                    JSONObject()
                        .put("date", it.date.toString())
                        .put("km", it.km)
                        .put("oil", it.oil)
                        .put("nextKm", it.nextKm),
                )
            }
        },
    )
    .put(
        "tasks",
        JSONArray().also { array ->
            tasks.forEach { array.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)) }
        },
    )
    .put("notes", JSONArray().also { array -> notes.forEach { array.put(it) } })
    .put("km", km)
    .put("kmDate", kmDate.toString())

/** [fallbackId] names a car saved before cars had an id. */
fun carFromJson(o: JSONObject, fallbackId: String = DEFAULT_CAR_ID): MyCar {
    val p = o.getJSONObject("profile")
    val documents = o.getJSONArray("documents")
    val oilChanges = o.getJSONArray("oilChanges")
    val tasks = o.getJSONArray("tasks")
    val notes = o.getJSONArray("notes")
    return MyCar(
        id = o.optString("id").ifEmpty { fallbackId },
        profile = CarProfile(
            name = p.getString("name"),
            brand = p.getString("brand"),
            model = p.getString("model"),
            trim = p.getString("trim"),
            year = p.getInt("year"),
            plate = p.getString("plate"),
            engine = p.getString("engine"),
            vin = p.getString("vin"),
            color = p.getString("color"),
            fuel = p.getString("fuel"),
            transmission = p.getString("transmission"),
        ),
        documents = (0 until documents.length()).map {
            val d = documents.getJSONObject(it)
            CarDocument(
                id = d.getString("id"),
                name = d.getString("name"),
                expiresOn = d.optString("expiresOn").takeIf { text -> text.isNotEmpty() }?.let { text -> LocalDate.parse(text) },
                expiresText = d.optString("expiresText"),
                note = d.optString("note"),
                images = d.optJSONArray("images")?.let { names -> (0 until names.length()).map { i -> names.getString(i) } }.orEmpty(),
            )
        },
        oilChanges = (0 until oilChanges.length()).map {
            val c = oilChanges.getJSONObject(it)
            OilChange(LocalDate.parse(c.getString("date")), c.getInt("km"), c.getString("oil"), c.getInt("nextKm"))
        },
        tasks = (0 until tasks.length()).map {
            val t = tasks.getJSONObject(it)
            Task(t.getString("id"), t.getString("text"), t.optBoolean("done"))
        },
        notes = (0 until notes.length()).map { notes.getString(it) },
        km = o.getInt("km"),
        kmDate = LocalDate.parse(o.getString("kmDate")),
    )
}

fun Garage.toJson(): JSONObject = JSONObject()
    .put("selected", selectedId)
    .put("cars", JSONArray().also { array -> cars.forEach { array.put(it.toJson()) } })

fun garageFromJson(o: JSONObject): Garage {
    val array = o.getJSONArray("cars")
    val cars = (0 until array.length()).map { carFromJson(array.getJSONObject(it)) }
    require(cars.isNotEmpty()) { "A garage has at least one car" }
    return Garage(cars, o.optString("selected").ifEmpty { cars.first().id })
}

/** A car from the wire; null when the text is not a car, so a bad message never breaks the session. */
fun carFromWire(text: String): MyCar? = runCatching { carFromJson(JSONObject(text)) }.getOrNull()
