package org.example.autoplay.mycar

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** The fixed data sheet of the car. */
data class CarProfile(
    /** Short title shown under the screen name. */
    val name: String,
    val brand: String,
    val model: String,
    val trim: String,
    val year: Int,
    val plate: String,
    val engine: String,
    val vin: String,
    val color: String,
    val fuel: String,
    val transmission: String,
)

/**
 * A paper that expires. [expiresText] covers dates without an exact day, like "Abril 2027":
 * [expiresOn] then holds the last day of that month, which is what the countdown uses.
 */
data class CarDocument(
    val id: String,
    val name: String,
    val expiresOn: LocalDate?,
    val expiresText: String,
    val note: String,
    /** Names of the pictures of this paper, kept in the phone's own storage; they never travel to the radio. */
    val images: List<String> = emptyList(),
)

data class OilChange(val date: LocalDate, val km: Int, val oil: String, val nextKm: Int)

data class Task(val id: String, val text: String, val done: Boolean)

data class MyCar(
    /** Stable name of this car, so the radio and the phone talk about the same one. */
    val id: String,
    val profile: CarProfile,
    val documents: List<CarDocument>,
    /** Newest first. */
    val oilChanges: List<OilChange>,
    val tasks: List<Task>,
    val notes: List<String>,
    val km: Int,
    /** Day the [km] reading was taken. */
    val kmDate: LocalDate,
)

/**
 * Where the car shown on "Mi auto" comes from. On the phone it is the driver's own garage; on the radio it is
 * the car the phone sent, so the same screen works on both and edits go back to where the data lives.
 */
interface MyCarData {
    /** The cars on offer; null on the radio while no phone has sent one. */
    val garage: Flow<Garage?>
    fun snapshot(): Garage?

    /** Only the phone keeps several cars; the radio shows the one the phone has in view. */
    val canManageGarage: Boolean

    /** Applies [transform] to the car in view; false when it refused (returned null) or there is no car. */
    fun edit(transform: (MyCar) -> MyCar?): Boolean
    fun selectCar(id: String)
    fun addCar(name: String)
    fun removeCar(id: String)
}

/** The driver's cars, kept on the phone as JSON; the first launch starts with [initialMyCar], which each build flavor defines. */
@Singleton
class MyCarStore @Inject constructor(@ApplicationContext context: Context, private val images: DocumentImages) : MyCarData {
    private val prefs = context.getSharedPreferences("my_car", Context.MODE_PRIVATE)
    private val _garage: MutableStateFlow<Garage>

    override val garage: StateFlow<Garage>
    override val canManageGarage = true

    init {
        val saved = load()
        _garage = MutableStateFlow(saved ?: initialMyCar().let { Garage(listOf(it), it.id) })
        garage = _garage.asStateFlow()
        if (saved == null) persist(_garage.value)
        images.deleteUnreferenced(_garage.value)
    }

    override fun snapshot(): Garage = _garage.value

    @Synchronized
    override fun edit(transform: (MyCar) -> MyCar?): Boolean {
        val next = _garage.value.edit(transform) ?: return false
        persist(next)
        return true
    }

    @Synchronized
    override fun selectCar(id: String) = persist(_garage.value.select(id))

    @Synchronized
    override fun addCar(name: String) {
        if (name.isBlank()) return
        persist(_garage.value.add(emptyCar(name, LocalDate.now())))
    }

    @Synchronized
    override fun removeCar(id: String) = persist(_garage.value.remove(id))

    /** The radio changed a car it got from this phone; kept only if the car is still here, with the pictures of this phone. */
    @Synchronized
    fun replaceCar(car: MyCar) = persist(_garage.value.replaceFromRadio(car))

    private fun persist(garage: Garage) {
        if (garage == _garage.value && prefs.contains(KEY_GARAGE)) return
        _garage.value = garage
        prefs.edit().putString(KEY_GARAGE, garage.toJson().toString()).apply()
        // A paper or a car that is gone takes its pictures with it
        images.deleteUnreferenced(garage)
    }

    private fun load(): Garage? {
        prefs.getString(KEY_GARAGE, null)?.let { text ->
            runCatching { garageFromJson(JSONObject(text)) }.getOrNull()?.let { return it }
        }
        // Saved before there were several cars: that one car is the first of the garage
        return prefs.getString(KEY_SINGLE_CAR, null)
            ?.let { text -> runCatching { carFromJson(JSONObject(text)) }.getOrNull() }
            ?.let { Garage(listOf(it), it.id) }
    }

    private companion object {
        const val KEY_GARAGE = "garage"
        const val KEY_SINGLE_CAR = "car"
    }
}
