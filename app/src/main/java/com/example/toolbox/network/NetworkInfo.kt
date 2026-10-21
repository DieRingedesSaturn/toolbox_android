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

enum class PublicIpRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
}

data class PublicIpDetails(
    val ip: String,
    val country: String? = null,
    val countryCode: String? = null,
    val region: String? = null,
    val city: String? = null,
    val postal: String? = null,
    val continent: String? = null,
    val timezone: String? = null,
    val ipType: String? = null,
    val isp: String? = null,
    val organization: String? = null,
    val asn: String? = null,
    val domain: String? = null,
    val hostname: String? = null,
    val ipRange: String? = null,
    val networkType: String? = null,
    val proxy: Boolean? = null,
    val risk: Int? = null,
    val source: String? = null,
) {
    fun formatLocation(): String? {
        val parts = listOfNotNull(
            country?.takeUnless { it.isBlank() },
            region?.takeUnless { it.isBlank() },
            city?.takeUnless { it.isBlank() },
        ).distinct()
        return parts.joinToString(", ").takeUnless { it.isBlank() }
    }

    /** proxycheck.io bands: up to 33 low, 34–66 medium, above 66 high. */
    fun riskLevel(): PublicIpRiskLevel? = risk?.let { score ->
        when {
            score <= 33 -> PublicIpRiskLevel.LOW
            score <= 66 -> PublicIpRiskLevel.MEDIUM
            else -> PublicIpRiskLevel.HIGH
        }
    }
}
