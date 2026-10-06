package com.example.toolbox.network

import java.net.Inet4Address
import java.net.InetAddress

/**
 * LAN device discovery + camera-heuristic data. Pure logic lives here so it
 * can be unit tested without Android sockets.
 */
enum class LanServiceSource {
    MDNS,
    SSDP,
    ONVIF_WS_DISCOVERY,
}

data class LanService(
    val source: LanServiceSource,
    val type: String,
    val name: String?,
    val port: Int?,
)

data class LanDevice(
    val ip: String,
    val mac: String? = null,
    val hostname: String? = null,
    val openPorts: List<Int> = emptyList(),
    val services: List<LanService> = emptyList(),
    val isSelf: Boolean = false,
    val isGateway: Boolean = false,
)

/** Why a device was flagged; the UI renders each flag's text. */
enum class LanFlag {
    ONVIF_SERVICE,
    RTSP_PORT,
    CAMERA_VENDOR_PORT,
    CAMERA_VENDOR_NIC,
    WEB_ADMIN_ONLY,
}

enum class LanSuspicion {
    NONE,
    NOTEWORTHY,
    LIKELY_CAMERA,
}

object LanScan {

    /** TCP ports probed on every live host. */
    val RTSP_PORTS = setOf(554, 8554)
    val CAMERA_VENDOR_PORTS = mapOf(
        37777 to "Dahua/Amcrest",
        34567 to "XiongMai",
        9527 to "XiongMai",
        8899 to "ONVIF",
        8000 to "Hikvision",
        9000 to "ONVIF alt",
        6036 to "ONVIF alt",
    )
    val GENERIC_PORTS = listOf(
        22, 80, 443, 445, 548, 631, 5000, 5357,
        62078, 7000, 8008, 8080, 8081, 8443, 9100,
    )
    val SCAN_PORTS: List<Int> =
        (RTSP_PORTS + CAMERA_VENDOR_PORTS.keys + GENERIC_PORTS).sorted()
    val WEB_ADMIN_PORTS = setOf(80, 443, 8008, 8080, 8081, 8443)

    /** Service types asked of mDNS / matched inside SSDP+WSD results. */
    val MDNS_TYPES = listOf(
        "_rtsp._tcp.", "_onvif._tcp.", "_http._tcp.", "_device-info._tcp.",
        "_googlecast._tcp.", "_airplay._tcp.", "_ipp._tcp.", "_printer._tcp.",
        "_smb._tcp.", "_workstation._tcp.", "_hap._tcp.", "_sonos._tcp.",
        "_companion-link._tcp.", "_amzn-wplay._tcp.",
    )
    val CAMERA_SERVICE_HINTS = listOf("onvif", "nvt", "networkvideo", "ipcamera", "rtsp")

    /** Selected OUI prefixes of camera/IoT vendors (first 3 bytes, uppercase). */
    val CAMERA_OUI = mapOf(
        "44:19:B6" to "Hikvision", "C0:56:E3" to "Hikvision", "54:C4:15" to "Hikvision",
        "3C:E3:8B" to "Dahua", "E0:50:8B" to "Dahua",
        "00:40:8C" to "Axis", "AC:CC:8E" to "Axis",
        "00:02:D1" to "Vivotek",
        "EC:71:DB" to "Reolink",
        "2C:AA:8E" to "Wyze", "D0:3F:27" to "Wyze",
        "78:45:58" to "Ubiquiti", "68:D7:9A" to "Ubiquiti", "F0:9F:C2" to "Ubiquiti",
        "E0:63:DA" to "Ubiquiti", "B4:FB:E4" to "Ubiquiti",
        "50:C7:BF" to "TP-Link/Tapo", "F4:F2:6D" to "TP-Link/Tapo",
        "EC:08:6B" to "TP-Link/Tapo", "B0:4E:26" to "TP-Link/Tapo",
        "14:EB:B6" to "TP-Link/Tapo", "60:32:B1" to "TP-Link/Tapo",
        "5C:62:8B" to "TP-Link/Tapo", "30:DE:4B" to "TP-Link/Tapo",
        "64:CC:2E" to "Xiaomi", "50:EC:50" to "Xiaomi", "78:11:DC" to "Xiaomi",
        "F0:B4:29" to "Xiaomi", "8C:BE:BE" to "Xiaomi", "7C:1D:D9" to "Xiaomi",
        "34:CE:00" to "Xiaomi", "9C:99:A0" to "Xiaomi", "28:6C:07" to "Xiaomi",
        "04:CF:8C" to "Xiaomi",
        "B0:C5:54" to "D-Link", "28:10:7B" to "D-Link", "1C:7E:E5" to "D-Link",
        "90:8D:78" to "D-Link",
    )

    const val MAX_SCAN_HOSTS = 1024

    /**
     * Host addresses inside the subnet [ip]/[prefixLength], excluding the
     * network and broadcast addresses. Huge subnets are narrowed to the /24
     * that contains [ip]; returns null for unusable input.
     */
    fun subnetHosts(ip: String, prefixLength: Int, maxHosts: Int = MAX_SCAN_HOSTS): List<String>? {
        val addr = runCatching { InetAddress.getByName(ip) as? Inet4Address }.getOrNull()
            ?: return null
        if (prefixLength !in 8..30) return null
        val ipInt = addr.address.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        val hostBits = 32 - prefixLength
        val size = 1L shl hostBits
        val hosts = if (size - 2 > maxHosts) {
            // Narrow to the /24 that contains our own address.
            val base24 = ipInt and 0xFFFFFF00L
            (1L..254L).map { base24 + it }
        } else {
            val network = ipInt and ((-1L shl hostBits) and 0xFFFFFFFFL)
            (1L until size - 1).map { network + it }
        }
        return hosts.map { value ->
            (0..3).joinToString(".") { shift ->
                ((value shr ((3 - shift) * 8)) and 0xFF).toString()
            }
        }
    }

    /** OUI vendor guess from a MAC like "aa:bb:cc:dd:ee:ff"; null when unknown. */
    fun cameraVendorForMac(mac: String?): String? {
        val prefix = mac?.uppercase()?.split(':', '-')
            ?.takeIf { it.size >= 3 }
            ?.take(3)?.joinToString(":") ?: return null
        return CAMERA_OUI[prefix]
    }

    fun flagsFor(device: LanDevice): Set<LanFlag> {
        val flags = mutableSetOf<LanFlag>()
        val serviceText = device.services
            .joinToString(" ") { "${it.type} ${it.name.orEmpty()}" }
            .lowercase()
        if (device.services.any { it.source == LanServiceSource.ONVIF_WS_DISCOVERY } ||
            CAMERA_SERVICE_HINTS.any { hint -> serviceText.contains(hint) }
        ) {
            flags += LanFlag.ONVIF_SERVICE
        }
        if (device.openPorts.any { it in RTSP_PORTS }) flags += LanFlag.RTSP_PORT
        if (device.openPorts.any { it in CAMERA_VENDOR_PORTS }) {
            flags += LanFlag.CAMERA_VENDOR_PORT
        }
        if (cameraVendorForMac(device.mac) != null) flags += LanFlag.CAMERA_VENDOR_NIC
        if (flags.isEmpty() && device.openPorts.any { it in WEB_ADMIN_PORTS }) {
            flags += LanFlag.WEB_ADMIN_ONLY
        }
        return flags
    }

    fun suspicion(device: LanDevice): LanSuspicion {
        val flags = flagsFor(device)
        return when {
            flags.any {
                it == LanFlag.ONVIF_SERVICE || it == LanFlag.RTSP_PORT ||
                    it == LanFlag.CAMERA_VENDOR_PORT
            } -> LanSuspicion.LIKELY_CAMERA
            flags.contains(LanFlag.CAMERA_VENDOR_NIC) -> LanSuspicion.NOTEWORTHY
            else -> LanSuspicion.NONE
        }
    }

    /** Parses an SSDP M-SEARCH response; returns the interesting headers. */
    fun parseSsdpResponse(text: String): Pair<String?, String?> {
        var st: String? = null
        var server: String? = null
        text.lineSequence().forEach { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) return@forEach
            val key = line.substring(0, idx).trim().uppercase()
            val value = line.substring(idx + 1).trim()
            when (key) {
                "ST", "NT" -> if (st == null) st = value
                "SERVER" -> server = value
            }
        }
        return st to server
    }

    const val WS_DISCOVERY_PROBE = """<?xml version="1.0" encoding="UTF-8"?>
<e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope" xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:dn="http://www.onvif.org/ver10/network/wsdl"><e:Header><w:MessageID>uuid:4d5f6a7b-8c9d-4e5f-8a9b-0c1d2e3f4a5b</w:MessageID><w:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To><w:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action></e:Header><e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></e:Body></e:Envelope>"""

    private val XADDRS_PATTERN =
        Regex("<[^>]*XAddrs[^>]*>([^<]+)</[^>]*XAddrs>", RegexOption.IGNORE_CASE)
    private val TYPES_PATTERN =
        Regex("<[^>]*Types[^>]*>([^<]+)</[^>]*Types>", RegexOption.IGNORE_CASE)
    private val HOST_PATTERN =
        Regex("https?://(\\[?[0-9a-fA-F:.]+]?)[/:\\s]")

    /**
     * Parses a WS-Discovery ProbeMatch body: returns advertised device types
     * plus every http(s) host found in XAddrs.
     */
    fun parseWsDiscoveryMatch(xml: String): Pair<List<String>, List<String>> {
        val types = TYPES_PATTERN.findAll(xml)
            .flatMap { it.groupValues[1].split(Regex("\\s+")).asSequence() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        val hosts = XADDRS_PATTERN.findAll(xml)
            .flatMap { HOST_PATTERN.findAll(it.groupValues[1]) }
            .map { it.groupValues[1].trim('[', ']') }
            .distinct()
            .toList()
        return types to hosts
    }
}
