package com.example.toolbox.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class LanScanProgress(
    val phase: LanScanPhase,
    val foundHosts: Int = 0,
    val scannedHosts: Int = 0,
    val totalHosts: Int = 0,
)

enum class LanScanPhase {
    DISCOVERING_HOSTS,
    SCANNING_PORTS,
    LISTENING_SERVICES,
}

data class LanScanResult(
    val devices: List<LanDevice>,
    val scannedHostCount: Int,
    val subnet: String,
)

data class LanSubnet(
    val ownIp: String,
    val prefixLength: Int,
    val interfaceName: String?,
)

/**
 * On-demand LAN probe: ARP table sweep for alive hosts, TCP connect scan of a
 * short port list, plus mDNS/SSDP/ONVIF WS-Discovery service announcements.
 * Runs only when the user taps scan; everything is cancelled with the caller.
 */
class LanScanner(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(ConnectivityManager::class.java)
    private val nsdManager = appContext.getSystemService(NsdManager::class.java)

    fun localSubnet(): LanSubnet? {
        val cm = connectivityManager ?: return null
        val network = cm.activeNetwork ?: return null
        val props = cm.getLinkProperties(network) ?: return null
        val link = props.linkAddresses.firstOrNull {
            it.address is Inet4Address && !it.address.isLoopbackAddress
        } ?: return null
        return LanSubnet(
            ownIp = link.address.hostAddress ?: return null,
            prefixLength = link.prefixLength,
            interfaceName = props.interfaceName,
        )
    }

    fun gateways(): Set<String> {
        val cm = connectivityManager ?: return emptySet()
        val props = cm.activeNetwork?.let(cm::getLinkProperties) ?: return emptySet()
        return props.routes.filter { it.isDefaultRoute }
            .mapNotNull { it.gateway?.hostAddress }
            .toSet()
    }

    @SuppressLint("MissingPermission") // INTERNET + CHANGE_WIFI_MULTICAST_STATE are manifest-held.
    suspend fun scan(onProgress: (LanScanProgress) -> Unit): LanScanResult? =
        withContext(Dispatchers.IO) {
            val subnet = localSubnet() ?: return@withContext null
            val hosts = LanScan.subnetHosts(subnet.ownIp, subnet.prefixLength)
                ?: return@withContext null
            val gatewayIps = gateways()
            val wifi = appContext.getSystemService(WifiManager::class.java)
            val lock = wifi?.createMulticastLock(LOCK_TAG)?.apply {
                setReferenceCounted(true)
                acquire()
            }
            try {
                coroutineScope {
                    val services = ConcurrentHashMap<String, MutableList<LanService>>()
                    val announcements = async {
                        collectAnnouncements(subnet, services)
                    }
                    onProgress(
                        LanScanProgress(
                            LanScanPhase.DISCOVERING_HOSTS,
                            totalHosts = hosts.size,
                        ),
                    )
                    val alive = arpSweep(hosts)
                    onProgress(
                        LanScanProgress(
                            LanScanPhase.DISCOVERING_HOSTS,
                            foundHosts = alive.size,
                            totalHosts = hosts.size,
                        ),
                    )
                    onProgress(
                        LanScanProgress(
                            LanScanPhase.SCANNING_PORTS,
                            foundHosts = alive.size,
                            totalHosts = hosts.size,
                        ),
                    )
                    val devices = scanPorts(alive + subnet.ownIp, onProgress, hosts.size)
                    onProgress(
                        LanScanProgress(
                            LanScanPhase.LISTENING_SERVICES,
                            foundHosts = alive.size,
                            totalHosts = hosts.size,
                        ),
                    )
                    announcements.await()
                    val merged = merge(devices, services, subnet, gatewayIps)
                    LanScanResult(
                        devices = merged,
                        scannedHostCount = hosts.size,
                        subnet = subnetCidr(subnet),
                    )
                }
            } finally {
                runCatching { lock?.release() }
            }
        }

    private fun subnetCidr(subnet: LanSubnet): String {
        val shown = LanScan.subnetHosts(subnet.ownIp, subnet.prefixLength)
            .orEmpty().size
        val prefix = if ((1L shl (32 - subnet.prefixLength)) - 2 > LanScan.MAX_SCAN_HOSTS) {
            24
        } else {
            subnet.prefixLength
        }
        return "${subnet.ownIp}/$prefix ($shown hosts)"
    }

    /** Sends one UDP datagram per host so the kernel resolves ARP, then reads /proc/net/arp. */
    private suspend fun arpSweep(hosts: List<String>): Set<String> {
        val packet = ByteArray(1)
        runCatching {
            DatagramSocket().use { socket ->
                hosts.forEach { host ->
                    runCatching {
                        socket.send(DatagramPacket(packet, packet.size, InetSocketAddress(host, ARP_PROBE_PORT)))
                    }
                }
            }
        }
        delay(ARP_SETTLE_MILLIS)
        val hostSet = hosts.toSet()
        return readArpTable().keys.filter { it in hostSet }.toSet()
    }

    private fun readArpTable(): Map<String, String> = runCatching {
        File("/proc/net/arp").useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val cols = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (cols.size < 4) return@mapNotNull null
                val mac = cols[3]
                if (mac == "00:00:00:00:00:00") return@mapNotNull null
                cols[0] to mac
            }.toMap()
        }
    }.getOrDefault(emptyMap())

    private suspend fun scanPorts(
        ips: Collection<String>,
        onProgress: (LanScanProgress) -> Unit,
        totalHosts: Int,
    ): Map<String, List<Int>> = coroutineScope {
        val results = ConcurrentHashMap<String, List<Int>>()
        val scanned = java.util.concurrent.atomic.AtomicInteger(0)
        val hostGate = Semaphore(HOST_SCAN_PARALLELISM)
        ips.map { ip ->
            async {
                hostGate.withPermit {
                    val open = coroutineScope {
                        LanScan.SCAN_PORTS.map { port ->
                            async(Dispatchers.IO) { if (probeTcp(ip, port)) port else null }
                        }.awaitAll().filterNotNull().sorted()
                    }
                    results[ip] = open
                    val done = scanned.incrementAndGet()
                    onProgress(
                        LanScanProgress(
                            LanScanPhase.SCANNING_PORTS,
                            foundHosts = ips.size,
                            scannedHosts = done,
                            totalHosts = totalHosts,
                        ),
                    )
                }
            }
        }.awaitAll()
        results.toMap()
    }

    private fun probeTcp(ip: String, port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ip, port), TCP_TIMEOUT_MILLIS)
        }
        true
    }.getOrDefault(false)

    private suspend fun collectAnnouncements(
        subnet: LanSubnet,
        out: ConcurrentHashMap<String, MutableList<LanService>>,
    ) = coroutineScope {
        val ssdp = async { ssdpSearch(out) }
        val wsd = async { wsDiscoveryProbe(out) }
        val mdns = async { mdnsDiscover(out) }
        awaitAll(ssdp, wsd, mdns)
    }

    private suspend fun ssdpSearch(out: ConcurrentHashMap<String, MutableList<LanService>>) {
        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = SSDP_RECEIVE_MILLIS.toInt()
                val payload = SSDP_M_SEARCH.toByteArray(Charsets.US_ASCII)
                repeat(2) {
                    runCatching {
                        socket.send(
                            DatagramPacket(
                                payload,
                                payload.size,
                                InetSocketAddress(SSDP_GROUP, SSDP_PORT),
                            ),
                        )
                    }
                    delay(300)
                }
                val deadline = System.currentTimeMillis() + SERVICE_LISTEN_MILLIS
                val buffer = ByteArray(4096)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    val received = runCatching { socket.receive(packet); true }.getOrDefault(false)
                    if (!received) break
                    val from = packet.address?.hostAddress ?: continue
                    val (st, server) = LanScan.parseSsdpResponse(
                        String(packet.data, 0, packet.length),
                    )
                    addService(
                        out,
                        from,
                        LanService(LanServiceSource.SSDP, st ?: "ssdp:all", server, null),
                    )
                }
            }
        }
    }

    private suspend fun wsDiscoveryProbe(out: ConcurrentHashMap<String, MutableList<LanService>>) {
        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = SSDP_RECEIVE_MILLIS.toInt()
                val payload = LanScan.WS_DISCOVERY_PROBE.toByteArray(Charsets.UTF_8)
                runCatching {
                    socket.send(
                        DatagramPacket(
                            payload,
                            payload.size,
                            InetSocketAddress(SSDP_GROUP, WS_DISCOVERY_PORT),
                        ),
                    )
                }
                val deadline = System.currentTimeMillis() + SERVICE_LISTEN_MILLIS
                val buffer = ByteArray(8192)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    val received = runCatching { socket.receive(packet); true }.getOrDefault(false)
                    if (!received) break
                    val from = packet.address?.hostAddress ?: continue
                    val (types, xaddrHosts) = LanScan.parseWsDiscoveryMatch(
                        String(packet.data, 0, packet.length),
                    )
                    val label = types.firstOrNull { it.contains("NetworkVideo") }
                        ?: types.firstOrNull() ?: "ws-discovery"
                    addService(
                        out,
                        from,
                        LanService(LanServiceSource.ONVIF_WS_DISCOVERY, label, null, null),
                    )
                    xaddrHosts.forEach { host ->
                        addService(
                            out,
                            host,
                            LanService(LanServiceSource.ONVIF_WS_DISCOVERY, label, null, null),
                        )
                    }
                }
            }
        }
    }

    private fun addService(
        out: ConcurrentHashMap<String, MutableList<LanService>>,
        ip: String,
        service: LanService,
    ) {
        val list = out.getOrPut(ip) { java.util.Collections.synchronizedList(mutableListOf()) }
        synchronized(list) {
            if (list.none {
                    it.type == service.type && it.name == service.name &&
                        it.source == service.source
                }
            ) {
                list += service
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun mdnsDiscover(out: ConcurrentHashMap<String, MutableList<LanService>>) {
        val nsd = nsdManager ?: return
        val found = ConcurrentHashMap.newKeySet<NsdServiceInfo>()
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()
        try {
            LanScan.MDNS_TYPES.forEach { type ->
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) = Unit
                    override fun onStartDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) = Unit

                    override fun onDiscoveryStopped(serviceType: String) = Unit
                    override fun onStopDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) = Unit

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        found += serviceInfo
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
                }
                runCatching {
                    nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                    listeners += listener
                }
            }
            delay(MDNS_LISTEN_MILLIS)
        } finally {
            listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        }
        val resolveGate = Semaphore(4)
        coroutineScope {
            found.take(MAX_MDNS_RESOLVES).map { info ->
                async {
                    resolveGate.withPermit {
                        val resolved = withTimeoutOrNull(1500) { resolve(nsd, info) }
                        val host = resolved?.host?.hostAddress ?: return@withPermit
                        addService(
                            out,
                            host,
                            LanService(
                                source = LanServiceSource.MDNS,
                                type = resolved.serviceType,
                                name = resolved.serviceName,
                                port = resolved.port,
                            ),
                        )
                    }
                }
            }.awaitAll()
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(
        nsd: NsdManager,
        info: NsdServiceInfo,
    ): NsdServiceInfo? = suspendCancellableCoroutine { cont ->
        runCatching {
            nsd.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        cont.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        cont.resume(serviceInfo)
                    }
                },
            )
        }.onFailure { cont.resume(null) }
    }

    private fun merge(
        openPorts: Map<String, List<Int>>,
        services: Map<String, List<LanService>>,
        subnet: LanSubnet,
        gateways: Set<String>,
    ): List<LanDevice> {
        val arp = readArpTable()
        val ips = (openPorts.keys + services.keys + subnet.ownIp).toSet()
        return ips.map { ip ->
            val deviceServices = services[ip].orEmpty()
            LanDevice(
                ip = ip,
                mac = arp[ip],
                hostname = deviceServices
                    .mapNotNull { it.name }
                    .firstOrNull { it.isNotBlank() },
                openPorts = openPorts[ip].orEmpty(),
                services = deviceServices,
                isSelf = ip == subnet.ownIp,
                isGateway = ip in gateways,
            )
        }.sortedWith(
            compareByDescending<LanDevice> { LanScan.suspicion(it).ordinal }
                .thenBy { ipSortKey(it.ip) },
        )
    }

    private fun ipSortKey(ip: String): Long =
        ip.split('.').fold(0L) { acc, part -> (acc shl 8) or (part.toLongOrNull() ?: 0L) }

    companion object {
        private const val LOCK_TAG = "toolbox_lan_scan"
        private const val ARP_PROBE_PORT = 45678
        private const val ARP_SETTLE_MILLIS = 1_200L
        private const val TCP_TIMEOUT_MILLIS = 350
        private const val HOST_SCAN_PARALLELISM = 8
        private const val SSDP_GROUP = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val WS_DISCOVERY_PORT = 3702
        private const val SSDP_RECEIVE_MILLIS = 400L
        private const val SERVICE_LISTEN_MILLIS = 3_500L
        private const val MDNS_LISTEN_MILLIS = 4_500L
        private const val MAX_MDNS_RESOLVES = 40
        private const val SSDP_M_SEARCH =
            "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: ssdp:all\r\n\r\n"
    }
}
