package org.example.autoplay.car

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/** Apps the car screen offers until the driver picks their own. */
val DEFAULT_CAR_APPS = listOf(
    "com.waze",
    "com.google.android.apps.maps",
    "com.spotify.music",
    "com.google.android.apps.youtube.music",
)

/** Maps apps get the "Navegar" button: the map full screen with AutoPlay as a floating mini player. */
val NAVIGATION_APPS = setOf("com.waze", "com.google.android.apps.maps")

/** Adds [pkg] at the end or removes it, so the order the driver already has is kept. */
fun togglePackage(current: List<String>, pkg: String): List<String> =
    if (pkg in current) current - pkg else current + pkg

/** The apps shown in the car's side panel, in the order the driver chose. */
@Singleton
class CarAppsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("car", Context.MODE_PRIVATE)
    private val _packages = MutableStateFlow(load())
    val packages: StateFlow<List<String>> = _packages.asStateFlow()

    @Synchronized
    fun setPackages(list: List<String>) {
        val clean = list.distinct()
        _packages.value = clean
        prefs.edit().putString(KEY, JSONArray(clean).toString()).apply()
    }

    private fun load(): List<String> {
        val text = prefs.getString(KEY, null) ?: return DEFAULT_CAR_APPS
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(DEFAULT_CAR_APPS)
    }

    private companion object {
        const val KEY = "panel_apps"
    }
}
