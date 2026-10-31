package com.example.toolbox.network

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BoundedHttpResponseTest {

    @Test
    fun testReadsBodyAtExactLimit() {
        val body = "hello".toByteArray()
        assertEquals(
            "hello",
            BoundedHttpResponse.readText(ByteArrayInputStream(body), body.size.toLong()),
        )
    }

    @Test
    fun testRejectsBodyAboveLimit() {
        val body = "too large".toByteArray()
        try {
            BoundedHttpResponse.readText(ByteArrayInputStream(body), 4L)
            fail("expected HttpResponseTooLargeException")
        } catch (_: HttpResponseTooLargeException) {
            // Expected.
        }
    }
}
