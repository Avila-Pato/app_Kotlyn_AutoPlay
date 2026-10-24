package org.example.autoplay.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class DiscoveredCar(val name: String, val host: String, val port: Int)

/** Advertises the car on the local network and lets the phone find it (Network Service Discovery). */
@Singleton
class CarDiscovery @Inject constructor(@ApplicationContext context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)

    /** Publishes the car service until the returned handle is closed. */
    fun register(name: String, port: Int): Closeable {
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = Protocol.SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {
                Log.w(TAG, "NSD registration failed: $error")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        return Closeable { runCatching { nsd.unregisterService(listener) } }
    }

    /** Cars currently visible on the network; the list updates as they appear and disappear. */
    fun discover(): Flow<List<DiscoveredCar>> = callbackFlow {
        val cars = linkedMapOf<String, DiscoveredCar>()
        // NsdManager resolves one service at a time, so found services are queued and resolved in order.
        val toResolve = Channel<NsdServiceInfo>(Channel.UNLIMITED)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                toResolve.trySend(info)
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                synchronized(cars) {
                    cars.remove(info.serviceName)
                    trySend(cars.values.toList())
                }
            }
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, error: Int) {
                Log.w(TAG, "NSD discovery failed: $error")
            }
            override fun onStopDiscoveryFailed(serviceType: String, error: Int) = Unit
        }

        launch {
            for (info in toResolve) {
                val car = resolve(info) ?: continue
                synchronized(cars) {
                    cars[car.name] = car
                    trySend(cars.values.toList())
                }
            }
        }

        trySend(emptyList())
        nsd.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }

    @Suppress("DEPRECATION") // resolveService is the only option below API 34
    private suspend fun resolve(info: NsdServiceInfo): DiscoveredCar? =
        suspendCancellableCoroutine { cont ->
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val host = resolved.host?.hostAddress
                    cont.resume(host?.let { DiscoveredCar(resolved.serviceName, it, resolved.port) })
                }
                override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                    cont.resume(null)
                }
            })
        }

    private companion object {
        const val TAG = "CarDiscovery"
    }
}
