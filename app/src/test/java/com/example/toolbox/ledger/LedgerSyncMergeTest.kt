package com.example.toolbox.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerSyncMergeTest {

    private fun entry(
        uuid: String,
        updatedAt: Long,
        title: String = uuid,
        deletedAt: Long? = null,
    ) = LedgerEntry(
        uuid = uuid,
        title = title,
        amountCents = 100L,
        occurredAtMillis = updatedAt,
        updatedAtMillis = updatedAt,
        deletedAtMillis = deletedAt,
        syncStatus = LedgerSyncStatus.PENDING_PUSH,
    )

    @Test
    fun testRemoteNewerWins() {
        val local = listOf(entry("a", updatedAt = 100L, title = "local"))
        val remote = listOf(entry("a", updatedAt = 200L, title = "remote"))
        val result = LedgerSyncMerge.merge(local, remote)
        assertEquals("remote", result.entries.single().title)
        assertEquals(1, result.pulledCount)
        assertEquals(0, result.pushedCount)
    }

    @Test
    fun testLocalNewerWins() {
        val local = listOf(entry("a", updatedAt = 200L, title = "local"))
        val remote = listOf(entry("a", updatedAt = 100L, title = "remote"))
        val result = LedgerSyncMerge.merge(local, remote)
        assertEquals("local", result.entries.single().title)
        assertEquals(0, result.pulledCount)
        assertEquals(1, result.pushedCount)
    }

    @Test
    fun testTieGoesToRemote() {
        val local = listOf(entry("a", updatedAt = 100L, title = "local"))
        val remote = listOf(entry("a", updatedAt = 100L, title = "remote"))
        val result = LedgerSyncMerge.merge(local, remote)
        assertEquals("remote", result.entries.single().title)
        assertEquals(0, result.pulledCount)
        assertEquals(0, result.pushedCount)
    }

    @Test
    fun testLocalTombstonePropagatesToRemote() {
        val local = listOf(entry("a", updatedAt = 200L, deletedAt = 200L))
        val remote = listOf(entry("a", updatedAt = 100L))
        val result = LedgerSyncMerge.merge(local, remote)
        assertNotNull(result.entries.single().deletedAtMillis)
        assertEquals(1, result.pushedCount)
    }

    @Test
    fun testRemoteTombstonePropagatesToLocal() {
        val local = listOf(entry("a", updatedAt = 100L))
        val remote = listOf(entry("a", updatedAt = 200L, deletedAt = 200L))
        val result = LedgerSyncMerge.merge(local, remote)
        assertNotNull(result.entries.single().deletedAtMillis)
        assertEquals(1, result.pulledCount)
    }

    @Test
    fun testLocalOnlyAndRemoteOnlyAreKept() {
        val local = listOf(entry("local-only", updatedAt = 10L))
        val remote = listOf(entry("remote-only", updatedAt = 20L))
        val result = LedgerSyncMerge.merge(local, remote)
        assertEquals(2, result.entries.size)
        assertEquals(1, result.pulledCount)
        assertEquals(1, result.pushedCount)
    }

    @Test
    fun testAllOutputSyncedAndSorted() {
        val local = listOf(
            entry("b", updatedAt = 300L),
            entry("a", updatedAt = 100L),
        )
        val remote = listOf(entry("c", updatedAt = 200L))
        val result = LedgerSyncMerge.merge(local, remote)
        assertTrue(result.entries.all { it.syncStatus == LedgerSyncStatus.SYNCED })
        assertEquals(listOf("a", "c", "b"), result.entries.map { it.uuid })
    }
}
