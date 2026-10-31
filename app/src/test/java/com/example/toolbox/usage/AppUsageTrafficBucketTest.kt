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
        assertEquals(NetworkBucketCategory.IGNORED, classifyNetworkBucket(-4))
    }

    @Test
    fun testSharedNetworkTotalUsesAvailableTransports() {
        assertEquals(300L, SharedNetworkTraffic(100L, 200L).totalBytes)
        assertEquals(100L, SharedNetworkTraffic(100L, null).totalBytes)
        assertNull(SharedNetworkTraffic(null, null).totalBytes)
    }
}
