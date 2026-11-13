package com.example.toolbox.ledger

import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

internal data class FileMerge<T>(val payload: T, val pulled: Int, val pushed: Int)
internal data class RemoteFile<T>(val payload: T?, val etag: String?, val exists: Boolean)
internal data class FileSyncResult<T>(
    val payload: T,
    val pulled: Int,
    val pushed: Int,
    val snapshotName: String?,
    val snapshotFailure: WebDavFailure?,
)

/** One implementation of legacy fallback, conditional writes, retries and history. */
internal class WholeFileWebDavSync<T>(
    private val client: WebDavClient,
    private val tool: BackupTool,
    private val fileName: String,
    private val legacyClients: List<WebDavClient>,
    private val empty: () -> T,
    private val decode: (String) -> T,
    private val encode: (T, Long) -> String,
    val merge: (T, T) -> FileMerge<T>,
    private val zone: ZoneId,
) {
    fun read(): RemoteFile<T> {
        val current = client.get(fileName)
        val etag = current?.let { WebDavClient.ifMatchValue(it.etag) }
        if (current != null && etag == null) throw WebDavException(WebDavFailure.VERSION_UNAVAILABLE)
        val source = current ?: legacyClients.firstNotNullOfOrNull { it.get(fileName) }
        return RemoteFile(source?.let { decode(it.body) }, etag, current != null)
    }

    fun combine(local: T, remote: RemoteFile<T>): FileMerge<T> = merge(local, remote.payload ?: empty())

    fun publish(payload: T, remote: RemoteFile<T>, now: Long): String {
        val body = encode(payload, now)
        client.put(fileName, body, ifMatch = remote.etag, ifNoneMatch = if (remote.exists) null else "*")
        return body
    }

    fun result(merged: FileMerge<T>, body: String, now: Long): FileSyncResult<T> {
        var name: String? = null
        var failure: WebDavFailure? = null
        try {
            name = WebDavHistory(client, tool).save(body, now, zone)
        } catch (error: Exception) {
            failure = (error as? WebDavException)?.failure ?: WebDavFailure.NETWORK
        }
        return FileSyncResult(merged.payload, merged.pulled, merged.pushed, name, failure)
    }

    fun sync(load: () -> T, apply: (T) -> Unit, now: Long): FileSyncResult<T> {
        // Preserve the single-target contract: a rejected PUT never changes local data.
        client.ensureFolder()
        repeat(MAX_ATTEMPTS) { attempt ->
            val remote = read()
            val merged = combine(load(), remote)
            // Read again only after a real precondition conflict, never before an ordinary PUT.
            try {
                val body = publish(merged.payload, remote, now)
                apply(merged.payload)
                return result(merged, body, now)
            } catch (error: WebDavException) {
                if (error.failure != WebDavFailure.PRECONDITION_FAILED || attempt == MAX_ATTEMPTS - 1) throw error
            }
        }
        error("Unreachable")
    }

    fun prepare(): RemoteFile<T> {
        client.ensureFolder()
        return read()
    }

    companion object {
        const val MAX_ATTEMPTS = 3

        /**
         * Read every target before publishing anywhere. Reuse each response's ETag;
         * retry the whole fan-out after a 412 so a later target's new rows reach earlier ones.
         * [apply] must atomically merge against current local data, never replace a stale snapshot.
         */
        suspend fun <T> syncTargets(
            targets: List<WholeFileWebDavSync<T>>,
            load: () -> T,
            apply: (T) -> Unit,
            now: Long,
        ): List<Result<FileSyncResult<T>>> = try {
            withContext(Dispatchers.IO) {
                require(targets.isNotEmpty())
                // Aliased service profiles must not race their own conditional PUTs.
                val unique = mutableListOf<WholeFileWebDavSync<T>>()
                val indices = targets.map { target ->
                    val existing = unique.indexOfFirst {
                        it.fileName == target.fileName && it.client.sameDestination(target.client)
                    }
                    if (existing >= 0) existing else unique.size.also { unique.add(target) }
                }
                if (unique.size != targets.size) {
                    val results = syncTargets(unique, load, apply, now)
                    return@withContext indices.map { results[it] }
                }
                var candidate = load()
                repeat(MAX_ATTEMPTS) { attempt ->
                    val reads = parallel(targets) { it.prepare() }
                    // Failed decrypts / reads abort before local writes or remote publication.
                    reads.firstOrNull { it.isFailure }?.exceptionOrNull()?.let { throw it }
                    val remotes = reads.map { it.getOrThrow() }
                    val local = load()
                    candidate = targets.first().merge(candidate, local).payload
                    for (index in targets.indices) {
                        candidate = targets[index].combine(candidate, remotes[index]).payload
                    }
                    val merged = candidate
                    val writes = parallel(targets.indices.toList()) { index ->
                        targets[index].publish(merged, remotes[index], now)
                    }
                    val conflict = writes.any {
                        (it.exceptionOrNull() as? WebDavException)?.failure == WebDavFailure.PRECONDITION_FAILED
                    }
                    if (conflict && attempt < MAX_ATTEMPTS - 1) return@repeat
                    // Match single-target sync: rejected publication alone must not
                    // import remote rows or mark local revisions as synchronized.
                    if (writes.any { it.isSuccess }) apply(merged)
                    return@withContext parallel(targets.indices.toList()) { index ->
                        val body = writes[index].getOrThrow()
                        val pulled = targets[index].combine(local, remotes[index]).pulled
                        val pushed = targets[index].combine(merged, remotes[index]).pushed
                        targets[index].result(FileMerge(merged, pulled, pushed), body, now)
                    }
                }
                error("Unreachable")
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            targets.map { Result.failure(error) }
        }

        private suspend fun <A, B> parallel(items: List<A>, block: (A) -> B): List<Result<B>> = coroutineScope {
            items.map { item ->
                async(Dispatchers.IO) {
                    try {
                        Result.success(block(item))
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        Result.failure(error)
                    }
                }
            }.awaitAll()
        }
    }
}
