package com.example.toolbox.ledger

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Immutable versions and an ETag-protected discovery index, shared by both tools. */
class WebDavHistory(private val client: WebDavClient, private val tool: BackupTool) {
    private val indexName = "${tool.key}-backups.json"

    fun list(): List<String> = parse(client.get(indexName)?.body).sortedDescending()

    fun read(name: String): String {
        require(nameRegex(tool).matches(name))
        return client.get(name)?.body ?: throw WebDavException(WebDavFailure.INVALID_REMOTE_FILE)
    }

    fun save(body: String, nowMillis: Long, zone: ZoneId): String {
        val name = snapshotName(tool, nowMillis, zone)
        client.put(name, body, ifNoneMatch = "*")
        repeat(3) { attempt ->
            val old = client.get(indexName)
            val etag = old?.let { WebDavClient.ifMatchValue(it.etag) }
            if (old != null && etag == null) throw WebDavException(WebDavFailure.VERSION_UNAVAILABLE)
            val previous = parse(old?.body)
            val all = (previous + name).distinct().sorted()
            val keep = all.takeLast(10)
            val index = JSONObject().put("snapshots", JSONArray(keep)).toString()
            try {
                client.put(indexName, index, ifMatch = etag, ifNoneMatch = if (old == null) "*" else null)
            } catch (failure: WebDavException) {
                if (failure.failure == WebDavFailure.PRECONDITION_FAILED && attempt < 2) return@repeat
                throw failure
            }
            (all - keep.toSet()).forEach { runCatching { client.delete(it) } }
            return name
        }
        throw WebDavException(WebDavFailure.PRECONDITION_FAILED)
    }

    private fun parse(body: String?): List<String> {
        if (body == null) return emptyList()
        return try {
            val array = JSONObject(body).getJSONArray("snapshots")
            require(array.length() <= 1000)
            (0 until array.length()).map { array.getString(it) }.also { names ->
                require(names.all { nameRegex(tool).matches(it) })
            }
        } catch (failure: Exception) {
            throw WebDavException(WebDavFailure.INVALID_REMOTE_FILE, cause = failure)
        }
    }

    companion object {
        fun nameRegex(tool: BackupTool) = Regex(
            "^${tool.key}-backup-\\d{8}-\\d{6}(?:-\\d{3}-[0-9a-f]{8})?\\.json$",
        )

        fun snapshotName(tool: BackupTool, millis: Long, zone: ZoneId): String =
            "${tool.key}-backup-${Instant.ofEpochMilli(millis).atZone(zone).format(
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"),
            )}-${UUID.randomUUID().toString().take(8)}.json"
    }
}
