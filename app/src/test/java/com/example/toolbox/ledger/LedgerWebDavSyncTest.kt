package com.example.toolbox.ledger

import kotlinx.coroutines.runBlocking
import com.example.toolbox.ui.AppSettingsBackup
import com.example.toolbox.totp.TotpVaultException
import com.example.toolbox.totp.TotpEntry
import com.example.toolbox.totp.TotpVaultCrypto
import com.example.toolbox.totp.TotpWebDavSync
import org.json.JSONObject
import org.junit.Assert.assertThrows
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
     * backed by a map, strong quoted ETags, conditional writes, basic-auth check,
     * and injectable failures (missing folder, one-shot 412 via etag bump,
     * snapshot PUT 503).
     */
    private class FakeWebDavServer(
        private val expectedAuth: String,
    ) {
        private val files = ConcurrentHashMap<String, Pair<String, String>>()
        private val requests = ConcurrentHashMap<String, Int>()
        fun requests(method: String, name: String): Int = requests["$method:$name"] ?: 0
        private var etagCounter = 0
        private val serverSocket = ServerSocket()

        @Volatile
        var folderMissing = false

        @Volatile
        var bumpEtagAfterNextGet = false

        @Volatile
        var createLedgerAfterNextMissingGet: String? = null

        @Volatile
        var omitEtagOnGet = false

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
                requests.merge("$method:$name", 1, Int::plus)
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
                            if (name == LedgerWebDavSync.LEDGER_FILE) {
                                createLedgerAfterNextMissingGet?.let { body ->
                                    createLedgerAfterNextMissingGet = null
                                    putRaw(name, body)
                                }
                            }
                        } else {
                            respond(socket, 200, file.first, etag = if (omitEtagOnGet) null else file.second)
                            if (bumpEtagAfterNextGet) {
                                bumpEtagAfterNextGet = false
                                files[name] = file.first to "\"etag-${++etagCounter}\""
                            }
                        }
                    }
                    "PUT" -> {
                        val ifMatch = headers["if-match"]
                        val ifNoneMatch = headers["if-none-match"]
                        when {
                            folderMissing -> respond(socket, 409)
                            failSnapshotPuts &&
                                WebDavSnapshots.NAME_REGEX.matches(name) ->
                                respond(socket, 503)
                            ifMatch != null && files[name]?.second != ifMatch ->
                                respond(socket, 412)
                            ifNoneMatch == "*" && files.containsKey(name) ->
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
    fun allTargetsReceiveTheUnionWithOneMainGetEach() = runBlocking {
        val other = FakeWebDavServer(WebDavPaths.basicAuthHeader("user", "pass"))
        try {
            server.putRaw(LedgerWebDavSync.LEDGER_FILE, LedgerSyncSerializer.toJsonString(LedgerSyncPayload(entries = listOf(entry("a", 100)))))
            other.putRaw(LedgerWebDavSync.LEDGER_FILE, LedgerSyncSerializer.toJsonString(LedgerSyncPayload(entries = listOf(entry("b", 200)))))
            var local = LedgerSyncPayload(entries = emptyList())
            var applied = 0
            val engines = listOf(server, other).map { target -> LedgerWebDavSync(
                WebDavClient(target.folderUrl, "user", "pass"), loadLocal = { emptyList() }, loadLocalTags = { emptyList() }, applyMerged = {},
            ).engine }
            val results = WholeFileWebDavSync.syncTargets(engines, { local }, { local = it; applied++ }, 1000L)
            assertTrue(results.all { it.isSuccess })
            for (target in listOf(server, other)) {
                assertEquals(setOf("a", "b"), LedgerBackup.parse(target.bodyOf(LedgerWebDavSync.LEDGER_FILE)!!).entries.map { it.uuid }.toSet())
                assertEquals(1, target.requests("GET", LedgerWebDavSync.LEDGER_FILE))
            }
            assertEquals(1, applied)
        } finally { other.stop() }
    }

    @Test
    fun failedTargetReadLeavesThatTargetUntouchedWhileHealthyTargetSyncs() = runBlocking {
        val other = FakeWebDavServer(WebDavPaths.basicAuthHeader("user", "pass"))
        try {
            val body = LedgerSyncSerializer.toJsonString(LedgerSyncPayload(entries = listOf(entry("remote", 100))))
            server.putRaw(LedgerWebDavSync.LEDGER_FILE, body)
            other.putRaw(LedgerWebDavSync.LEDGER_FILE, body)
            other.omitEtagOnGet = true
            var applied = false
            val engines = listOf(server, other).map { target -> LedgerWebDavSync(
                WebDavClient(target.folderUrl, "user", "pass"), loadLocal = { emptyList() }, loadLocalTags = { emptyList() }, applyMerged = {},
            ).engine }
            val result = WholeFileWebDavSync.syncTargets(engines, { LedgerSyncPayload(entries = emptyList()) }, { applied = true }, 1000L)
            assertTrue(result[0].isSuccess)
            assertEquals(WebDavFailure.VERSION_UNAVAILABLE,
                (result[1].exceptionOrNull() as WebDavException).failure)
            assertTrue(applied)
            assertEquals(1, server.requests("PUT", LedgerWebDavSync.LEDGER_FILE))
            assertEquals(0, other.requests("PUT", LedgerWebDavSync.LEDGER_FILE))
            assertEquals(body, other.bodyOf(LedgerWebDavSync.LEDGER_FILE))
        } finally { other.stop() }
    }

    @Test
    fun wrongTotpPasswordOnlyBlocksTheAffectedTarget() = runBlocking {
        val other = FakeWebDavServer(WebDavPaths.basicAuthHeader("user", "pass"))
        val pass = "correct-backup-password"
        val token = TotpEntry(uuid = "healthy", issuer = "Service", accountName = "account",
            secret = "JBSWY3DPEHPK3PXP", createdAtMillis = 100L)
        try {
            server.putRaw(TotpWebDavSync.VAULT_FILE,
                TotpVaultCrypto.encryptVault(listOf(token), pass, iterations = 1000))
            val unreadable = TotpVaultCrypto.encryptVault(listOf(token.copy(uuid = "unreadable")),
                "different-backup-password", iterations = 1000)
            other.putRaw(TotpWebDavSync.VAULT_FILE, unreadable)
            var local = listOf(token.copy(uuid = "local"))
            var applied = 0
            val engines = listOf(server, other).map { target ->
                TotpWebDavSync(WebDavClient(target.folderUrl, "user", "pass"), pass,
                    loadLocal = { emptyList() }, applyMerged = {}, iterations = 1000).engine
            }
            val results = WholeFileWebDavSync.syncTargets(engines, { local }, {
                local = it
                applied++
            }, 1000L)
            assertTrue(results[0].isSuccess)
            assertTrue(results[1].exceptionOrNull() is TotpVaultException)
            assertEquals(1, applied)
            assertEquals(setOf("healthy", "local"), local.map { it.uuid }.toSet())
            assertEquals(setOf("healthy", "local"), TotpVaultCrypto.decryptVault(
                server.bodyOf(TotpWebDavSync.VAULT_FILE)!!, pass).map { it.uuid }.toSet())
            assertEquals(unreadable, other.bodyOf(TotpWebDavSync.VAULT_FILE))
            assertEquals(0, other.requests("PUT", TotpWebDavSync.VAULT_FILE))
        } finally {
            other.stop()
        }
    }

    @Test
    fun allReadFailuresKeepTheirOwnErrorsAndDoNotApply() = runBlocking {
        server.putRaw(LedgerWebDavSync.LEDGER_FILE,
            LedgerSyncSerializer.toJsonString(LedgerSyncPayload(entries = listOf(entry("remote", 100)))))
        server.omitEtagOnGet = true
        var applied = false
        val engines = listOf("wrong", "pass").map { password ->
            LedgerWebDavSync(WebDavClient(server.folderUrl, "user", password),
                loadLocal = { emptyList() }, loadLocalTags = { emptyList() }, applyMerged = {}).engine
        }
        val results = WholeFileWebDavSync.syncTargets(engines,
            { LedgerSyncPayload(entries = emptyList()) }, { applied = true }, 1000L)
        assertEquals(listOf(WebDavFailure.UNAUTHORIZED, WebDavFailure.VERSION_UNAVAILABLE),
            results.map { (it.exceptionOrNull() as WebDavException).failure })
        assertFalse(applied)
        assertEquals(0, server.requests("PUT", LedgerWebDavSync.LEDGER_FILE))
    }

    @Test
    fun conflictRetriesTheWholeFanOutAndAppliesLocalOnlyOnce() = runBlocking {
        val other = FakeWebDavServer(WebDavPaths.basicAuthHeader("user", "pass"))
        try {
            for (target in listOf(server, other)) target.putRaw(LedgerWebDavSync.LEDGER_FILE,
                LedgerSyncSerializer.toJsonString(LedgerSyncPayload(entries = listOf(entry(if (target === server) "a" else "b", 100)))))
            other.bumpEtagAfterNextGet = true
            var applied = 0
            val engines = listOf(server, other).map { target -> LedgerWebDavSync(
                WebDavClient(target.folderUrl, "user", "pass"), loadLocal = { emptyList() }, loadLocalTags = { emptyList() }, applyMerged = {},
            ).engine }
            val result = WholeFileWebDavSync.syncTargets(engines, { LedgerSyncPayload(entries = emptyList()) }, { applied++ }, 1000L)
            assertTrue(result.all { it.isSuccess })
            assertEquals(1, applied)
            for (target in listOf(server, other)) {
                assertEquals(2, target.requests("GET", LedgerWebDavSync.LEDGER_FILE))
                assertEquals(setOf("a", "b"), LedgerBackup.parse(target.bodyOf(LedgerWebDavSync.LEDGER_FILE)!!).entries.map { it.uuid }.toSet())
            }
        } finally { other.stop() }
    }

    @Test
    fun rejectedPublicationDoesNotApplyRemoteRowsLocally() = runBlocking {
        server.putRaw(LedgerWebDavSync.LEDGER_FILE,
            LedgerSyncSerializer.toJsonString(LedgerSyncPayload(entries = listOf(entry("remote", 100)))))
        server.folderMissing = true
        var applied = false
        val engine = LedgerWebDavSync(
            WebDavClient(server.folderUrl, "user", "pass"),
            loadLocal = { emptyList() }, loadLocalTags = { emptyList() }, applyMerged = {},
        ).engine
        val result = WholeFileWebDavSync.syncTargets(
            listOf(engine), { LedgerSyncPayload(entries = listOf(entry("local", 200))) },
            { applied = true }, 1000L,
        )
        assertEquals(WebDavFailure.FOLDER_MISSING,
            (result.single().exceptionOrNull() as WebDavException).failure)
        assertFalse(applied)
        assertEquals(listOf("remote"), LedgerBackup.parse(
            server.bodyOf(LedgerWebDavSync.LEDGER_FILE)!!).entries.map { it.uuid })
    }

    @Test
    fun duplicateDestinationsPublishOnceAndReportBothProfiles() = runBlocking {
        val local = LedgerSyncPayload(entries = listOf(entry("local", 100)))
        var applied = 0
        val engines = List(2) {
            LedgerWebDavSync(
                WebDavClient(server.folderUrl, "user", "pass"),
                loadLocal = { emptyList() }, loadLocalTags = { emptyList() }, applyMerged = {},
            ).engine
        }
        val results = WholeFileWebDavSync.syncTargets(engines, { local }, { applied++ }, 1000L)
        assertEquals(2, results.size)
        assertTrue(results.all { it.isSuccess })
        assertEquals(1, applied)
        assertEquals(1, server.requests("GET", LedgerWebDavSync.LEDGER_FILE))
        assertEquals(1, server.requests("PUT", LedgerWebDavSync.LEDGER_FILE))
    }

    @Test
    fun encryptedTotpTargetsConvergeWithoutDroppingLocalTokens() = runBlocking {
        val other = FakeWebDavServer(WebDavPaths.basicAuthHeader("user", "pass"))
        val password = "test-backup-passphrase"
        fun token(id: String) = TotpEntry(uuid = id, issuer = id, accountName = "account",
            secret = "JBSWY3DPEHPK3PXP", createdAtMillis = 100L)
        try {
            server.putRaw(TotpWebDavSync.VAULT_FILE,
                TotpVaultCrypto.encryptVault(listOf(token("a")), password, iterations = 1000))
            other.putRaw(TotpWebDavSync.VAULT_FILE,
                TotpVaultCrypto.encryptVault(listOf(token("b")), password, iterations = 1000))
            var local = listOf(token("local"))
            var applied = 0
            val engines = listOf(server, other).map { target ->
                TotpWebDavSync(WebDavClient(target.folderUrl, "user", "pass"), password,
                    loadLocal = { emptyList() }, applyMerged = {}, iterations = 1000).engine
            }
            val result = WholeFileWebDavSync.syncTargets(engines, { local }, {
                local = it
                applied++
            }, 1000L)
            assertTrue(result.all { it.isSuccess })
            assertEquals(1, applied)
            assertEquals(setOf("a", "b", "local"), local.map { it.uuid }.toSet())
            for (target in listOf(server, other)) {
                val body = target.bodyOf(TotpWebDavSync.VAULT_FILE)!!
                assertFalse(body.contains("JBSWY3DPEHPK3PXP"))
                assertEquals(setOf("a", "b", "local"),
                    TotpVaultCrypto.decryptVault(body, password).map { it.uuid }.toSet())
                assertEquals(1, target.requests("GET", TotpWebDavSync.VAULT_FILE))
            }
        } finally {
            other.stop()
        }
    }

    @Test
    fun testSettingsBackupIsEncryptedVersionedAndWrongPasswordDoesNotOverwrite() {
        val client = WebDavClient(server.folderUrl + "Toolbox/settings/", "user", "pass",
            parentFolderUrl = server.folderUrl + "Toolbox/")
        val pass = "settings-test-password"
        val first = AppSettingsBackup.backup(client, JSONObject().put("language", "CHINESE"), pass)
        val main = "Toolbox/settings/settings.json"
        val before = server.bodyOf(main)!!
        assertFalse(before.contains("CHINESE"))
        assertEquals("CHINESE", AppSettingsBackup.decode(before, pass).getString("language"))
        assertThrows(TotpVaultException::class.java) {
            AppSettingsBackup.backup(client, JSONObject().put("language", "ENGLISH"), "different-password")
        }
        assertEquals(before, server.bodyOf(main))
        val second = AppSettingsBackup.backup(client, JSONObject().put("language", "ENGLISH"), pass)
        assertFalse(first.snapshotName == second.snapshotName)
        assertEquals(2, WebDavHistory(client, BackupTool.SETTINGS).list().size)
        assertEquals("CHINESE", AppSettingsBackup.decode(
            WebDavHistory(client, BackupTool.SETTINGS).read(first.snapshotName!!), pass).getString("language"))
    }

    @Test
    fun testSettingsConcurrentChangeIsNotOverwritten() {
        val client = WebDavClient(server.folderUrl, "user", "pass")
        val pass = "settings-test-password"
        AppSettingsBackup.backup(client, JSONObject().put("language", "CHINESE"), pass)
        val before = server.bodyOf("settings.json")!!
        server.bumpEtagAfterNextGet = true
        assertSyncFails(WebDavFailure.PRECONDITION_FAILED) {
            AppSettingsBackup.backup(client, JSONObject().put("language", "ENGLISH"), pass)
        }
        assertEquals(before, server.bodyOf("settings.json"))
    }

    @Test
    fun testSameTimestampSnapshotsRemainSeparate() {
        val local = mutableListOf(entry("a", 100L))
        val sync = newSync(local)
        val first = sync.sync(1_700_000_000_000L).snapshotName!!
        local.add(entry("b", 200L))
        val second = sync.sync(1_700_000_000_000L).snapshotName!!
        assertFalse(first == second)
        assertEquals(1, LedgerBackup.parse(server.bodyOf(first)!!).entries.size)
        assertEquals(2, LedgerBackup.parse(server.bodyOf(second)!!).entries.size)
        assertEquals(setOf(first, second), WebDavHistory(
            WebDavClient(server.folderUrl, "user", "pass"), BackupTool.LEDGER).list().toSet())
    }

    @Test
    fun testSnapshotIndexConflictIsRetriedWithoutLosingVersion() {
        val client = WebDavClient(server.folderUrl, "user", "pass")
        val history = WebDavHistory(client, BackupTool.LEDGER)
        val first = history.save("{}", 1_700_000_000_000L, ZoneId.of("UTC"))
        server.bumpEtagAfterNextGet = true
        val second = history.save("{}", 1_700_000_000_001L, ZoneId.of("UTC"))
        assertEquals(setOf(first, second), history.list().toSet())
    }

    @Test
    fun testEncryptedSyncMigratesPlaintextAndKeepsEncryptedSnapshots() {
        val plain = LedgerSyncSerializer.toJsonString(LedgerSyncPayload(entries = listOf(entry("a", 100L))))
        server.putRaw(LedgerWebDavSync.LEDGER_FILE, plain)
        val local = mutableListOf<LedgerEntry>()
        val vault = LedgerBackupVault("test-passphrase")
        val result = LedgerWebDavSync(
            client = WebDavClient(server.folderUrl, "user", "pass"),
            loadLocal = { local.toList() }, loadLocalTags = { emptyList() },
            applyMerged = { local.clear(); local.addAll(it.entries) }, vault = vault,
        ).sync()
        val remote = server.bodyOf(LedgerWebDavSync.LEDGER_FILE)!!
        assertTrue(remote.contains(LedgerBackupVault.FORMAT))
        assertEquals(listOf("a"), LedgerBackup.parse(vault.decode(remote)).entries.map { it.uuid })
        assertEquals(remote, server.bodyOf(result.snapshotName!!))
    }

    @Test
    fun testPullAllBeforePublishingConvergesMultipleTargets() {
        val second = FakeWebDavServer(WebDavPaths.basicAuthHeader("user", "pass"))
        try {
            val local = mutableListOf<LedgerEntry>()
            server.putRaw(LedgerWebDavSync.LEDGER_FILE,
                LedgerSyncSerializer.toJsonString(LedgerSyncPayload(entries = listOf(entry("a", 100L)))))
            second.putRaw(LedgerWebDavSync.LEDGER_FILE,
                LedgerSyncSerializer.toJsonString(LedgerSyncPayload(entries = listOf(entry("b", 200L)))))
            val engines = listOf(server, second).map { target -> LedgerWebDavSync(
                client = WebDavClient(target.folderUrl, "user", "pass"),
                loadLocal = { local.toList() }, loadLocalTags = { emptyList() },
                applyMerged = { payload ->
                    val merged = LedgerSyncMerge.merge(local, payload.entries).entries
                    local.clear(); local.addAll(merged)
                },
            ) }
            engines.forEach { engine ->
                val pulled = engine.pull()!!
                val merged = LedgerSyncMerge.merge(local, pulled.entries).entries
                local.clear(); local.addAll(merged)
            }
            engines.forEach { it.sync() }
            listOf(server, second).forEach { target ->
                assertEquals(setOf("a", "b"), LedgerBackup.parse(
                    target.bodyOf(LedgerWebDavSync.LEDGER_FILE)!!).entries.map { it.uuid }.toSet())
            }
        } finally { second.stop() }
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
    fun testFirstToolboxSyncReadsLegacyFileInTheParentFolder() {
        server.putRaw(
            LedgerWebDavSync.LEDGER_FILE,
            LedgerSyncSerializer.toJsonString(
                LedgerSyncPayload(entries = listOf(entry("legacy", 100L))),
            ),
        )
        val local = mutableListOf<LedgerEntry>()
        val result = LedgerWebDavSync(
            client = WebDavClient(
                WebDavPaths.toolboxFolderUrl(server.folderUrl),
                "user",
                "pass",
            ),
            legacyClient = WebDavClient(server.folderUrl, "user", "pass"),
            loadLocal = { local.toList() },
            loadLocalTags = { emptyList() },
            applyMerged = { merged ->
                local.clear()
                local.addAll(merged.entries)
            },
            zone = ZoneId.of("UTC"),
        ).sync(nowMillis = 1_700_000_000_000L)

        assertEquals(1, result.pulledCount)
        assertEquals(listOf("legacy"), local.map { it.uuid })
        assertNotNull(server.bodyOf("Toolbox/${LedgerWebDavSync.LEDGER_FILE}"))
        assertNotNull(server.bodyOf("Toolbox/${result.snapshotName}"))
        // The legacy file remains available as a fallback.
        assertNotNull(server.bodyOf(LedgerWebDavSync.LEDGER_FILE))
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
    fun testFirstSyncRetriesWhenAnotherDeviceCreatesRemoteFile() {
        val remoteBody = LedgerSyncSerializer.toJsonString(
            LedgerSyncPayload(entries = listOf(entry("remote", 200L))),
        )
        server.createLedgerAfterNextMissingGet = remoteBody
        val local = mutableListOf(entry("local", 100L))

        val result = newSync(local).sync(nowMillis = 1_700_000_000_000L)

        assertEquals(2, result.totalEntries)
        assertEquals(setOf("local", "remote"), local.map { it.uuid }.toSet())
        val remote = LedgerSyncSerializer.fromJsonString(
            server.bodyOf(LedgerWebDavSync.LEDGER_FILE)!!,
        )
        assertEquals(setOf("local", "remote"), remote.entries.map { it.uuid }.toSet())
    }

    @Test
    fun testMissingStrongEtagStopsBeforeOverwritingRemote() {
        val remoteBody = LedgerSyncSerializer.toJsonString(
            LedgerSyncPayload(entries = listOf(entry("remote", 200L))),
        )
        server.putRaw(LedgerWebDavSync.LEDGER_FILE, remoteBody)
        server.omitEtagOnGet = true
        val local = mutableListOf(entry("local", 100L))

        assertSyncFails(WebDavFailure.VERSION_UNAVAILABLE) {
            newSync(local).sync()
        }
        assertEquals(remoteBody, server.bodyOf(LedgerWebDavSync.LEDGER_FILE))
        assertEquals(listOf("local"), local.map { it.uuid })
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
