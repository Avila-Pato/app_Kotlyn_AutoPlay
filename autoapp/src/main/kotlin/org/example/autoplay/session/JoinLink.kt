package org.example.autoplay.session

/** Contents of the QR code shown by the car: `autoplay://join?h=<host>&p=<port>&c=<code>`. */
data class JoinLink(val host: String, val port: Int, val code: String) {

    override fun toString(): String = "$PREFIX?h=$host&p=$port&c=$code"

    companion object {
        private const val PREFIX = "autoplay://join"

        fun parse(text: String): JoinLink? {
            if (!text.startsWith("$PREFIX?")) return null
            val params = text.substringAfter('?').split('&').associate {
                it.substringBefore('=') to it.substringAfter('=', "")
            }
            val host = params["h"]?.takeIf { it.isNotBlank() } ?: return null
            val port = params["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val code = params["c"]?.takeIf { it.length == 6 && it.all(Char::isDigit) } ?: return null
            return JoinLink(host, port, code)
        }
    }
}
