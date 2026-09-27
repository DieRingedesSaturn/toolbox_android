package com.example.toolbox.fx

import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * Blocking exchange-rate reader with a fallback chain:
 * Frankfurter -> ECB eurofxref XML -> the community currency-api
 * (jsDelivr, then the pages.dev mirror). Network happens only on explicit
 * user actions; call from Dispatchers.IO.
 */
class FxRateReader(
    private val frankfurterBase: String = "https://api.frankfurter.dev/v1/",
    private val ecbDailyUrl: String =
        "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml",
    private val ecbHistUrl: String =
        "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist-90d.xml",
    private val currencyApiBases: List<String> = listOf(
        "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@{date}" +
            "/v1/currencies/eur.min.json",
        "https://{date}.currency-api.pages.dev/v1/currencies/eur.min.json",
    ),
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private class Deadline(private val endMillis: Long) {
        val exceeded: Boolean get() = System.currentTimeMillis() > endMillis
    }

    private fun deadline() = Deadline(System.currentTimeMillis() + OVERALL_DEADLINE_MS)

    fun fetchLatest(required: Set<String> = setOf("CNY", "JPY", "USD")): FxRates {
        val deadline = deadline()
        val attempts = mutableListOf<Pair<FxSource, FxFailure>>()

        fun consider(source: FxSource, fetch: () -> FxRates): FxRates? {
            if (deadline.exceeded) {
                attempts += source to FxFailure.NETWORK
                return null
            }
            return runCatching(fetch)
                .onFailure { attempts += source to failureOf(it) }
                .getOrNull()
                ?.takeIf { rates ->
                    required.all { (rates.eurRates[it] ?: 0.0) > 0.0 }
                }
                ?: run {
                    if (attempts.lastOrNull()?.first != source) {
                        attempts += source to FxFailure.INVALID_RESPONSE
                    }
                    null
                }
        }

        consider(FxSource.FRANKFURTER) {
            FxRatesJson.parseFrankfurter(
                get("${frankfurterBase}latest"),
                System.currentTimeMillis(),
            )
        }?.let { return it }

        consider(FxSource.ECB) {
            val byDate = parseEcbXml(get(ecbDailyUrl), System.currentTimeMillis())
            byDate.values.maxByOrNull { it.date }
                ?: throw FxException(FxFailure.INVALID_RESPONSE)
        }?.let { return it }

        for (base in currencyApiBases) {
            consider(FxSource.CURRENCY_API) {
                FxRatesJson.parseCurrencyApi(
                    get(base.replace("{date}", "latest")),
                    System.currentTimeMillis(),
                )
            }?.let { return it }
        }

        throw FxException(FxFailure.ALL_SOURCES_FAILED, attempts = attempts)
    }

    /**
     * Resolves each requested date to the rates of the latest available
     * business day <= that date. Sources are tried in order and only dates
     * still unresolved advance to the next step. May return a partial map;
     * throws [FxFailure.ALL_SOURCES_FAILED] only when nothing resolved.
     */
    fun fetchForDates(
        dates: Collection<LocalDate>,
        required: Set<String>,
    ): Map<LocalDate, FxRates> {
        if (dates.isEmpty()) return emptyMap()
        val deadline = deadline()
        val attempts = mutableListOf<Pair<FxSource, FxFailure>>()
        val resolved = mutableMapOf<LocalDate, FxRates>()
        val unresolved = dates.toSortedSet()

        fun resolveFrom(byDate: Map<LocalDate, FxRates>) {
            for (d in unresolved.toList()) {
                val hit = byDate.keys
                    .filter { it <= d }
                    .maxOrNull()
                    ?.let { byDate[it] }
                if (hit != null &&
                    required.all { (hit.eurRates[it] ?: 0.0) > 0.0 }
                ) {
                    resolved[d] = hit
                    unresolved.remove(d)
                }
            }
        }

        fun record(source: FxSource, e: Throwable) {
            attempts += source to failureOf(e)
        }

        // 1. Frankfurter range (min-7)..max — one request.
        if (unresolved.isNotEmpty() && !deadline.exceeded) {
            try {
                val range = "${unresolved.first().minusDays(7)}..${unresolved.last()}"
                val byDate = FxRatesJson.parseFrankfurterRange(
                    get("$frankfurterBase$range?symbols=${required.joinToString(",")}"),
                    System.currentTimeMillis(),
                )
                resolveFrom(byDate)
            } catch (e: Exception) {
                record(FxSource.FRANKFURTER, e)
            }
        }

        // 2. ECB 90-day history, only when all remaining dates are inside it.
        if (unresolved.isNotEmpty() && !deadline.exceeded &&
            unresolved.first() >= today().minusDays(85)
        ) {
            try {
                resolveFrom(
                    parseEcbXml(get(ecbHistUrl), System.currentTimeMillis()),
                )
            } catch (e: Exception) {
                record(FxSource.ECB, e)
            }
        }

        // 3. currency-api per date, at most MAX_CURRENCY_API_DATES dates.
        for (d in unresolved.sorted().take(MAX_CURRENCY_API_DATES).toList()) {
            for (base in currencyApiBases) {
                if (deadline.exceeded) {
                    attempts += FxSource.CURRENCY_API to FxFailure.NETWORK
                    break
                }
                try {
                    val rates = FxRatesJson.parseCurrencyApi(
                        get(base.replace("{date}", d.toString())),
                        System.currentTimeMillis(),
                    )
                    if (required.all { (rates.eurRates[it] ?: 0.0) > 0.0 }) {
                        resolved[d] = rates
                        unresolved.remove(d)
                        break
                    }
                    record(FxSource.CURRENCY_API, FxException(FxFailure.INVALID_RESPONSE))
                } catch (e: Exception) {
                    record(FxSource.CURRENCY_API, e)
                }
            }
        }

        if (resolved.isEmpty()) {
            throw FxException(FxFailure.ALL_SOURCES_FAILED, attempts = attempts)
        }
        return resolved
    }

    private fun failureOf(e: Throwable): FxFailure =
        (e as? FxException)?.failure ?: FxFailure.NETWORK

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw FxException(FxFailure.HTTP_ERROR, httpCode = code)
            }
            return conn.inputStream.readBytes().toString(Charsets.UTF_8)
        } catch (e: FxException) {
            throw e
        } catch (e: IOException) {
            throw FxException(FxFailure.NETWORK, cause = e)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * ECB gesmes XML: `<Cube><Cube time="yyyy-MM-dd"><Cube currency="USD"
     * rate="1.14"/>...</Cube></Cube>` — one map entry per `time` cube.
     */
    private fun parseEcbXml(
        xml: String,
        fetchedAtMillis: Long,
    ): Map<LocalDate, FxRates> {
        val doc = try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true,
            )
            factory.isExpandEntityReferences = false
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        } catch (e: Exception) {
            throw FxException(FxFailure.INVALID_RESPONSE, cause = e)
        }
        val cubes = doc.getElementsByTagName("Cube")
        val byDate = linkedMapOf<LocalDate, MutableMap<String, Double>>()
        var currentDay: LocalDate? = null
        for (i in 0 until cubes.length) {
            val el = cubes.item(i) as? Element ?: continue
            val time = el.getAttribute("time")
            if (time.isNotBlank()) {
                currentDay = try {
                    LocalDate.parse(time)
                } catch (e: Exception) {
                    null
                }
                currentDay?.let { day -> byDate.getOrPut(day) { mutableMapOf() } }
                continue
            }
            val code = el.getAttribute("currency")
            val rateText = el.getAttribute("rate")
            val day = currentDay ?: continue
            val rate = rateText.toDoubleOrNull()
            if (code.isBlank() || rate == null || rate <= 0.0) {
                throw FxException(FxFailure.INVALID_RESPONSE)
            }
            byDate.getValue(day)[code] = rate
        }
        if (byDate.isEmpty() || byDate.values.any { it.isEmpty() }) {
            throw FxException(FxFailure.INVALID_RESPONSE)
        }
        return byDate.mapValues { (day, rates) ->
            rates["EUR"] = 1.0
            FxRates(
                date = day.toString(),
                eurRates = rates,
                fetchedAtMillis = fetchedAtMillis,
                source = FxSource.ECB,
            )
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val OVERALL_DEADLINE_MS = 25_000L
        private const val MAX_CURRENCY_API_DATES = 12
    }
}
