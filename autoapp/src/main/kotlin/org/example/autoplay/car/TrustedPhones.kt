package org.example.autoplay.car

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.example.autoplay.session.SessionCode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phones the driver already approved once. They come back with their token and join without
 * a code or "Permitir"; removing a phone from the car screen forgets it.
 */
@Singleton
class TrustedPhones @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("trusted_phones", Context.MODE_PRIVATE)

    /** Lets a phone tell this car apart from others it knows. */
    val carId: String = prefs.getString(KEY_CAR_ID, null)
        ?: SessionCode.newResumeToken().also { prefs.edit().putString(KEY_CAR_ID, it).apply() }

    fun isTrusted(token: String): Boolean = token.isNotEmpty() && prefs.contains(PREFIX + token)

    /** Returns the token the phone must keep. */
    fun trust(deviceName: String): String =
        SessionCode.newResumeToken().also { prefs.edit().putString(PREFIX + it, deviceName).apply() }

    fun forget(token: String) {
        prefs.edit().remove(PREFIX + token).remove(ALIAS + token).apply()
    }

    /** The name the driver gave this phone from the car screen, which wins over the phone's own. */
    fun aliasFor(token: String): String? = if (token.isEmpty()) null else prefs.getString(ALIAS + token, null)

    fun setAlias(token: String, name: String) {
        if (token.isNotEmpty()) prefs.edit().putString(ALIAS + token, name).apply()
    }

    private companion object {
        const val KEY_CAR_ID = "car_id"
        const val PREFIX = "phone_"
        const val ALIAS = "alias_"
    }
}
