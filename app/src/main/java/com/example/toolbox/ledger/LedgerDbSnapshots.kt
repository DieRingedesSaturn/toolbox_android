package com.example.toolbox.ledger

import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

object LedgerDbSnapshots {
    const val DIR_NAME = "ledger-db-snapshots"
    const val KEEP = 3
    private const val PREFIX = "toolbox_ledger-"
    private const val SUFFIX = ".db"
    private const val WAL_SUFFIX = "-wal"
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

    @Synchronized
    fun save(
        dbFile: File,
        dir: File,
        fromVersion: Int,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        pruneAfterSave: Boolean = true,
    ): File {
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create $dir" }
        // Never overwrite a recovery copy, including two saves in the same second.
        var stamp = nowMillis
        var target = File(dir, fileName(fromVersion, stamp, zone))
        while (target.exists() || File(target.path + WAL_SUFFIX).exists()) {
            stamp += 1_000L
            target = File(dir, fileName(fromVersion, stamp, zone))
        }
        withStagedCopy(dbFile, dir) { staged ->
            val wal = File(staged.path + WAL_SUFFIX)
            val targetWal = File(target.path + WAL_SUFFIX)
            try {
                // The main filename publishes the completed pair to list().
                if (wal.isFile) check(wal.renameTo(targetWal)) { "Cannot publish snapshot WAL" }
                check(staged.renameTo(target)) { "Cannot publish snapshot" }
            } catch (failure: Exception) {
                targetWal.delete()
                throw failure
            }
        }
        if (pruneAfterSave) prune(dir, KEEP)
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

    /** Isolate the database and its WAL; callers never open the original copy. */
    fun <T> withStagedCopy(source: File, parent: File, block: (File) -> T): T {
        require(source.isFile) { "Missing snapshot" }
        return withTemporaryDatabase(parent) { staged ->
            copyDurably(source, staged)
            val wal = File(source.path + WAL_SUFFIX)
            if (wal.isFile && wal.length() > 0L) {
                copyDurably(wal, File(staged.path + WAL_SUFFIX))
            }
            block(staged)
        }
    }

    fun <T> withTemporaryDatabase(parent: File, block: (File) -> T): T {
        check(parent.isDirectory || parent.mkdirs()) { "Cannot create staging directory" }
        val work = File(parent, ".pending-${UUID.randomUUID()}")
        check(work.mkdir()) { "Cannot create snapshot staging directory" }
        try {
            return block(File(work, "ledger.db"))
        } finally {
            work.deleteRecursively()
        }
    }

    private fun copyDurably(source: File, target: File) {
        source.inputStream().use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
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
