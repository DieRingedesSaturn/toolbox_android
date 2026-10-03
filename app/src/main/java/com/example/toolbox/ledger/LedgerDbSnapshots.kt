package com.example.toolbox.ledger

import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object LedgerDbSnapshots {
    const val DIR_NAME = "ledger-db-snapshots"
    const val KEEP = 3
    private const val PREFIX = "toolbox_ledger-"
    private const val SUFFIX = ".db"
    private const val WAL_SUFFIX = "-wal"
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)

    fun fileName(
        fromVersion: Int,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = PREFIX +
        LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone).format(STAMP) +
        "-v$fromVersion" + SUFFIX

    fun save(
        dbFile: File,
        dir: File,
        fromVersion: Int,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): File {
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create $dir" }
        val target = File(dir, fileName(fromVersion, nowMillis, zone))
        dbFile.copyTo(target, overwrite = true)
        val wal = File(dbFile.path + WAL_SUFFIX)
        val targetWal = File(target.path + WAL_SUFFIX)
        if (wal.length() > 0L) wal.copyTo(targetWal, overwrite = true) else targetWal.delete()
        prune(dir, KEEP)
        return target
    }

    fun prune(dir: File, keep: Int) {
        dir.listFiles { file ->
            file.isFile && file.name.startsWith(PREFIX) && file.name.endsWith(SUFFIX)
        }
            .orEmpty()
            .sortedByDescending { it.name }
            .drop(keep)
            .forEach { stale ->
                stale.delete()
                File(stale.path + WAL_SUFFIX).delete()
            }
    }
}
