package com.example.toolbox.ledger

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavClientTest {

    @Test
    fun testFailureForStatus() {
        assertNull(WebDavClient.failureForStatus(200, "GET"))
        assertNull(WebDavClient.failureForStatus(201, "PUT"))
        assertNull(WebDavClient.failureForStatus(204, "DELETE"))
        assertEquals(
            WebDavFailure.UNAUTHORIZED,
            WebDavClient.failureForStatus(401, "GET"),
        )
        assertEquals(
            WebDavFailure.UNAUTHORIZED,
            WebDavClient.failureForStatus(403, "PUT"),
        )
        assertEquals(
            WebDavFailure.PRECONDITION_FAILED,
            WebDavClient.failureForStatus(412, "PUT"),
        )
        assertEquals(
            WebDavFailure.FOLDER_MISSING,
            WebDavClient.failureForStatus(404, "PUT"),
        )
        assertEquals(
            WebDavFailure.FOLDER_MISSING,
            WebDavClient.failureForStatus(409, "PUT"),
        )
        assertEquals(
            WebDavFailure.HTTP_ERROR,
            WebDavClient.failureForStatus(404, "GET"),
        )
        assertEquals(
            WebDavFailure.HTTP_ERROR,
            WebDavClient.failureForStatus(500, "GET"),
        )
        assertEquals(
            WebDavFailure.HTTP_ERROR,
            WebDavClient.failureForStatus(404, "DELETE"),
        )
    }

    @Test
    fun testIfMatchValue() {
        assertNull(WebDavClient.ifMatchValue(null))
        assertNull(WebDavClient.ifMatchValue(""))
        assertNull(WebDavClient.ifMatchValue("   "))
        assertNull(WebDavClient.ifMatchValue("W/\"abc\""))
        assertNull(WebDavClient.ifMatchValue("w/\"abc\""))
        assertEquals("\"abc\"", WebDavClient.ifMatchValue("\"abc\""))
    }

    @Test
    fun createsTheFolderWithMkcol() {
        val request = AtomicReference<String>()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val worker = Thread {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                val head = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    head.append(line).append("\r\n")
                    if (line.isEmpty()) break
                }
                request.set(head.toString())
                socket.getOutputStream().write(
                    "HTTP/1.1 201 Created\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        .toByteArray(Charsets.ISO_8859_1),
                )
                socket.getOutputStream().flush()
            }
        }.apply { start() }
        try {
            val client = WebDavClient(
                "http://127.0.0.1:${server.localPort}/dav/Toolbox/",
                "user",
                "secret",
            )
            client.ensureFolder()
            assertTrue(request.get().startsWith("MKCOL /dav/Toolbox/ HTTP/1.1\r\n"))
            assertTrue(request.get().contains(WebDavPaths.basicAuthHeader("user", "secret")))
        } finally {
            server.close()
            worker.join(1_000)
        }
    }

    @Test
    fun createsParentBeforeToolFolderAndSkipsInformationalHeaders() {
        val requests = mutableListOf<String>()
        val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        val worker = Thread {
            repeat(2) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    requests.add(reader.readLine())
                    while (!reader.readLine().isNullOrEmpty()) { /* Consume request. */ }
                    socket.getOutputStream().write((
                        "HTTP/1.1 100 Continue\r\nX-Hint: ignored\r\n\r\n" +
                            "HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n"
                        ).toByteArray(Charsets.ISO_8859_1))
                }
            }
        }.apply { start() }
        try {
            val parent = "http://127.0.0.1:${server.localPort}/dav/Toolbox/"
            WebDavClient(parent + "ledger/", "user", "secret", parentFolderUrl = parent).ensureFolder()
            worker.join(1000)
            assertEquals(listOf("MKCOL /dav/Toolbox/ HTTP/1.1", "MKCOL /dav/Toolbox/ledger/ HTTP/1.1"), requests)
        } finally { server.close(); worker.join(1000) }
    }

    @Test
    fun rejectsUnboundedMkcolStatusLine() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val worker = Thread {
            server.accept().use { socket ->
                socket.getInputStream().bufferedReader().readLine()
                runCatching { socket.getOutputStream().write("X".repeat(70_000).toByteArray()) }
            }
        }.apply { start() }
        try {
            val client = WebDavClient("http://127.0.0.1:${server.localPort}/dav/", "user", "secret")
            assertEquals(WebDavFailure.NETWORK,
                assertThrows(WebDavException::class.java) { client.ensureFolder() }.failure)
        } finally { server.close(); worker.join(1000) }
    }

    @Test
    fun existingFolderMayAnswerMethodNotAllowed() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val worker = Thread {
            server.accept().use { socket ->
                socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    .readLine()
                socket.getOutputStream().write(
                    "HTTP/1.1 405 Method Not Allowed\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        .toByteArray(Charsets.ISO_8859_1),
                )
                socket.getOutputStream().flush()
            }
        }.apply { start() }
        try {
            WebDavClient(
                "http://127.0.0.1:${server.localPort}/dav/Toolbox/",
                "user",
                "secret",
            ).ensureFolder()
        } finally {
            server.close()
            worker.join(1_000)
        }
    }

    @Test
    fun rejectsRedirectsInsteadOfFollowingThem() {
        val hits = AtomicInteger()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port = server.localPort
        val worker = Thread {
            try {
                while (!server.isClosed) {
                    server.accept().use { socket ->
                        val first = hits.incrementAndGet() == 1
                        val response = if (first) {
                            "HTTP/1.1 302 Found\r\n" +
                                "Location: http://127.0.0.1:$port/redirected\r\n" +
                                "Content-Length: 0\r\nConnection: close\r\n\r\n"
                        } else {
                            "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        }
                        socket.getOutputStream().write(response.toByteArray(Charsets.US_ASCII))
                        socket.getOutputStream().flush()
                    }
                }
            } catch (_: IOException) {
                // Server closed by the test.
            }
        }.apply {
            isDaemon = true
            start()
        }
        try {
            val client = WebDavClient("http://127.0.0.1:$port/folder/", "user", "secret")
            val error = assertThrows(WebDavException::class.java) { client.get("ledger.json") }
            assertEquals(WebDavFailure.HTTP_ERROR, error.failure)
            assertEquals(302, error.httpCode)
            assertEquals(1, hits.get())
        } finally {
            server.close()
            worker.join(1_000)
        }
    }
}
