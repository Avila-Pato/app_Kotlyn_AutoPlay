package org.example.autoplay.session

import java.security.MessageDigest
import java.security.SecureRandom

/** A 6-digit join code that expires after [ttlMillis] and can be redeemed only once. */
class SessionCode(
    val value: String,
    createdAt: Long,
    ttlMillis: Long = TTL_MILLIS,
) {
    val expiresAt: Long = createdAt + ttlMillis

    @Volatile
    private var used = false

    fun isValid(now: Long): Boolean = !used && now < expiresAt

    /** Burns the code and returns true if [candidate] matches and the code is still valid. */
    @Synchronized
    fun redeem(candidate: String, now: Long): Boolean {
        if (!isValid(now)) return false
        val matches = MessageDigest.isEqual(candidate.toByteArray(), value.toByteArray())
        if (matches) used = true
        return matches
    }

    companion object {
        const val TTL_MILLIS = 5 * 60 * 1000L

        private val random = SecureRandom()

        fun generate(now: Long): SessionCode =
            SessionCode("%06d".format(random.nextInt(1_000_000)), now)

        /** Random token the phone uses to resume a session after a dropped connection. */
        fun newResumeToken(): String {
            val bytes = ByteArray(16).also(random::nextBytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
