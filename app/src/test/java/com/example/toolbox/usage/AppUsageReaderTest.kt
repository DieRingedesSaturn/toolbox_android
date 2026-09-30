package com.example.toolbox.usage

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUsageReaderTest {

    @Test
    fun testYesterdayUsesLocalMidnightAcrossDaylightSavingChange() {
        val zone = ZoneId.of("America/New_York")
        val todayStart = LocalDate.of(2026, 3, 9).atStartOfDay(zone).toInstant().toEpochMilli()
        val yesterdayStart = LocalDate.of(2026, 3, 8).atStartOfDay(zone).toInstant().toEpochMilli()

        val (start, end) = AppUsageReader.calculateTimeWindow(
            UsageTimeRange.YESTERDAY,
            todayStart + 12 * 60 * 60 * 1000L,
            zone,
        )

        assertEquals(yesterdayStart, start)
        assertEquals(todayStart - 1L, end)
        assertEquals(23 * 60 * 60 * 1000L, todayStart - yesterdayStart)
    }

    @Test
    fun testScreenInteractiveTimeCalculation() {
        val startTime = 10_000L
        val endTime = 100_000L

        // Screen on from 20_000 to 40_000 (20s)
        // Screen on from 50_000 to 70_000 (20s)
        val events = sequenceOf(
            UsageEventRecord(eventType = 15, packageName = "android", timestamp = 20_000L),
            UsageEventRecord(eventType = 16, packageName = "android", timestamp = 40_000L),
            UsageEventRecord(eventType = 15, packageName = "android", timestamp = 50_000L),
            UsageEventRecord(eventType = 16, packageName = "android", timestamp = 70_000L),
        )

        val result = AppUsageReader.processUsageEvents(events, startTime, endTime)
        assertEquals(40_000L, result.measuredScreenDurationMillis)
    }

    @Test
    fun testAppForegroundDurationCappedByScreenOff() {
        val startTime = 10_000L
        val endTime = 100_000L

        // App starts at 20_000, screen on at 20_000
        // Screen turns off at 40_000 (app was NOT explicitly paused before screen off)
        val events = sequenceOf(
            UsageEventRecord(eventType = 15, packageName = "android", timestamp = 20_000L),
            UsageEventRecord(eventType = 1, packageName = "com.test.app", timestamp = 20_000L),
            UsageEventRecord(eventType = 16, packageName = "android", timestamp = 40_000L),
        )

        val result = AppUsageReader.processUsageEvents(events, startTime, endTime)
        assertEquals(20_000L, result.measuredScreenDurationMillis)
        assertEquals(20_000L, result.appDurations["com.test.app"])
    }

    @Test
    fun testMidnightBoundaryTruncation() {
        val startTime = 50_000L
        val endTime = 100_000L

        // App was opened before startTime at 30_000 and paused at 70_000
        // Screen was on since 30_000 and turns off at 70_000
        val events = sequenceOf(
            UsageEventRecord(eventType = 15, packageName = "android", timestamp = 30_000L),
            UsageEventRecord(eventType = 1, packageName = "com.test.app", timestamp = 30_000L),
            UsageEventRecord(eventType = 2, packageName = "com.test.app", timestamp = 70_000L),
            UsageEventRecord(eventType = 16, packageName = "android", timestamp = 70_000L),
        )

        val result = AppUsageReader.processUsageEvents(events, startTime, endTime)
        // Screen was on from startTime (50_000) to 70_000 = 20_000L
        assertEquals(20_000L, result.measuredScreenDurationMillis)
        // App duration from startTime (50_000) to 70_000 = 20_000L
        assertEquals(20_000L, result.appDurations["com.test.app"])
    }

    @Test
    fun testDuplicateStoppedEventsDoNotAccumulatePhantomHours() {
        val startTime = 50_000L
        val endTime = 100_000L

        // Many background stopped/paused events without any resume
        val events = sequenceOf(
            UsageEventRecord(eventType = 2, packageName = "com.phantom.app", timestamp = 55_000L),
            UsageEventRecord(eventType = 23, packageName = "com.phantom.app", timestamp = 60_000L),
            UsageEventRecord(eventType = 23, packageName = "com.phantom.app", timestamp = 65_000L),
            UsageEventRecord(eventType = 2, packageName = "com.phantom.app", timestamp = 70_000L),
        )

        val result = AppUsageReader.processUsageEvents(events, startTime, endTime)
        assertEquals(0L, result.measuredScreenDurationMillis)
        assertTrue(result.appDurations.isEmpty())
    }

    @Test
    fun testEmptyEventsFallback() {
        val result = AppUsageReader.processUsageEvents(emptySequence(), 0L, 10_000L)
        assertEquals(0L, result.measuredScreenDurationMillis)
        assertTrue(result.appDurations.isEmpty())
        assertTrue(result.appLastUsed.isEmpty())
    }
}
