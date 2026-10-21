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
              "type": "IPv4",
              "continent": "Asia",
              "country": "China",
              "country_code": "CN",
              "region": "Guangdong",
              "city": "Shenzhen",
              "postal": "518000",
              "connection": {
                "asn": 4134,
                "isp": "China Telecom",
                "org": "Chinanet",
                "domain": "chinatelecom.com.cn"
              },
              "timezone": {
                "id": "Asia/Shanghai",
                "utc": "+08:00"
              }
            }
        """.trimIndent()

        val details = NetworkInfoReader.parseIpWhoIs(json)
        assertNotNull(details)
        assertEquals("203.0.113.195", details?.ip)
        assertEquals("China", details?.country)
        assertEquals("CN", details?.countryCode)
        assertEquals("Guangdong", details?.region)
        assertEquals("Shenzhen", details?.city)
        assertEquals("518000", details?.postal)
        assertEquals("Asia", details?.continent)
        assertEquals("IPv4", details?.ipType)
        assertEquals("China Telecom", details?.isp)
        assertEquals("Chinanet", details?.organization)
        assertEquals("AS4134", details?.asn)
        assertEquals("chinatelecom.com.cn", details?.domain)
        assertEquals("Asia/Shanghai (UTC+08:00)", details?.timezone)
        assertEquals("ipwho.is", details?.source)
        assertNull(details?.proxy)
        assertNull(details?.risk)
        assertEquals("China, Guangdong, Shenzhen", details?.formatLocation())
    }

    @Test
    fun parsesIpWhoIsIpv6Response() {
        val json = """
            {
              "ip": "240e:390:800:100::1",
              "success": true,
              "type": "IPv6",
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
        assertEquals("IPv6", details?.ipType)
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
              "version": "IPv4",
              "city": "Mountain View",
              "region": "California",
              "country_name": "United States",
              "country_code": "US",
              "postal": "94043",
              "timezone": "America/Los_Angeles",
              "utc_offset": "-07:00",
              "network": "8.8.8.0/24",
              "org": "Google LLC",
              "asn": "AS15169"
            }
        """.trimIndent()

        val details = NetworkInfoReader.parseIpApiCo(json)
        assertNotNull(details)
        assertEquals("8.8.8.8", details?.ip)
        assertEquals("United States", details?.country)
        assertEquals("US", details?.countryCode)
        assertEquals("California", details?.region)
        assertEquals("Mountain View", details?.city)
        assertEquals("94043", details?.postal)
        assertEquals("IPv4", details?.ipType)
        assertEquals("America/Los_Angeles (UTC-07:00)", details?.timezone)
        assertEquals("8.8.8.0/24", details?.ipRange)
        assertEquals("Google LLC", details?.isp)
        assertEquals("AS15169", details?.asn)
        assertEquals("ipapi.co", details?.source)
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
    fun parsesProxyCheckFullResponse() {
        val json = """
            {
              "status": "ok",
              "203.0.113.195": {
                "asn": "AS64500",
                "range": "203.0.113.0/24",
                "hostname": "host.example.net",
                "provider": "Example Provider",
                "organisation": "Example Org",
                "continent": "Asia",
                "country": "Japan",
                "isocode": "JP",
                "region": "Tokyo",
                "timezone": "Asia/Tokyo",
                "city": "Tokyo",
                "postcode": "100-0001",
                "latitude": 35.68,
                "longitude": 139.69,
                "proxy": "yes",
                "type": "VPN",
                "risk": 66
              },
              "query_time": 4
            }
        """.trimIndent()

        val details = NetworkInfoReader.parseProxyCheck(json)
        assertNotNull(details)
        assertEquals("203.0.113.195", details?.ip)
        assertEquals("Japan", details?.country)
        assertEquals("JP", details?.countryCode)
        assertEquals("Tokyo", details?.region)
        assertEquals("Tokyo", details?.city)
        assertEquals("100-0001", details?.postal)
        assertEquals("Asia", details?.continent)
        assertEquals("Asia/Tokyo", details?.timezone)
        assertEquals("IPv4", details?.ipType)
        assertEquals("Example Provider", details?.isp)
        assertEquals("Example Org", details?.organization)
        assertEquals("AS64500", details?.asn)
        assertEquals("host.example.net", details?.hostname)
        assertEquals("203.0.113.0/24", details?.ipRange)
        assertEquals("VPN", details?.networkType)
        assertEquals(true, details?.proxy)
        assertEquals(66, details?.risk)
        assertEquals(PublicIpRiskLevel.MEDIUM, details?.riskLevel())
        assertEquals("proxycheck.io", details?.source)
        assertEquals("Japan, Tokyo", details?.formatLocation())
    }

    @Test
    fun parsesProxyCheckCleanAddress() {
        val json = """
            {
              "status": "ok",
              "8.8.8.8": {
                "proxy": "no",
                "type": "Business",
                "risk": 0
              },
              "query_time": 3
            }
        """.trimIndent()

        val details = NetworkInfoReader.parseProxyCheck(json)
        assertNotNull(details)
        assertEquals(false, details?.proxy)
        assertEquals("Business", details?.networkType)
        assertEquals(0, details?.risk)
        assertEquals(PublicIpRiskLevel.LOW, details?.riskLevel())
    }

    @Test
    fun returnsNullWhenProxyCheckHasNoAddressResult() {
        val json = """
            {
              "status": "error",
              "message": "No valid IP Addresses supplied."
            }
        """.trimIndent()

        assertNull(NetworkInfoReader.parseProxyCheck(json))
    }

    @Test
    fun parsesPlainIpString() {
        val ipv4 = NetworkInfoReader.parsePlainIp(" 192.0.2.1 \n")
        assertNotNull(ipv4)
        assertEquals("192.0.2.1", ipv4?.ip)
        assertEquals("IPv4", ipv4?.ipType)
        assertEquals("ipify", ipv4?.source)
        assertNull(ipv4?.country)

        val ipv6 = NetworkInfoReader.parsePlainIp("2001:db8::1")
        assertEquals("IPv6", ipv6?.ipType)

        val invalid = NetworkInfoReader.parsePlainIp("not-an-ip")
        assertNull(invalid)
    }

    @Test
    fun riskLevelUsesProxyCheckBands() {
        assertEquals(PublicIpRiskLevel.LOW, PublicIpDetails(ip = "1.1.1.1", risk = 0).riskLevel())
        assertEquals(PublicIpRiskLevel.LOW, PublicIpDetails(ip = "1.1.1.1", risk = 33).riskLevel())
        assertEquals(PublicIpRiskLevel.MEDIUM, PublicIpDetails(ip = "1.1.1.1", risk = 34).riskLevel())
        assertEquals(PublicIpRiskLevel.MEDIUM, PublicIpDetails(ip = "1.1.1.1", risk = 66).riskLevel())
        assertEquals(PublicIpRiskLevel.HIGH, PublicIpDetails(ip = "1.1.1.1", risk = 67).riskLevel())
        assertEquals(PublicIpRiskLevel.HIGH, PublicIpDetails(ip = "1.1.1.1", risk = 100).riskLevel())
        assertNull(PublicIpDetails(ip = "1.1.1.1").riskLevel())
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
