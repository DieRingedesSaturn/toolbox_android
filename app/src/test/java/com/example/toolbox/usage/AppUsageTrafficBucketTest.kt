package com.example.toolbox.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test


class AppUsageTrafficBucketTest {

    @Test
    fun testTetheringBucketIsNotTreatedAsAnAppOrDropped() {
        // NetworkStats.Bucket.UID_TETHERING is the stable special UID -5.
        assertEquals(NetworkBucketCategory.TETHERING, classifyNetworkBucket(-5))
        assertEquals(NetworkBucketCategory.APP, classifyNetworkBucket(10_123))
        assertEquals(NetworkBucketCategory.IGNORED, classifyNetworkBucket(0))
        assertEquals(NetworkBucketCategory.IGNORED, classifyNetworkBucket(-1))
        assertEquals(NetworkBucketCategory.REMOVED, classifyNetworkBucket(-4))
    }

    @Test
    fun sharedUidTrafficCountsOnce() {
        val shared = AppUsageItem(
            packageName = "a", label = "A", category = AppCategory.OTHER,
            foregroundDurationMillis = 0L, lastTimeUsedMillis = 0L,
            wifiRxBytes = 40L, wifiTxBytes = 10L,
            cellularRxBytes = 0L, cellularTxBytes = 0L,
            sharedUidTraffic = true, uid = 1000,
        )
        val sibling = shared.copy(packageName = "b", label = "B")
        val other = shared.copy(
            packageName = "c", uid = 2000, sharedUidTraffic = false,
            wifiRxBytes = 5L, wifiTxBytes = 5L,
        )
        assertEquals(60L, attributedTrafficBytes(listOf(shared, sibling, other), wifi = true))
    }

    @Test
    fun deviceTotalWinsWhenLargerThanUidSum() {
        assertEquals(100L, preferredTrafficTotal(100L, 60L))
        assertEquals(90L, preferredTrafficTotal(0L, 90L))
        assertEquals(40L, preferredTrafficTotal(null, 40L))
        assertEquals(15L, preferredTrafficTotal(15L, null))
        assertNull(preferredTrafficTotal(null, null))
    }

    @Test
    fun leftoverIsTotalMinusAppsAndTethering() {
        assertEquals(70L, leftoverTrafficBytes(100L, 20L, 10L))
        assertEquals(0L, leftoverTrafficBytes(10L, 20L, 10L))
        assertNull(leftoverTrafficBytes(null, 20L, 10L))
    }

    @Test
    fun testSharedNetworkTotalUsesAvailableTransports() {
        assertEquals(300L, SharedNetworkTraffic(100L, 200L).totalBytes)
        assertEquals(100L, SharedNetworkTraffic(100L, null).totalBytes)
        assertNull(SharedNetworkTraffic(null, null).totalBytes)
    }

    @Test
    fun combinedUnlistedTrafficAddsTetheringAndLeftover() {
        val combined = SharedNetworkTraffic.combine(
            SharedNetworkTraffic(10L, 20L),
            SharedNetworkTraffic(70L, 0L),
        )
        assertEquals(80L, combined?.wifiBytes)
        assertEquals(20L, combined?.cellularBytes)
        assertEquals(100L, combined?.totalBytes)
        assertNull(SharedNetworkTraffic.combine(SharedNetworkTraffic(0L, 0L), null))
    }
}
