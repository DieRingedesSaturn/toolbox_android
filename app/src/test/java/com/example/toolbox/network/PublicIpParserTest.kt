package com.example.toolbox.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicIpParserTest {

    @Test
    fun parsesIpWhoIsFullResponse() {
        val json = """
            {
              "ip": "203.0.113.195",
              "success": true,
              "country": "China",
              "region": "Guangdong",
              "city": "Shenzhen",
              "connection": {
                "isp": "China Telecom",
                "org": "Chinanet"
              }
            }
        """.trimIndent()

        val details = NetworkInfoReader.parseIpWhoIs(json)
        assertNotNull(details)
        assertEquals("203.0.113.195", details?.ip)
        assertEquals("China", details?.country)
        assertEquals("Guangdong", details?.region)
        assertEquals("Shenzhen", details?.city)
        assertEquals("China Telecom", details?.isp)
        assertEquals("China, Guangdong, Shenzhen", details?.formatLocation())
    }

    @Test
    fun parsesIpWhoIsIpv6Response() {
        val json = """
            {
              "ip": "240e:390:800:100::1",
              "success": true,
              "country": "China",
              "region": "Beijing",
              "city": "Beijing",
              "connection": {
                "isp": "China Telecom"
              }
            }
        """.trimIndent()

        val details = NetworkInfoReader.parseIpWhoIs(json)
        assertNotNull(details)
        assertEquals("240e:390:800:100::1", details?.ip)
        assertEquals("China, Beijing", details?.formatLocation())
    }

    @Test
    fun returnsNullWhenIpWhoIsReportsFailure() {
        val json = """
            {
              "success": false,
              "message": "invalid IP address"
            }
        """.trimIndent()

        assertNull(NetworkInfoReader.parseIpWhoIs(json))
    }

    @Test
    fun parsesIpApiCoResponse() {
        val json = """
            {
              "ip": "8.8.8.8",
              "city": "Mountain View",
              "region": "California",
              "country_name": "United States",
              "org": "Google LLC",
              "asn": "AS15169"
            }
        """.trimIndent()

        val details = NetworkInfoReader.parseIpApiCo(json)
        assertNotNull(details)
        assertEquals("8.8.8.8", details?.ip)
        assertEquals("United States", details?.country)
        assertEquals("California", details?.region)
        assertEquals("Mountain View", details?.city)
        assertEquals("Google LLC", details?.isp)
        assertEquals("United States, California, Mountain View", details?.formatLocation())
    }

    @Test
    fun returnsNullWhenIpApiCoReportsError() {
        val json = """
            {
              "error": true,
              "reason": "Rate limited"
            }
        """.trimIndent()

        assertNull(NetworkInfoReader.parseIpApiCo(json))
    }

    @Test
    fun parsesPlainIpString() {
        val ipv4 = NetworkInfoReader.parsePlainIp(" 192.0.2.1 \n")
        assertNotNull(ipv4)
        assertEquals("192.0.2.1", ipv4?.ip)
        assertNull(ipv4?.country)

        val invalid = NetworkInfoReader.parsePlainIp("not-an-ip")
        assertNull(invalid)
    }

    @Test
    fun formatLocationHandlesMissingOrDuplicateParts() {
        val full = PublicIpDetails(ip = "1.1.1.1", country = "Japan", region = "Tokyo", city = "Tokyo")
        assertEquals("Japan, Tokyo", full.formatLocation())

        val countryOnly = PublicIpDetails(ip = "1.1.1.1", country = "Singapore")
        assertEquals("Singapore", countryOnly.formatLocation())

        val none = PublicIpDetails(ip = "1.1.1.1")
        assertNull(none.formatLocation())
    }

    @Test
    fun isIpAddressValidatesIpv4AndIpv6() {
        assertTrue(NetworkInfoReader.isIpAddress("1.1.1.1"))
        assertTrue(NetworkInfoReader.isIpAddress("255.255.255.255"))
        assertTrue(NetworkInfoReader.isIpAddress("2001:db8::1"))
        assertTrue(NetworkInfoReader.isIpAddress("::1"))

        assertFalse(NetworkInfoReader.isIpAddress("256.1.1.1"))
        assertFalse(NetworkInfoReader.isIpAddress("1.1.1"))
        assertFalse(NetworkInfoReader.isIpAddress("example.com"))
        assertFalse(NetworkInfoReader.isIpAddress(""))
    }
}
