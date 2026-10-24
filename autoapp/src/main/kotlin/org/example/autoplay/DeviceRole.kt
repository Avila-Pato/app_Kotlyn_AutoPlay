package org.example.autoplay

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

enum class DeviceRole { Car, Phone }

/**
 * Whether this device is the car or a phone, so nobody has to answer "¿En qué equipo estás?".
 * The user's own choice always wins; otherwise the hardware tells: a small screen with a SIM is a
 * phone, a head unit or a tablet without one (a modded tablet on the dashboard) is the car.
 */
object DeviceRoles {
    private const val PREFS = "device"
    private const val KEY_ROLE = "role"

    /** Null when it cannot tell, e.g. a large tablet with a SIM: the home screen asks once. */
    fun resolve(context: Context): DeviceRole? = saved(context) ?: detect(context)

    fun save(context: Context, role: DeviceRole) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ROLE, role.name).apply()
    }

    private fun saved(context: Context): DeviceRole? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ROLE, null)
            ?.let { name -> DeviceRole.entries.find { it.name == name } }

    private fun detect(context: Context): DeviceRole? {
        val pm = context.packageManager
        val carMode = context.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_CAR
        if (pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) || carMode) return DeviceRole.Car
        val hasSim = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val large = context.resources.configuration.smallestScreenWidthDp >= LARGE_SCREEN_DP
        return when {
            hasSim && !large -> DeviceRole.Phone
            !hasSim && large -> DeviceRole.Car
            else -> null
        }
    }

    private const val LARGE_SCREEN_DP = 600
}
