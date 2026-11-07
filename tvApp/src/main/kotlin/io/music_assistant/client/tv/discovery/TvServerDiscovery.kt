package io.music_assistant.client.tv.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.ConnectionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import kotlin.coroutines.resume

/** A Music Assistant server, as its `/info` page describes it. */
data class DiscoveredServer(
    val serverId: String,
    val name: String?,
    val host: String,
    val port: Int,
    val version: String?,
    val homeAssistantAddon: Boolean,
) {
    val connectionInfo: ConnectionInfo get() = ConnectionInfo(host, port, isTls = false)
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: "$host:$port"
}

private val log = Logger.withTag("TvDiscovery")

/**
 * Finds the Music Assistant servers of the local network. Each server announces itself over mDNS
 * (`_mass._tcp`). A network that drops multicast hides them, so when mDNS finds nothing for a few
 * seconds, the TV also asks every address of its own /24 subnet for `/info` on the default port.
 */
class TvServerDiscovery(private val context: Context) {

    fun servers(): Flow<List<DiscoveredServer>> = channelFlow {
        val found = LinkedHashMap<String, DiscoveredServer>()
        val lock = Mutex()
        suspend fun add(server: DiscoveredServer) {
            val list = lock.withLock {
                if (found[server.serverId] == server) return
                found[server.serverId] = server
                found.values.toList()
            }
            send(list)
        }
        send(emptyList())

        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        val resolveLock = Mutex()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                launch {
                    // One resolution at a time: NsdManager refuses a second one in flight.
                    val resolved = resolveLock.withLock { nsd?.resolve(service) } ?: return@launch
                    // Every server announces the same host name (mass.local), so the resolved
                    // address can belong to another server: the TXT URLs of the server come first,
                    // and only an answer with the announced server id counts.
                    val candidates = listOfNotNull(
                        resolved.txtUrl("base_url"),
                        resolved.txtUrl("internal_url"),
                        resolved.host?.hostAddress?.takeIf { resolved.port > 0 }?.let { it to resolved.port },
                    ).distinct()
                    log.i { "mDNS: ${service.serviceName} at ${candidates.joinToString()}" }
                    candidates.firstNotNullOfOrNull { (host, port) ->
                        probeServerInfo(host, port)?.takeIf { it.serverId == service.serviceName }
                    }?.let { add(it) }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(serviceType: String) = log.i { "mDNS discovery started" }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) =
                log.w { "mDNS discovery failed: $errorCode" }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        val started = runCatching {
            nsd?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.isSuccess && nsd != null

        launch {
            delay(SCAN_FALLBACK_DELAY_MS)
            if (lock.withLock { found.isEmpty() }) scanSubnets { add(it) }
        }

        awaitClose {
            if (started) runCatching { nsd?.stopServiceDiscovery(listener) }
        }
    }

    @Suppress("DEPRECATION") // resolveService works on every API level the app supports.
    private suspend fun NsdManager.resolve(service: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { cont ->
            runCatching {
                resolveService(
                    service,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            if (cont.isActive) cont.resume(null)
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            if (cont.isActive) cont.resume(info)
                        }
                    },
                )
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }

    /** A plain `http://host:port` URL of the TXT record, as a host and a port. */
    private fun NsdServiceInfo.txtUrl(key: String): Pair<String, Int>? = runCatching {
        val url = URL(attributes[key]?.toString(Charsets.UTF_8) ?: return null)
        if (url.protocol != "http" || url.path.trim('/').isNotEmpty()) return null
        url.host to url.port.takeIf { it > 0 }.let { it ?: url.defaultPort }
    }.getOrNull()

    private suspend fun scanSubnets(onFound: suspend (DiscoveredServer) -> Unit) = withContext(Dispatchers.IO) {
        val prefixes = localIpv4Addresses().map { it.substringBeforeLast('.') }.distinct()
        log.i { "Scanning ${prefixes.joinToString { "$it.0/24" }} for port $DEFAULT_PORT" }
        val permits = Semaphore(SCAN_PARALLELISM)
        coroutineScope {
            prefixes.forEach { prefix ->
                (1..254).forEach { last ->
                    launch {
                        permits.withPermit {
                            val host = "$prefix.$last"
                            if (isPortOpen(host, DEFAULT_PORT)) probeServerInfo(host, DEFAULT_PORT)?.let { onFound(it) }
                        }
                    }
                }
            }
        }
    }

    private fun isPortOpen(host: String, port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS) }
        true
    }.getOrDefault(false)

    private companion object {
        const val SERVICE_TYPE = "_mass._tcp."
        const val DEFAULT_PORT = 8095
        const val SCAN_FALLBACK_DELAY_MS = 4_000L
        const val SCAN_PARALLELISM = 48
        const val CONNECT_TIMEOUT_MS = 350
    }
}

suspend fun probeServerInfo(connection: ConnectionInfo): DiscoveredServer? =
    probeServerInfo(connection.host, connection.port, connection.webUrl)

/** Reads the public `/info` page of a server; null when nothing answers like Music Assistant. */
suspend fun probeServerInfo(host: String, port: Int, baseUrl: String = "http://$host:$port"): DiscoveredServer? =
    withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL("$baseUrl/info").openConnection() as HttpURLConnection
            connection.connectTimeout = PROBE_TIMEOUT_MS
            connection.readTimeout = PROBE_TIMEOUT_MS
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val serverId = json.optString("server_id").takeIf { it.isNotBlank() } ?: return@runCatching null
                DiscoveredServer(
                    serverId = serverId,
                    name = json.optString("name").takeIf { it.isNotBlank() },
                    host = host,
                    port = port,
                    version = json.optString("server_version").takeIf { it.isNotBlank() },
                    homeAssistantAddon = json.optBoolean("homeassistant_addon", false),
                )
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

/** The IPv4 addresses of this device on the local network. */
fun localIpv4Addresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filter { it.isSiteLocalAddress }
        .mapNotNull { it.hostAddress }
}.getOrDefault(emptyList())

/**
 * The address of the TV that a phone on the network of the server can reach: the local address in
 * the same /24 as the server. When no interface is on that network (an emulator behind its NAT, a
 * VPN), the system property `debug.ma.tv.lan_host` gives the address to show, for a development
 * setup that relays the page; otherwise the first local address.
 */
suspend fun lanAddressFor(serverHost: String?): String? = withContext(Dispatchers.IO) {
    val local = localIpv4Addresses()
    val serverIp = serverHost?.let { host ->
        runCatching { java.net.InetAddress.getAllByName(host).filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress }
            .getOrNull()
    }
    val prefix = serverIp?.substringBeforeLast('.')
    local.firstOrNull { prefix != null && it.substringBeforeLast('.') == prefix }
        ?: systemProperty(LAN_HOST_PROPERTY)?.takeIf { it.isNotBlank() }
        ?: local.firstOrNull()
}

private fun systemProperty(name: String): String? = runCatching {
    Runtime.getRuntime().exec(arrayOf("getprop", name)).inputStream.bufferedReader().use { it.readText().trim() }
}.getOrNull()

private const val LAN_HOST_PROPERTY = "debug.ma.tv.lan_host"
private const val PROBE_TIMEOUT_MS = 2_000
