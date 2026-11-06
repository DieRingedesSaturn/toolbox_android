package com.example.toolbox.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PublicIpLocationTest {
    @Test
    fun parsesApproximateCoordinatesAndSourceWithoutGps() {
        val result = NetworkInfoReader.parseIpLocation(
            """{"success":true,"ip":"203.0.113.5","latitude":30.5,"longitude":114.2,"city":"Example"}""",
            "ipwho.is",
        )
        assertNotNull(result)
        assertEquals(30.5, result!!.latitude, 0.0)
        assertEquals(114.2, result.longitude, 0.0)
        assertEquals("Example", result.place)
        assertEquals("ipwho.is", result.source)
    }

    @Test
    fun acceptsEquatorAndNumericStringsFromFallback() {
        val result = NetworkInfoReader.parseIpLocation(
            """{"ip":"2001:db8::1","latitude":"0","longitude":"0"}""", "ipapi.co",
        )
        assertEquals(0.0, result!!.latitude, 0.0)
        assertEquals(0.0, result.longitude, 0.0)
        assertEquals("ipapi.co", result.source)
    }

    @Test
    fun neverTurnsAbsentOrInvalidCoordinatesIntoZero() {
        listOf(
            "", ",\"latitude\":null,\"longitude\":114", ",\"latitude\":30",
            ",\"latitude\":true,\"longitude\":114", ",\"latitude\":91,\"longitude\":114",
            ",\"latitude\":30,\"longitude\":181", ",\"latitude\":\"NaN\",\"longitude\":114",
            ",\"latitude\":30,\"longitude\":\"Infinity\"",
        ).forEach { fields ->
            assertNull(fields, NetworkInfoReader.parseIpLocation("{\"ip\":\"203.0.113.5\"$fields}", "ipwho.is"))
        }
    }

    @Test
    fun rejectsFailedResponsesAndUnknownSources() {
        val success = """{"ip":"203.0.113.5","latitude":30,"longitude":114}"""
        assertNull(NetworkInfoReader.parseIpLocation(success, "unknown"))
        assertNull(NetworkInfoReader.parseIpLocation(success.dropLast(1) + ",\"success\":false}", "ipwho.is"))
        assertNull(NetworkInfoReader.parseIpLocation(success.dropLast(1) + ",\"error\":true}", "ipapi.co"))
    }
}
