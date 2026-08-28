package com.example.toolbox.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.LinkProperties
import android.net.RouteInfo
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NetworkInfoReader(context: Context) {
    private val appContext = context.applicationContext

    fun readLocal(): NetworkInfo {
        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkInfo(false, emptyList(), null, null, fallbackAddresses(), emptyList(), emptyList())
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let(connectivityManager::getNetworkCapabilities)
        val linkProperties = activeNetwork?.let(connectivityManager::getLinkProperties)
        val transports = capabilities?.let(::readTransports).orEmpty()
        val localAddresses = linkProperties?.linkAddresses
            ?.mapNotNull { it.address.hostAddress }
            ?.distinct()
            ?.takeUnless { it.isEmpty() }
            ?: fallbackAddresses()
        val gateways = linkProperties?.routes
            ?.filter(RouteInfo::isDefaultRoute)
            ?.mapNotNull { it.gateway?.hostAddress }
            ?.filterNot { it == "0.0.0.0" || it == "::" }
            ?.distinct()
            .orEmpty()
        val dnsServers = linkProperties?.dnsServers
            ?.mapNotNull { it.hostAddress }
            ?.distinct()
            .orEmpty()

        return NetworkInfo(
            isConnected = activeNetwork != null && capabilities != null,
            transports = transports,
            interfaceName = linkProperties?.interfaceName,
            isMetered = activeNetwork?.let { connectivityManager.isActiveNetworkMetered },
            localAddresses = localAddresses,
            dnsServers = dnsServers,
            gateways = gateways,
        )
    }

    suspend fun readPublicIp(): String? = readPublicIpDetails()?.ip

    suspend fun readPublicIpDetails(): PublicIpDetails? = withContext(Dispatchers.IO) {
        fetchUrl(IPWHOIS_URL)?.let { body ->
            runCatching { parseIpWhoIs(body) }.getOrNull()
        } ?: fetchUrl(IPAPICO_URL)?.let { body ->
            runCatching { parseIpApiCo(body) }.getOrNull()
        } ?: fetchUrl(IPIFY_URL)?.let { body ->
            runCatching { parsePlainIp(body) }.getOrNull()
        }
    }

    private fun fetchUrl(urlString: String): String? {
        val connection = runCatching {
            (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = HTTP_TIMEOUT_MILLIS.toInt()
                readTimeout = HTTP_TIMEOUT_MILLIS.toInt()
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("User-Agent", "Toolbox-Android/0.1")
                setRequestProperty("Accept", "application/json, text/plain")
            }
        }.getOrNull() ?: return null

        return try {
            if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun readTransports(capabilities: NetworkCapabilities): List<NetworkTransport> {
        val transports = buildList {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add(NetworkTransport.WIFI)
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add(NetworkTransport.CELLULAR)
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add(NetworkTransport.ETHERNET)
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add(NetworkTransport.VPN)
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add(NetworkTransport.BLUETOOTH)
        }
        return transports.ifEmpty { listOf(NetworkTransport.OTHER) }
    }

    private fun fallbackAddresses(): List<String> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface ->
                Collections.list(networkInterface.inetAddresses)
                    .mapNotNull { it.hostAddress }
            }
            .distinct()
    }.getOrDefault(emptyList())

    companion object {
        const val HTTP_TIMEOUT_MILLIS = 5_000L
        const val IPWHOIS_URL = "https://ipwho.is/"
        const val IPAPICO_URL = "https://ipapi.co/json/"
        const val IPIFY_URL = "https://api64.ipify.org?format=text"

        fun parseIpWhoIs(jsonString: String): PublicIpDetails? {
            val json = org.json.JSONObject(jsonString)
            if (json.has("success") && !json.optBoolean("success", true)) return null
            val ip = json.optString("ip").takeIf(::isIpAddress) ?: return null
            val country = json.optString("country").takeUnless { it.isBlank() || it == "null" }
            val region = json.optString("region").takeUnless { it.isBlank() || it == "null" }
            val city = json.optString("city").takeUnless { it.isBlank() || it == "null" }
            val connection = json.optJSONObject("connection")
            val isp = connection?.optString("isp")?.takeUnless { it.isBlank() || it == "null" }
                ?: connection?.optString("org")?.takeUnless { it.isBlank() || it == "null" }
                ?: json.optString("isp").takeUnless { it.isBlank() || it == "null" }
            return PublicIpDetails(
                ip = ip,
                country = country,
                region = region,
                city = city,
                isp = isp,
            )
        }

        fun parseIpApiCo(jsonString: String): PublicIpDetails? {
            val json = org.json.JSONObject(jsonString)
            if (json.optBoolean("error", false)) return null
            val ip = json.optString("ip").takeIf(::isIpAddress) ?: return null
            val country = json.optString("country_name").takeUnless { it.isBlank() || it == "null" }
            val region = json.optString("region").takeUnless { it.isBlank() || it == "null" }
            val city = json.optString("city").takeUnless { it.isBlank() || it == "null" }
            val isp = json.optString("org").takeUnless { it.isBlank() || it == "null" }
                ?: json.optString("asn").takeUnless { it.isBlank() || it == "null" }
            return PublicIpDetails(
                ip = ip,
                country = country,
                region = region,
                city = city,
                isp = isp,
            )
        }

        fun parsePlainIp(text: String): PublicIpDetails? {
            val ip = text.trim().takeIf(::isIpAddress) ?: return null
            return PublicIpDetails(ip = ip)
        }

        fun isIpAddress(value: String): Boolean {
            val ipv4 = value.split('.')
            if (ipv4.size == 4 && ipv4.all { it.toIntOrNull()?.let { octet -> octet in 0..255 } == true }) {
                return true
            }
            return value.contains(':') && value.matches(Regex("[0-9a-fA-F:]+"))
        }
    }
}
