package com.example.toolbox.fx

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

/**
 * SharedPreferences cache for fetched rates: the most recent `latest` plus a
 * per-requested-date historical cache capped at [MAX_HISTORICAL] dates
 * (oldest evicted). Corrupt entries read as absent.
 */
class FxRateStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun latest(): FxRates? = read(KEY_LATEST)

    fun saveLatest(rates: FxRates) {
        prefs.edit { putString(KEY_LATEST, FxRatesJson.toJson(rates)) }
    }

    fun forDate(date: LocalDate): FxRates? = read(dateKey(date))

    fun saveForDate(requested: LocalDate, rates: FxRates) {
        val keys = prefs.getString(KEY_DATES, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.toMutableList()
            ?: mutableListOf()
        keys.remove(requested.toString())
        keys.add(requested.toString())
        val evicted = keys.dropLast(MAX_HISTORICAL).also {
            keys.removeAll(it.toSet())
        }
        prefs.edit {
            putString(dateKey(requested), FxRatesJson.toJson(rates))
            putString(KEY_DATES, keys.joinToString(","))
            for (stale in evicted) {
                remove("date_$stale")
            }
        }
    }

    private fun read(key: String): FxRates? =
        prefs.getString(key, null)?.let { json ->
            runCatching { FxRatesJson.fromJson(json) }.getOrNull()
        }

    private fun dateKey(date: LocalDate): String = "date_$date"

    /** All cached historical entries (invalid dates are skipped). */
    private fun allCached(): List<FxRates> {
        val keys = prefs.getString(KEY_DATES, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: return emptyList()
        return keys.mapNotNull { read("date_$it") }
    }

    /**
     * The closest cached rate set for [date] — latest business day <= D,
     * else the closest after D. Candidates are the historical cache plus
     * `latest`.
     */
    fun nearestCached(date: LocalDate): FxRates? =
        FxRatesMath.nearest(allCached() + listOfNotNull(latest()), date)

    /**
     * Cached rates for [dates] first; the missing ones are fetched in ONE
     * [FxRateReader.fetchForDates] call (only when [fetchIfMissing]) and cached
     * by their requested dates. `latest` is refreshed when today was fetched.
     * Throws [FxException] only when fetching was needed and nothing resolved.
     */
    fun ratesForDates(
        dates: Collection<LocalDate>,
        fetchIfMissing: Boolean,
        reader: FxRateReader = FxRateReader(),
    ): Map<LocalDate, FxRates> {
        val out = mutableMapOf<LocalDate, FxRates>()
        val missing = mutableListOf<LocalDate>()
        for (d in dates) {
            val cached = forDate(d)
            if (cached != null) {
                out[d] = cached
            } else {
                missing += d
            }
        }
        if (missing.isEmpty() || !fetchIfMissing) {
            return out
        }
        val fetched = reader.fetchForDates(
            missing,
            required = LEDGER_CURRENCIES.toSet(),
        )
        val todayDate = LocalDate.now()
        for ((requested, rates) in fetched) {
            saveForDate(requested, rates)
            out[requested] = rates
            if (requested == todayDate) {
                saveLatest(rates)
            }
        }
        return out
    }

    /**
     * Rate usable for a ledger entry dated [date]: cached history first, then
     * an optional live fetch. A fetch for today also refreshes `latest`.
     */
    fun rateForDate(
        date: LocalDate,
        fetchIfMissing: Boolean,
        reader: FxRateReader = FxRateReader(),
    ): FxRates? = ratesForDates(
        setOf(date),
        fetchIfMissing = fetchIfMissing,
        reader = reader,
    )[date]

    companion object {
        private const val PREFS_NAME = "fx_rates"
        private const val KEY_LATEST = "latest"
        private const val KEY_DATES = "dates"
        private const val MAX_HISTORICAL = 120
    }
}
