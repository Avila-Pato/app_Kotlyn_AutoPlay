package org.example.autoplay.phone

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** A car that already approved this phone: reconnecting needs no code. */
data class KnownCar(
    val id: String,
    val name: String,
    val trust: String,
    /** How the phone reached it last time. */
    val target: CarTarget,
    val lastUsed: Long,
)

/** The cars this phone joined before, most recent first. */
@Singleton
class KnownCars @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("known_cars", Context.MODE_PRIVATE)
    private val _cars = MutableStateFlow(load())
    val cars: StateFlow<List<KnownCar>> = _cars.asStateFlow()

    /** The token to present to [target], if it is a car we know; the car checks it, not us. */
    fun trustFor(target: CarTarget): String = _cars.value.firstOrNull { it.target.sameCarAs(target) }?.trust.orEmpty()

    @Synchronized
    fun save(car: KnownCar) {
        persist(listOf(car) + _cars.value.filter { it.id != car.id })
    }

    @Synchronized
    fun forget(id: String) {
        persist(_cars.value.filter { it.id != id })
    }

    @Synchronized
    fun forgetTrust(trust: String) {
        persist(_cars.value.filter { it.trust != trust })
    }

    private fun persist(cars: List<KnownCar>) {
        _cars.value = cars.sortedByDescending { it.lastUsed }
        val json = JSONArray()
        cars.forEach { car ->
            json.put(
                JSONObject()
                    .put("id", car.id)
                    .put("name", car.name)
                    .put("trust", car.trust)
                    .put("lastUsed", car.lastUsed)
                    .put("target", car.target.toJson()),
            )
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun load(): List<KnownCar> {
        val json = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrNull() ?: return emptyList()
        return (0 until json.length()).mapNotNull { i ->
            runCatching {
                val o = json.getJSONObject(i)
                KnownCar(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    trust = o.getString("trust"),
                    target = targetFromJson(o.getJSONObject("target")),
                    lastUsed = o.optLong("lastUsed"),
                )
            }.getOrNull()
        }.sortedByDescending { it.lastUsed }
    }

    private fun CarTarget.toJson(): JSONObject = when (this) {
        is CarTarget.Wifi -> JSONObject().put("type", "wifi").put("host", host).put("port", port)
        is CarTarget.Bluetooth -> JSONObject().put("type", "bt").put("address", address).put("name", name)
    }

    private fun targetFromJson(o: JSONObject): CarTarget = when (o.getString("type")) {
        "bt" -> CarTarget.Bluetooth(o.getString("address"), o.getString("name"))
        else -> CarTarget.Wifi(o.getString("host"), o.getInt("port"))
    }

    /** Bluetooth addresses are stable; on Wi-Fi the IP is the best hint we have. */
    private fun CarTarget.sameCarAs(other: CarTarget) = when {
        this is CarTarget.Bluetooth && other is CarTarget.Bluetooth -> address == other.address
        this is CarTarget.Wifi && other is CarTarget.Wifi -> host == other.host
        else -> false
    }

    private companion object {
        const val KEY = "cars"
    }
}
