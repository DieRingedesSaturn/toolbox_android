package com.example.toolbox.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
