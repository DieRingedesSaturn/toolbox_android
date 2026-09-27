package com.example.toolbox.fx

import java.time.LocalDate
import java.util.Currency
import org.json.JSONObject

enum class FxSource(val id: String) {
    FRANKFURTER("frankfurter"),
    ECB("ecb"),
    CURRENCY_API("currency-api"),
    ;

    companion object {
        fun fromId(id: String?): FxSource =
            entries.firstOrNull { it.id == id } ?: FRANKFURTER
    }
}

enum class FxFailure {
    NETWORK,
    HTTP_ERROR,
    INVALID_RESPONSE,
    ALL_SOURCES_FAILED,
}

class FxException(
    val failure: FxFailure,
    val httpCode: Int? = null,
    cause: Throwable? = null,
    val attempts: List<Pair<FxSource, FxFailure>> = emptyList(),
) : Exception(failure.name, cause)

val LEDGER_CURRENCIES = listOf("CNY", "JPY", "USD", "EUR")

/**
 * ECB reference rates: every rate is the amount of that currency per 1 EUR, so
 * `eurRates["CNY"]` is the CNY-per-EUR rate. The base currency is stored as 1.0.
 */
data class FxRates(
    val date: String,
    val eurRates: Map<String, Double>,
    val fetchedAtMillis: Long,
    val source: FxSource = FxSource.FRANKFURTER,
) {
    fun cnyPerUnit(code: String): Double? {
        if (code == "CNY") return 1.0
        val cny = eurRates["CNY"] ?: return null
        val rate = eurRates[code] ?: return null
        return cny / rate
    }

    fun convert(amount: Double, from: String, to: String): Double? {
        val fromRate = eurRates[from] ?: return null
        val toRate = eurRates[to] ?: return null
        return amount / fromRate * toRate
    }

    val currencies: List<String>
        get() = eurRates.keys.sorted()
}

object FxRatesMath {

    /**
     * The closest cached rate set for [date]: the latest `rates.date` <= D
     * wins, otherwise the closest date after D.
     */
    fun nearest(candidates: List<FxRates>, date: LocalDate): FxRates? {
        if (candidates.isEmpty()) return null
        val parsed = candidates.mapNotNull { rates ->
            runCatching { LocalDate.parse(rates.date) }.getOrNull()?.let { it to rates }
        }
        if (parsed.isEmpty()) return null
        val atOrBefore = parsed
            .filter { it.first <= date }
            .maxByOrNull { it.first }
        if (atOrBefore != null) return atOrBefore.second
        return parsed.minByOrNull { it.first }?.second
    }
}

object FxRatesJson {

    fun parseFrankfurter(json: String, fetchedAtMillis: Long): FxRates {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw FxException(FxFailure.INVALID_RESPONSE, cause = e)
        }
        if (root.optString("base") != "EUR") {
            throw FxException(FxFailure.INVALID_RESPONSE)
        }
        val date = root.optString("date").takeIf { it.isNotBlank() }
            ?: throw FxException(FxFailure.INVALID_RESPONSE)
        return FxRates(
            date = date,
            eurRates = parseRatesObject(
                root.optJSONObject("rates")
                    ?: throw FxException(FxFailure.INVALID_RESPONSE),
            ),
            fetchedAtMillis = fetchedAtMillis,
            source = FxSource.FRANKFURTER,
        )
    }

    /** Frankfurter range response: `rates` is a map of yyyy-MM-dd -> rates. */
    fun parseFrankfurterRange(
        json: String,
        fetchedAtMillis: Long,
    ): Map<LocalDate, FxRates> {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw FxException(FxFailure.INVALID_RESPONSE, cause = e)
        }
        if (root.optString("base") != "EUR") {
            throw FxException(FxFailure.INVALID_RESPONSE)
        }
        val byDate = root.optJSONObject("rates")
            ?: throw FxException(FxFailure.INVALID_RESPONSE)
        val out = mutableMapOf<LocalDate, FxRates>()
        for (key in byDate.keys()) {
            val day = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
            val dayRates = byDate.optJSONObject(key) ?: continue
            out[day] = FxRates(
                date = key,
                eurRates = parseRatesObject(dayRates),
                fetchedAtMillis = fetchedAtMillis,
                source = FxSource.FRANKFURTER,
            )
        }
        if (out.isEmpty()) throw FxException(FxFailure.INVALID_RESPONSE)
        return out
    }

    /**
     * currency-api community response: `{"date":"yyyy-MM-dd","eur":{...}}` with
     * lowercase codes that include crypto — keep only real ISO currencies.
     */
    fun parseCurrencyApi(json: String, fetchedAtMillis: Long): FxRates {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw FxException(FxFailure.INVALID_RESPONSE, cause = e)
        }
        val date = root.optString("date").takeIf { it.isNotBlank() }
            ?: throw FxException(FxFailure.INVALID_RESPONSE)
        val table = root.optJSONObject("eur")
            ?: throw FxException(FxFailure.INVALID_RESPONSE)
        val isoCodes = Currency.getAvailableCurrencies().map { it.currencyCode }.toSet()
        val rates = mutableMapOf<String, Double>()
        for (key in table.keys()) {
            if (!key.matches(Regex("^[a-z]{3}$"))) continue
            val code = key.uppercase()
            if (code !in isoCodes) continue
            val rate = table.optDouble(key, Double.NaN)
            if (rate.isNaN() || rate <= 0.0) {
                throw FxException(FxFailure.INVALID_RESPONSE)
            }
            rates[code] = rate
        }
        if (rates.isEmpty()) throw FxException(FxFailure.INVALID_RESPONSE)
        rates["EUR"] = 1.0
        return FxRates(
            date = date,
            eurRates = rates,
            fetchedAtMillis = fetchedAtMillis,
            source = FxSource.CURRENCY_API,
        )
    }

    private fun parseRatesObject(ratesObject: JSONObject): MutableMap<String, Double> {
        val rates = mutableMapOf<String, Double>()
        for (code in ratesObject.keys()) {
            val rate = ratesObject.optDouble(code, Double.NaN)
            if (rate.isNaN() || rate <= 0.0) {
                throw FxException(FxFailure.INVALID_RESPONSE)
            }
            rates[code] = rate
        }
        if (rates.isEmpty()) throw FxException(FxFailure.INVALID_RESPONSE)
        rates["EUR"] = 1.0
        return rates
    }

    fun toJson(rates: FxRates): String {
        val root = JSONObject()
        root.put("date", rates.date)
        root.put("fetchedAtMillis", rates.fetchedAtMillis)
        root.put("source", rates.source.id)
        val ratesObject = JSONObject()
        for ((code, rate) in rates.eurRates.toSortedMap()) {
            ratesObject.put(code, rate)
        }
        root.put("rates", ratesObject)
        return root.toString()
    }

    fun fromJson(json: String): FxRates {
        val root = JSONObject(json)
        val date = root.getString("date")
        val fetchedAtMillis = root.optLong("fetchedAtMillis", 0L)
        val source = FxSource.fromId(root.optString("source"))
        val ratesObject = root.getJSONObject("rates")
        val rates = mutableMapOf<String, Double>()
        for (code in ratesObject.keys()) {
            rates[code] = ratesObject.getDouble(code)
        }
        return FxRates(
            date = date,
            eurRates = rates,
            fetchedAtMillis = fetchedAtMillis,
            source = source,
        )
    }
}
