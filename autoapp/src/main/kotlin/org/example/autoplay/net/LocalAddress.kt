package org.example.autoplay.net

import java.net.Inet4Address
import java.net.NetworkInterface

/** First private IPv4 address of this device (Wi-Fi or hotspot), shown to the phone for manual connection. */
fun localIpv4Address(): String? =
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
