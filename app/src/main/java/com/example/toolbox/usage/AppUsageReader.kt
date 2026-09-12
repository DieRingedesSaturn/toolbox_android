package com.example.toolbox.usage

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

object AppUsageReader {

    fun hasUsagePermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getUsageSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun queryUsageReport(
        context: Context,
        timeRange: UsageTimeRange,
    ): UsageReport = withContext(Dispatchers.IO) {
        val (startTime, endTime) = calculateTimeWindow(timeRange)

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
        val packageManager = context.packageManager

        // 1. Query Screen Usage Time by Package & Real Screen-On Time
        val usageMap = mutableMapOf<String, Pair<Long, Long>>() // pkg -> (duration, lastUsed)
        var measuredScreenDurationMillis = 0L

        if (usageStatsManager != null && hasUsagePermission(context)) {
            var eventsProcessed = false
            if (timeRange == UsageTimeRange.TODAY || timeRange == UsageTimeRange.YESTERDAY) {
                val processed = runCatching {
                    val queryStart = maxOf(0L, startTime - 4 * 3600_000L)
                    val events = usageStatsManager.queryEvents(queryStart, endTime)
                    processUsageEvents(events.asSequence(), startTime, endTime)
                }.getOrNull()

                if (processed != null && (processed.appDurations.isNotEmpty() || processed.measuredScreenDurationMillis > 0L)) {
                    eventsProcessed = true
                    measuredScreenDurationMillis = processed.measuredScreenDurationMillis
                    for ((pkg, dur) in processed.appDurations) {
                        val lastUsed = processed.appLastUsed[pkg] ?: startTime
                        usageMap[pkg] = Pair(dur, lastUsed)
                    }
                }
            }

            if (!eventsProcessed || usageMap.isEmpty()) {
                readUsageFromStats(
                    usageStatsManager = usageStatsManager,
                    startTime = startTime,
                    endTime = endTime,
                    timeRange = timeRange,
                    outUsageMap = usageMap,
                )
            }
        }

        // 2. Query Network Traffic (Wi-Fi & Mobile Cellular) by UID
        val wifiTraffic = mutableMapOf<Int, Pair<Long, Long>>() // uid -> (rxBytes, txBytes)
        val cellularTraffic = mutableMapOf<Int, Pair<Long, Long>>() // uid -> (rxBytes, txBytes)

        if (networkStatsManager != null && hasUsagePermission(context)) {
            readNetworkBuckets(networkStatsManager, NetworkCapabilities.TRANSPORT_WIFI, startTime, endTime, wifiTraffic)
            readNetworkBuckets(networkStatsManager, NetworkCapabilities.TRANSPORT_CELLULAR, startTime, endTime, cellularTraffic)
        }

        // 3. Resolve Installed Applications metadata
        val installedApps = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                packageManager.getInstalledApplications(0)
            }
        }.getOrDefault(emptyList())

        val appInfoByPkg = installedApps.associateBy { it.packageName }
        val allPackages = (usageMap.keys + installedApps.map { it.packageName }).toSet()

        val items = mutableListOf<AppUsageItem>()

        for (pkg in allPackages) {
            val appInfo: ApplicationInfo? = appInfoByPkg[pkg] ?: runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    packageManager.getApplicationInfo(pkg, 0)
                }
            }.getOrNull()

            val label = appInfo?.let { runCatching { it.loadLabel(packageManager).toString() }.getOrNull() } ?: pkg
            val category = if (appInfo != null) AppCategoryResolver.resolve(appInfo) else AppCategory.OTHER

            val (duration, lastUsed) = usageMap[pkg] ?: Pair(0L, 0L)
            val uid = appInfo?.uid ?: -1

            val (wifiRx, wifiTx) = if (uid > 0) wifiTraffic[uid] ?: Pair(0L, 0L) else Pair(0L, 0L)
            val (cellRx, cellTx) = if (uid > 0) cellularTraffic[uid] ?: Pair(0L, 0L) else Pair(0L, 0L)

            val item = AppUsageItem(
                packageName = pkg,
                label = label,
                category = category,
                foregroundDurationMillis = duration,
                lastTimeUsedMillis = lastUsed,
                wifiRxBytes = wifiRx,
                wifiTxBytes = wifiTx,
                cellularRxBytes = cellRx,
                cellularTxBytes = cellTx,
            )

            if (item.hasUsage) {
                items.add(item)
            }
        }

        // 4. Summaries and Aggregation
        val totalScreenDuration = if (measuredScreenDurationMillis > 0L) {
            measuredScreenDurationMillis
        } else {
            items.sumOf { it.foregroundDurationMillis }
        }
        val totalWifi = items.sumOf { it.totalWifiBytes }
        val totalCellular = items.sumOf { it.totalCellularBytes }

        val categorySummaries = items.groupBy { it.category }.map { (category, list) ->
            CategoryUsageSummary(
                category = category,
                totalDurationMillis = list.sumOf { it.foregroundDurationMillis },
                totalWifiBytes = list.sumOf { it.totalWifiBytes },
                totalCellularBytes = list.sumOf { it.totalCellularBytes },
                appCount = list.size,
            )
        }.sortedByDescending { it.totalDurationMillis }

        UsageReport(
            timeRange = timeRange,
            startTimeMillis = startTime,
            endTimeMillis = endTime,
            totalScreenDurationMillis = totalScreenDuration,
            totalWifiBytes = totalWifi,
            totalCellularBytes = totalCellular,
            items = items,
            categorySummaries = categorySummaries,
        )
    }

    private fun readNetworkBuckets(
        manager: NetworkStatsManager,
        transport: Int,
        startTime: Long,
        endTime: Long,
        outMap: MutableMap<Int, Pair<Long, Long>>,
    ) {
        runCatching {
            val stats = manager.querySummary(transport, null, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                if (uid > 0) {
                    val prev = outMap[uid] ?: Pair(0L, 0L)
                    outMap[uid] = Pair(
                        prev.first + bucket.rxBytes,
                        prev.second + bucket.txBytes,
                    )
                }
            }
            stats.close()
        }
    }

    private fun calculateTimeWindow(timeRange: UsageTimeRange): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis

        return when (timeRange) {
            UsageTimeRange.TODAY -> Pair(startOfToday, now)
            UsageTimeRange.YESTERDAY -> {
                val startOfYesterday = startOfToday - 86400000L
                val endOfYesterday = startOfToday - 1L
                Pair(startOfYesterday, endOfYesterday)
            }
            UsageTimeRange.PAST_7_DAYS -> {
                val start7DaysAgo = startOfToday - (6 * 86400000L)
                Pair(start7DaysAgo, now)
            }
            UsageTimeRange.PAST_30_DAYS -> {
                val start30DaysAgo = startOfToday - (29 * 86400000L)
                Pair(start30DaysAgo, now)
            }
        }
    }

    fun processUsageEvents(
        events: Sequence<UsageEventRecord>,
        startTime: Long,
        endTime: Long,
    ): ProcessedUsageEvents {
        var hasAnyEvents = false
        var screenInteractive = false
        var screenInteractiveStart: Long? = null
        var totalScreenInteractiveTime = 0L

        val activeApps = mutableMapOf<String, Long>()
        val durations = mutableMapOf<String, Long>()
        val lastUsed = mutableMapOf<String, Long>()

        for (event in events) {
            val ts = event.timestamp
            if (ts > endTime) continue

            hasAnyEvents = true
            val pkg = event.packageName

            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    if (!screenInteractive) {
                        screenInteractive = true
                        screenInteractiveStart = ts
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    val currentStart = screenInteractiveStart
                    if (screenInteractive && currentStart != null) {
                        val sStart = maxOf(currentStart, startTime)
                        val sEnd = minOf(ts, endTime)
                        if (sEnd > sStart) {
                            totalScreenInteractiveTime += (sEnd - sStart)
                        }
                    }
                    screenInteractive = false
                    screenInteractiveStart = null

                    // Screen turned off: close any active app foreground sessions
                    for ((p, aStart) in activeApps) {
                        val sStart = maxOf(aStart, startTime)
                        val sEnd = minOf(ts, endTime)
                        if (sEnd > sStart) {
                            durations[p] = (durations[p] ?: 0L) + (sEnd - sStart)
                        }
                    }
                    activeApps.clear()
                }
                1, // UsageEvents.Event.ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
                -> {
                    if (!pkg.isNullOrEmpty()) {
                        if (ts >= startTime) {
                            lastUsed[pkg] = maxOf(lastUsed[pkg] ?: 0L, ts)
                        }
                        val prevStart = activeApps[pkg]
                        if (prevStart != null) {
                            val sStart = maxOf(prevStart, startTime)
                            val sEnd = minOf(ts, endTime)
                            if (sEnd > sStart) {
                                durations[pkg] = (durations[pkg] ?: 0L) + (sEnd - sStart)
                            }
                        }
                        activeApps[pkg] = ts
                    }
                }
                2, // UsageEvents.Event.ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
                23, // UsageEvents.Event.ACTIVITY_STOPPED
                -> {
                    if (!pkg.isNullOrEmpty()) {
                        if (ts >= startTime) {
                            lastUsed[pkg] = maxOf(lastUsed[pkg] ?: 0L, ts)
                        }
                        val aStart = activeApps.remove(pkg) ?: continue
                        val sStart = maxOf(aStart, startTime)
                        val sEnd = minOf(ts, endTime)
                        if (sEnd > sStart) {
                            durations[pkg] = (durations[pkg] ?: 0L) + (sEnd - sStart)
                        }
                    }
                }
            }
        }

        if (!hasAnyEvents) {
            return ProcessedUsageEvents(
                measuredScreenDurationMillis = 0L,
                appDurations = emptyMap(),
                appLastUsed = emptyMap(),
            )
        }

        val openScreenStart = screenInteractiveStart
        if (screenInteractive && openScreenStart != null) {
            val sStart = maxOf(openScreenStart, startTime)
            val sEnd = endTime
            if (sEnd > sStart) {
                totalScreenInteractiveTime += (sEnd - sStart)
            }
        }

        for ((p, aStart) in activeApps) {
            val sStart = maxOf(aStart, startTime)
            val sEnd = endTime
            if (sEnd > sStart) {
                durations[p] = (durations[p] ?: 0L) + (sEnd - sStart)
            }
        }

        val finalScreenDuration = if (totalScreenInteractiveTime > 0L) {
            totalScreenInteractiveTime
        } else {
            durations.values.sum()
        }

        return ProcessedUsageEvents(
            measuredScreenDurationMillis = finalScreenDuration,
            appDurations = durations.filterValues { it > 0L },
            appLastUsed = lastUsed,
        )
    }

    private fun readUsageFromStats(
        usageStatsManager: UsageStatsManager,
        startTime: Long,
        endTime: Long,
        timeRange: UsageTimeRange,
        outUsageMap: MutableMap<String, Pair<Long, Long>>,
    ) {
        val interval = if (timeRange == UsageTimeRange.PAST_30_DAYS) {
            UsageStatsManager.INTERVAL_WEEKLY
        } else {
            UsageStatsManager.INTERVAL_DAILY
        }
        val statsList = runCatching {
            usageStatsManager.queryUsageStats(
                interval,
                startTime,
                endTime,
            )
        }.getOrNull() ?: emptyList()

        for (stat in statsList) {
            if (stat.totalTimeInForeground > 0L && stat.lastTimeUsed >= startTime) {
                val current = outUsageMap[stat.packageName]
                val duration = (current?.first ?: 0L) + stat.totalTimeInForeground
                val lastUsed = maxOf(current?.second ?: 0L, stat.lastTimeUsed)
                outUsageMap[stat.packageName] = Pair(duration, lastUsed)
            }
        }
    }

    private fun UsageEvents.asSequence(): Sequence<UsageEventRecord> = sequence {
        val event = UsageEvents.Event()
        while (hasNextEvent()) {
            getNextEvent(event)
            yield(
                UsageEventRecord(
                    eventType = event.eventType,
                    packageName = event.packageName,
                    timestamp = event.timeStamp,
                ),
            )
        }
    }
}
