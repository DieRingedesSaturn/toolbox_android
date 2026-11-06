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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

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

    /** Explicit GeoIP action; no proxy/risk requests and no device coordinates sent. */
    suspend fun readPublicIpLocation(): PublicIpLocation? = withContext(Dispatchers.IO) {
        for ((url, source) in listOf(IPWHOIS_URL to "ipwho.is", IPAPICO_URL to "ipapi.co")) {
            currentCoroutineContext().ensureActive()
            val body = fetchUrl(url)
            currentCoroutineContext().ensureActive()
            val result = body?.let { runCatching { parseIpLocation(it, source) }.getOrNull() }
            if (result != null) return@withContext result
        }
        null
    }

    suspend fun readPublicIpDetails(): PublicIpDetails? = withContext(Dispatchers.IO) {
        val base = fetchUrl(IPWHOIS_URL)?.let { body ->
            runCatching { parseIpWhoIs(body) }.getOrNull()
        } ?: fetchUrl(IPAPICO_URL)?.let { body ->
            runCatching { parseIpApiCo(body) }.getOrNull()
        } ?: fetchUrl(IPIFY_URL)?.let { body ->
            runCatching { parsePlainIp(body) }.getOrNull()
        } ?: return@withContext null

        // The free, keyless proxycheck tier adds usage type, proxy/VPN and a risk score.
        // If it is rate-limited or unreachable, the core IP data above still stands.
        val extra = fetchUrl(
            "$PROXYCHECK_URL${base.ip}?vpn=1&asn=1&risk=1",
        )?.let { body ->
            runCatching { parseProxyCheck(body) }.getOrNull()
        }
        when {
            extra == null -> base
            base.source == "ipify" -> extra
            else -> merge(base, extra)
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
                BoundedHttpResponse.readText(connection.inputStream, MAX_RESPONSE_BYTES)
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
        private const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024
        const val IPWHOIS_URL = "https://ipwho.is/"
        const val IPAPICO_URL = "https://ipapi.co/json/"
        const val IPIFY_URL = "https://api64.ipify.org?format=text"
        const val PROXYCHECK_URL = "https://proxycheck.io/v2/"

        fun parseIpLocation(body: String, source: String): PublicIpLocation? {
            val details = when (source) {
                "ipwho.is" -> parseIpWhoIs(body)
                "ipapi.co" -> parseIpApiCo(body)
                else -> null
            } ?: return null
            val json = JSONObject(body)
            // optDouble alone accepts numeric strings; reject absent/null/boolean values.
            fun coordinate(key: String): Double? = when (val value = json.opt(key)) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
            val lat = coordinate("latitude") ?: return null
            val lon = coordinate("longitude") ?: return null
            if (!lat.isFinite() || lat !in -90.0..90.0 || !lon.isFinite() || lon !in -180.0..180.0) return null
            return PublicIpLocation(lat, lon, details.formatLocation(), source)
        }

        fun parseIpWhoIs(jsonString: String): PublicIpDetails? {
            val json = JSONObject(jsonString)
            if (json.has("success") && !json.optBoolean("success", true)) return null
            val ip = json.optString("ip").takeIf(::isIpAddress) ?: return null
            val connection = json.optJSONObject("connection")
            val timezoneJson = json.optJSONObject("timezone")
            val timezoneId = timezoneJson?.text("id")
            val utcOffset = timezoneJson?.text("utc")
            val isp = connection?.text("isp") ?: connection?.text("org") ?: json.text("isp")
            return PublicIpDetails(
                ip = ip,
                country = json.text("country"),
                countryCode = json.text("country_code"),
                region = json.text("region"),
                city = json.text("city"),
                postal = json.text("postal"),
                continent = json.text("continent"),
                timezone = formatTimezone(timezoneId, utcOffset),
                ipType = json.text("type") ?: inferIpType(ip),
                isp = isp,
                organization = connection?.text("org"),
                asn = normalizeAsn(connection?.text("asn")),
                domain = connection?.text("domain"),
                source = "ipwho.is",
            )
        }

        fun parseIpApiCo(jsonString: String): PublicIpDetails? {
            val json = JSONObject(jsonString)
            if (json.optBoolean("error", false)) return null
            val ip = json.optString("ip").takeIf(::isIpAddress) ?: return null
            val organization = json.text("org")
            return PublicIpDetails(
                ip = ip,
                country = json.text("country_name"),
                countryCode = json.text("country_code"),
                region = json.text("region"),
                city = json.text("city"),
                postal = json.text("postal"),
                timezone = formatTimezone(json.text("timezone"), json.text("utc_offset")),
                ipType = json.text("version") ?: inferIpType(ip),
                isp = organization,
                organization = organization,
                asn = normalizeAsn(json.text("asn")),
                ipRange = json.text("network"),
                source = "ipapi.co",
            )
        }

        fun parseProxyCheck(jsonString: String): PublicIpDetails? {
            val json = JSONObject(jsonString)
            val ip = json.keys().asSequence().firstOrNull(::isIpAddress) ?: return null
            val data = json.optJSONObject(ip) ?: return null
            val proxy = when (val value = data.opt("proxy")) {
                is Boolean -> value
                is String -> when (value.lowercase()) {
                    "yes", "true" -> true
                    "no", "false" -> false
                    else -> null
                }
                else -> null
            }
            return PublicIpDetails(
                ip = ip,
                country = data.text("country"),
                countryCode = data.text("isocode"),
                region = data.text("region"),
                city = data.text("city"),
                postal = data.text("postcode"),
                continent = data.text("continent"),
                timezone = data.text("timezone"),
                ipType = inferIpType(ip),
                isp = data.text("provider"),
                organization = data.text("organisation"),
                asn = normalizeAsn(data.text("asn")),
                hostname = data.text("hostname"),
                ipRange = data.text("range"),
                networkType = data.text("type"),
                proxy = proxy,
                risk = data.optInt("risk", -1).takeIf { it in 0..100 },
                source = "proxycheck.io",
            )
        }

        fun parsePlainIp(text: String): PublicIpDetails? {
            val ip = text.trim().takeIf(::isIpAddress) ?: return null
            return PublicIpDetails(ip = ip, ipType = inferIpType(ip), source = "ipify")
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

private fun JSONObject.text(key: String): String? =
    optString(key).trim().takeUnless { it.isEmpty() || it == "null" }

private fun normalizeAsn(value: String?): String? = value
    ?.trim()
    ?.takeUnless { it.isEmpty() || it == "null" }
    ?.let { if (it.startsWith("AS", ignoreCase = true)) "AS" + it.substring(2) else "AS$it" }

private fun inferIpType(ip: String): String = if (ip.contains(':')) "IPv6" else "IPv4"

private fun formatTimezone(id: String?, utcOffset: String?): String? = when {
    id != null && utcOffset != null -> "$id (UTC$utcOffset)"
    else -> id
}

private fun merge(base: PublicIpDetails, extra: PublicIpDetails): PublicIpDetails = base.copy(
    country = base.country ?: extra.country,
    countryCode = base.countryCode ?: extra.countryCode,
    region = base.region ?: extra.region,
    city = base.city ?: extra.city,
    postal = base.postal ?: extra.postal,
    continent = base.continent ?: extra.continent,
    timezone = base.timezone ?: extra.timezone,
    ipType = base.ipType ?: extra.ipType,
    isp = base.isp ?: extra.isp,
    organization = base.organization ?: extra.organization,
    asn = base.asn ?: extra.asn,
    domain = base.domain ?: extra.domain,
    hostname = base.hostname ?: extra.hostname,
    ipRange = base.ipRange ?: extra.ipRange,
    networkType = base.networkType ?: extra.networkType,
    proxy = base.proxy ?: extra.proxy,
    risk = base.risk ?: extra.risk,
    source = listOfNotNull(base.source, extra.source).distinct().joinToString(" + "),
)
