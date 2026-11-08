package com.example.toolbox.ledger

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
