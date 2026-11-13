package com.example.toolbox.device

import android.content.Context
import com.example.toolbox.fx.FxRateStore
import com.example.toolbox.ledger.LedgerAccounts
import com.example.toolbox.ledger.LedgerCalculator
import com.example.toolbox.ledger.LedgerStore
import com.example.toolbox.network.NetworkInfoReader
import com.example.toolbox.network.NetworkTransport
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal data class HomeStatus(
    val batteryPercent: Int?,
    val isCharging: Boolean?,
    val storageAvailable: Long,
    val storageTotal: Long,
    val monthExpenseCents: Long,
    val monthIncomeCents: Long,
    val totalAssetsCnyCents: Long?,
    val assetsPartial: Boolean,
    val memoryAvailable: Long = 0L,
    val memoryTotal: Long = 0L,
    val networkConnected: Boolean = false,
    val networkTransports: List<NetworkTransport> = emptyList(),
    val networkAddress: String? = null,
    val todayExpenseCents: Long = 0L,
    val todayIncomeCents: Long = 0L,
)

private data class LedgerHomeSnapshot(
    val monthExpenseCents: Long,
    val monthIncomeCents: Long,
    val totalAssetsCnyCents: Long?,
    val assetsPartial: Boolean,
    val todayExpenseCents: Long,
    val todayIncomeCents: Long,
)

/** On-demand home snapshot. Call from Dispatchers.IO while Home is visible. */
internal object HomeStatusReader {
    fun read(context: Context, readQuick: Boolean, readNetwork: Boolean, readLedger: Boolean): HomeStatus? =
        runCatching {
            val quick = if (readQuick) {
                DeviceInfoReader.readQuickStatus(context)
            } else {
                null
            }
            val network = if (readNetwork) {
                NetworkInfoReader(context).readLocal()
            } else {
                null
            }
            val ledger = if (readLedger) {
                val (allEntries, accounts) = LedgerStore(context).use { store ->
                    store.queryAllEntriesForSync() to store.queryAccounts(includeDeleted = false)
                }
                val entries = allEntries.filter { it.deletedAtMillis == null }
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val month = YearMonth.from(today)
                val summary = LedgerCalculator.monthSummary(allEntries, month, zone)
                val (totalCny, allConverted) = LedgerAccounts.totalAssetsCnyCents(
                    accounts,
                    entries,
                    FxRateStore(context.applicationContext).latest(),
                )
                val (todayExpense, todayIncome) = LedgerCalculator.dayTotals(
                    allEntries,
                    today,
                    zone,
                )
                LedgerHomeSnapshot(
                    monthExpenseCents = summary.regularExpenseCents,
                    monthIncomeCents = summary.incomeCents,
                    totalAssetsCnyCents = totalCny,
                    assetsPartial = !allConverted,
                    todayExpenseCents = todayExpense,
                    todayIncomeCents = todayIncome,
                )
            } else {
                null
            }
            val ipv4 = network?.localAddresses?.firstOrNull { address ->
                address.contains('.') && !address.contains(':')
            }
            HomeStatus(
                batteryPercent = quick?.batteryPercent,
                isCharging = quick?.isCharging,
                storageAvailable = quick?.storageAvailable ?: 0L,
                storageTotal = quick?.storageTotal ?: 0L,
                monthExpenseCents = ledger?.monthExpenseCents ?: 0L,
                monthIncomeCents = ledger?.monthIncomeCents ?: 0L,
                totalAssetsCnyCents = ledger?.totalAssetsCnyCents,
                assetsPartial = ledger?.assetsPartial == true,
                memoryAvailable = quick?.memoryAvailable ?: 0L,
                memoryTotal = quick?.memoryTotal ?: 0L,
                networkConnected = network?.isConnected == true,
                networkTransports = network?.transports.orEmpty(),
                networkAddress = ipv4 ?: network?.localAddresses?.firstOrNull(),
                todayExpenseCents = ledger?.todayExpenseCents ?: 0L,
                todayIncomeCents = ledger?.todayIncomeCents ?: 0L,
            )
        }.getOrNull()
}
