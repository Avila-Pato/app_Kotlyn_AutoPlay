package org.example.autoplay.net

import android.bluetooth.BluetoothSocket
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.Timer
import java.util.TimerTask

/**
 * A framed message connection over Wi-Fi (TCP) or Bluetooth (RFCOMM); [send] may be called
 * from several coroutines at once.
 */
class Connection private constructor(
    input: InputStream,
    output: OutputStream,
    private val closeable: Closeable,
    val remoteAddress: String?,
    val isBluetooth: Boolean,
) : Closeable {
    constructor(socket: Socket) : this(
        socket.getInputStream(),
        socket.getOutputStream(),
        socket,
        socket.inetAddress?.hostAddress,
        isBluetooth = false,
    ) {
        socket.tcpNoDelay = true
    }

    constructor(socket: BluetoothSocket) : this(
        socket.inputStream,
        socket.outputStream,
        socket,
        "Bluetooth",
        isBluetooth = true,
    )

    private val input = DataInputStream(BufferedInputStream(input))
    private val output = DataOutputStream(BufferedOutputStream(output))

    /** Start of the read in progress, or 0; checked by the watchdog because Bluetooth sockets have no read timeout. */
    @Volatile private var readingSince = 0L
    private val watchdog = object : TimerTask() {
        override fun run() {
            val since = readingSince
            if (since != 0L && readTimeoutMillis > 0 && System.currentTimeMillis() - since > readTimeoutMillis) close()
        }
    }

    init {
        timer.schedule(watchdog, WATCHDOG_PERIOD_MILLIS, WATCHDOG_PERIOD_MILLIS)
    }

    /** A blocked [receive] fails after this long without a complete message; 0 waits forever. */
    @Volatile var readTimeoutMillis: Int = 0

    fun send(message: Message) = synchronized(output) { Protocol.write(output, message) }

    /** Blocks until a message arrives; throws on timeout or when the connection is closed. */
    fun receive(): Message {
        readingSince = System.currentTimeMillis()
        try {
            return Protocol.read(input)
        } finally {
            readingSince = 0L
        }
    }

    /** Sends a last message without failing if the connection is already broken, then closes. */
    fun closeWith(message: Message) {
        runCatching { send(message) }
        close()
    }

    override fun close() {
        watchdog.cancel()
        runCatching { closeable.close() }
    }

    private companion object {
        const val WATCHDOG_PERIOD_MILLIS = 500L
        val timer = Timer("connection-watchdog", true)
    }
}
