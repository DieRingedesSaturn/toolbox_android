package com.example.toolbox.ledger

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LedgerWebDavSyncTest {

    /**
     * Minimal in-process WebDAV-like file server on a raw ServerSocket
     * (com.sun.net.httpserver is not on the unit-test classpath): GET/PUT/DELETE
     * backed by a map, strong quoted ETags, If-Match handling, basic-auth check,
     * and injectable failures (missing folder, one-shot 412 via etag bump,
     * snapshot PUT 503).
     */
    private class FakeWebDavServer(
        private val expectedAuth: String,
    ) {
        private val files = ConcurrentHashMap<String, Pair<String, String>>()
        private var etagCounter = 0
        private val serverSocket = ServerSocket()

        @Volatile
        var folderMissing = false

        @Volatile
        var bumpEtagAfterNextGet = false

        @Volatile
        var failSnapshotPuts = false

        @Volatile
        private var running = true

        val folderUrl: String

        init {
            serverSocket.bind(InetSocketAddress("127.0.0.1", 0))
            folderUrl = "http://127.0.0.1:${serverSocket.localPort}/dav/"
            thread(name = "fake-webdav") {
                while (running) {
                    val client = try {
                        serverSocket.accept()
                    } catch (e: SocketException) {
                        break
                    }
                    runCatching { handle(client) }
                }
            }
        }

        fun putRaw(name: String, body: String) {
            files[name] = body to "\"etag-${++etagCounter}\""
        }

        fun bodyOf(name: String): String? = files[name]?.first

        fun fileNamesMatching(regex: Regex): List<String> =
            files.keys.filter { regex.matches(it) }

        fun stop() {
            running = false
            serverSocket.close()
        }

        private fun handle(client: Socket) {
            client.use { socket ->
                val input = socket.getInputStream()
                val head = StringBuilder()
                while (true) {
                    val byte = input.read()
                    if (byte < 0 || head.length > MAX_HEAD_BYTES) return
                    head.append(byte.toChar())
                    if (head.endsWith("\r\n\r\n")) break
                }
                val lines = head.toString().split("\r\n")
                val requestParts = lines.first().split(" ")
                if (requestParts.size < 2) return
                val method = requestParts[0]
                val name = requestParts[1].removePrefix("/dav/")
                val headers = lines.drop(1)
                    .filter { ':' in it }
                    .associate {
                        it.substringBefore(':').trim().lowercase() to
                            it.substringAfter(':').trim()
                    }

                if (headers["expect"].equals("100-continue", ignoreCase = true)) {
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                        flush()
                    }
                }
                val bodyLength = headers["content-length"]?.toIntOrNull() ?: 0
                val body = ByteArray(bodyLength)
                var received = 0
                while (received < bodyLength) {
                    val count = input.read(body, received, bodyLength - received)
                    if (count < 0) break
                    received += count
                }

                if (headers["authorization"] != expectedAuth) {
                    respond(socket, 401)
                    return
                }
                when (method) {
                    "GET" -> {
                        val file = files[name]
                        if (file == null) {
                            respond(socket, 404)
                        } else {
                            respond(socket, 200, file.first, etag = file.second)
                            if (bumpEtagAfterNextGet) {
                                bumpEtagAfterNextGet = false
                                files[name] = file.first to "\"etag-${++etagCounter}\""
                            }
                        }
                    }
                    "PUT" -> {
                        val ifMatch = headers["if-match"]
                        when {
                            folderMissing -> respond(socket, 409)
                            failSnapshotPuts &&
                                WebDavSnapshots.NAME_REGEX.matches(name) ->
                                respond(socket, 503)
                            ifMatch != null && files[name]?.second != ifMatch ->
                                respond(socket, 412)
                            else -> {
                                files[name] = body.toString(Charsets.UTF_8) to
                                    "\"etag-${++etagCounter}\""
                                respond(socket, 201)
                            }
                        }
                    }
                    "DELETE" ->
                        if (files.remove(name) != null) {
                            respond(socket, 204)
                        } else {
                            respond(socket, 404)
                        }
                    else -> respond(socket, 405)
                }
            }
        }

        private fun respond(
            socket: Socket,
            code: Int,
            body: String = "",
            etag: String? = null,
        ) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val head = buildString {
                append("HTTP/1.1 ").append(code).append(' ').append(REASONS[code] ?: "")
                append("\r\nContent-Length: ").append(bytes.size).append("\r\n")
                etag?.let { append("ETag: ").append(it).append("\r\n") }
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().apply {
                write(head.toByteArray(Charsets.ISO_8859_1))
                write(bytes)
                flush()
            }
        }

        companion object {
            private const val MAX_HEAD_BYTES = 64 * 1024
            private val REASONS = mapOf(
                200 to "OK",
                201 to "Created",
                204 to "No Content",
                400 to "Bad Request",
                401 to "Unauthorized",
                404 to "Not Found",
                405 to "Method Not Allowed",
                409 to "Conflict",
                412 to "Precondition Failed",
                503 to "Service Unavailable",
            )
        }
    }

    private val server = FakeWebDavServer(WebDavPaths.basicAuthHeader("user", "pass"))

    @After
    fun tearDown() {
        server.stop()
    }

    private fun entry(uuid: String, updatedAt: Long, deletedAt: Long? = null) =
        LedgerEntry(
            uuid = uuid,
            title = uuid,
            amountCents = 100L,
            occurredAtMillis = updatedAt,
            updatedAtMillis = updatedAt,
            deletedAtMillis = deletedAt,
        )

    private fun newSync(
        local: MutableList<LedgerEntry>,
        username: String = "user",
        password: String = "pass",
    ) = LedgerWebDavSync(
        client = WebDavClient(server.folderUrl, username, password),
        loadLocal = { local.toList() },
        loadLocalTags = { emptyList() },
        applyMerged = { merged ->
            local.clear()
            local.addAll(merged.entries)
        },
        zone = ZoneId.of("UTC"),
    )

    private fun assertSyncFails(failure: WebDavFailure, block: () -> Unit) {
        try {
            block()
            fail("expected WebDavException $failure")
        } catch (e: WebDavException) {
            assertEquals(failure, e.failure)
        }
    }

    @Test
    fun testFirstSyncCreatesLedgerJsonSnapshotAndIndex() {
        val local = mutableListOf(entry("a", 100L), entry("b", 200L))
        val result = newSync(local).sync(nowMillis = 1_700_000_000_000L)

        assertEquals(2, result.totalEntries)
        assertEquals(2, result.pushedCount)
        assertEquals(0, result.pulledCount)
        assertNotNull(result.snapshotName)
        assertNull(result.snapshotFailure)

        assertNotNull(server.bodyOf(LedgerWebDavSync.LEDGER_FILE))
        assertNotNull(server.bodyOf(result.snapshotName!!))
        val index = WebDavSnapshots.parseIndex(
            server.bodyOf(WebDavSnapshots.INDEX_FILE),
        )
        assertEquals(listOf(result.snapshotName), index)
        assertTrue(local.all { it.syncStatus == LedgerSyncStatus.SYNCED })
    }

    @Test
    fun testTwoDevicesMergeBothWaysIncludingTombstone() {
        val deviceA = mutableListOf(entry("a", 100L))
        newSync(deviceA).sync(nowMillis = 1_700_000_000_000L)

        val deviceB = mutableListOf(entry("b", 200L))
        val resultB = newSync(deviceB).sync(nowMillis = 1_700_000_100_000L)
        assertEquals(2, resultB.totalEntries)
        assertEquals(1, resultB.pulledCount)
        assertEquals(1, resultB.pushedCount)
        assertEquals(setOf("a", "b"), deviceB.map { it.uuid }.toSet())

        // Device B deletes "a" (tombstone wins because it is newer).
        deviceB.replaceAll { existing ->
            if (existing.uuid == "a") {
                existing.copy(deletedAtMillis = 300L, updatedAtMillis = 300L)
            } else {
                existing
            }
        }
        newSync(deviceB).sync(nowMillis = 1_700_000_200_000L)

        newSync(deviceA).sync(nowMillis = 1_700_000_300_000L)
        val entryA = deviceA.single { it.uuid == "a" }
        assertNotNull(entryA.deletedAtMillis)
        assertTrue(deviceA.any { it.uuid == "b" })
    }

    @Test
    fun testEtagBumpDuringSyncRetriesAndSucceeds() {
        val local = mutableListOf(entry("a", 100L))
        newSync(local).sync(nowMillis = 1_700_000_000_000L)

        server.bumpEtagAfterNextGet = true
        local.add(entry("c", 300L))
        val result = newSync(local).sync(nowMillis = 1_700_000_100_000L)

        assertEquals(2, result.totalEntries)
        val remote = LedgerSyncSerializer.fromJsonString(
            server.bodyOf(LedgerWebDavSync.LEDGER_FILE)!!,
        )
        assertEquals(setOf("a", "c"), remote.entries.map { it.uuid }.toSet())
    }

    @Test
    fun testWrongAuthThrowsUnauthorized() {
        val local = mutableListOf(entry("a", 100L))
        assertSyncFails(WebDavFailure.UNAUTHORIZED) {
            newSync(local, password = "wrong").sync()
        }
    }

    @Test
    fun testMissingFolderThrowsFolderMissing() {
        server.folderMissing = true
        val local = mutableListOf(entry("a", 100L))
        assertSyncFails(WebDavFailure.FOLDER_MISSING) {
            newSync(local).sync()
        }
    }

    @Test
    fun testUnparseableRemoteStopsSyncWithoutOverwrite() {
        server.putRaw(LedgerWebDavSync.LEDGER_FILE, "not json at all")
        val local = mutableListOf(entry("a", 100L))
        assertSyncFails(WebDavFailure.INVALID_REMOTE_FILE) {
            newSync(local).sync()
        }
        assertEquals("not json at all", server.bodyOf(LedgerWebDavSync.LEDGER_FILE))
    }

    @Test
    fun testElevenSyncsKeepTenSnapshots() {
        val local = mutableListOf(entry("a", 100L))
        val firstName = WebDavSnapshots.snapshotName(1_700_000_000_000L, ZoneId.of("UTC"))
        repeat(11) { i ->
            newSync(local).sync(nowMillis = 1_700_000_000_000L + i * 1000L)
        }
        val snapshots = server.fileNamesMatching(WebDavSnapshots.NAME_REGEX)
        assertEquals(WebDavSnapshots.KEEP, snapshots.size)
        assertFalse(snapshots.contains(firstName))
        assertEquals(
            WebDavSnapshots.KEEP,
            WebDavSnapshots.parseIndex(server.bodyOf(WebDavSnapshots.INDEX_FILE)).size,
        )
    }

    @Test
    fun testSnapshotFailureDoesNotFailSync() {
        server.failSnapshotPuts = true
        val local = mutableListOf(entry("a", 100L))
        val result = newSync(local).sync(nowMillis = 1_700_000_000_000L)

        assertEquals(WebDavFailure.HTTP_ERROR, result.snapshotFailure)
        assertNull(result.snapshotName)
        assertNotNull(server.bodyOf(LedgerWebDavSync.LEDGER_FILE))
    }

    @Test
    fun testNewerSchemaThrows() {
        val payload = LedgerSyncSerializer.toJsonString(
            LedgerSyncPayload(schemaVersion = 99, entries = emptyList()),
        )
        server.putRaw(LedgerWebDavSync.LEDGER_FILE, payload)
        val local = mutableListOf(entry("a", 100L))
        assertSyncFails(WebDavFailure.NEWER_SCHEMA) {
            newSync(local).sync()
        }
    }
}
