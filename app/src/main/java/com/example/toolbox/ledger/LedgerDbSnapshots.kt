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
    private const val SHM_SUFFIX = "-shm"
    private const val RESTORE_TMP_SUFFIX = ".restore-tmp"
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)
    private val NAME_PATTERN = Regex(
        "^${Regex.escape(PREFIX)}(\\d{8}-\\d{6})-v(\\d+)${Regex.escape(SUFFIX)}\$",
    )

    data class Snapshot(
        val file: File,
        val takenAtMillis: Long,
        val schemaVersion: Int,
    )

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

    fun list(dir: File, zone: ZoneId = ZoneId.systemDefault()): List<Snapshot> =
        dir.listFiles { file -> file.isFile && NAME_PATTERN.matches(file.name) }
            .orEmpty()
            .mapNotNull { file ->
                val match = NAME_PATTERN.matchEntire(file.name) ?: return@mapNotNull null
                val millis = runCatching {
                    LocalDateTime.parse(match.groupValues[1], STAMP)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli()
                }.getOrNull() ?: return@mapNotNull null
                Snapshot(file, millis, match.groupValues[2].toInt())
            }
            .sortedByDescending { it.takenAtMillis }

    fun restore(snapshot: File, dbFile: File) {
        require(snapshot.isFile) { "Missing snapshot $snapshot" }
        val tmp = File(dbFile.path + RESTORE_TMP_SUFFIX)
        val tmpWal = File(tmp.path + WAL_SUFFIX)
        snapshot.copyTo(tmp, overwrite = true)
        val snapshotWal = File(snapshot.path + WAL_SUFFIX)
        if (snapshotWal.isFile && snapshotWal.length() > 0L) {
            snapshotWal.copyTo(tmpWal, overwrite = true)
        } else {
            tmpWal.delete()
        }
        check(tmp.renameTo(dbFile)) { "Cannot replace $dbFile" }
        val dbWal = File(dbFile.path + WAL_SUFFIX)
        if (tmpWal.isFile) {
            check(tmpWal.renameTo(dbWal)) { "Cannot replace $dbWal" }
        } else {
            dbWal.delete()
        }
        File(dbFile.path + SHM_SUFFIX).delete()
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
