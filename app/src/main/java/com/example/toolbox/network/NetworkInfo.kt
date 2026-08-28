package com.example.toolbox.network

enum class NetworkTransport {
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    BLUETOOTH,
    OTHER,
}

data class NetworkInfo(
    val isConnected: Boolean,
    val transports: List<NetworkTransport>,
    val interfaceName: String?,
    val isMetered: Boolean?,
    val localAddresses: List<String>,
    val dnsServers: List<String>,
    val gateways: List<String>,
)

data class PublicIpDetails(
    val ip: String,
    val country: String? = null,
    val region: String? = null,
    val city: String? = null,
    val isp: String? = null,
) {
    fun formatLocation(): String? {
        val parts = listOfNotNull(
            country?.takeUnless { it.isBlank() },
            region?.takeUnless { it.isBlank() },
            city?.takeUnless { it.isBlank() },
        ).distinct()
        return parts.joinToString(", ").takeUnless { it.isBlank() }
    }
}
