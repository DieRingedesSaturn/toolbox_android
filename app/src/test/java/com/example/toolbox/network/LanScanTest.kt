package com.example.toolbox.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanScanTest {

    @Test
    fun testSubnetHostsSlash30() {
        val hosts = LanScan.subnetHosts("192.168.1.5", 30)
        assertEquals(listOf("192.168.1.5", "192.168.1.6"), hosts)
    }

    @Test
    fun testSubnetHostsSlash24() {
        val hosts = LanScan.subnetHosts("10.0.0.42", 24)!!
        assertEquals(254, hosts.size)
        assertEquals("10.0.0.1", hosts.first())
        assertEquals("10.0.0.254", hosts.last())
        assertFalse(hosts.contains("10.0.0.255"))
    }

    @Test
    fun testSubnetHostsHugeSubnetNarrowsToSlash24() {
        val hosts = LanScan.subnetHosts("172.16.5.200", 16)!!
        assertEquals(254, hosts.size)
        assertTrue(hosts.all { it.startsWith("172.16.5.") })
    }

    @Test
    fun testSubnetHostsRejectsBadInput() {
        assertNull(LanScan.subnetHosts("not-an-ip", 24))
        assertNull(LanScan.subnetHosts("192.168.1.5", 31))
        assertNull(LanScan.subnetHosts("192.168.1.5", 4))
        assertNull(LanScan.subnetHosts("fe80::1", 24))
    }

    @Test
    fun testVendorLookup() {
        assertEquals("Hikvision", LanScan.cameraVendorForMac("44:19:b6:11:22:33"))
        assertEquals("Xiaomi", LanScan.cameraVendorForMac("8C-BE-BE-11-22-33"))
        assertNull(LanScan.cameraVendorForMac("FF:FF:FF:11:22:33"))
        assertNull(LanScan.cameraVendorForMac(null))
        assertNull(LanScan.cameraVendorForMac("bad"))
    }

    @Test
    fun testSuspicionLevels() {
        val onvifCam = LanDevice(
            ip = "192.168.1.9",
            services = listOf(
                LanService(
                    LanServiceSource.ONVIF_WS_DISCOVERY,
                    "dn:NetworkVideoTransmitter",
                    null,
                    null,
                ),
            ),
        )
        assertEquals(LanSuspicion.LIKELY_CAMERA, LanScan.suspicion(onvifCam))

        val rtspCam = LanDevice("192.168.1.9", openPorts = listOf(554, 80))
        assertEquals(LanSuspicion.LIKELY_CAMERA, LanScan.suspicion(rtspCam))
        assertTrue(LanScan.flagsFor(rtspCam).contains(LanFlag.RTSP_PORT))

        val xiongMai = LanDevice("192.168.1.9", openPorts = listOf(34567))
        assertEquals(LanSuspicion.LIKELY_CAMERA, LanScan.suspicion(xiongMai))

        val vendorNic = LanDevice("192.168.1.9", mac = "AC:CC:8E:01:02:03")
        assertEquals(LanSuspicion.NOTEWORTHY, LanScan.suspicion(vendorNic))

        val devBox = LanDevice("192.168.1.9", openPorts = listOf(22, 8080))
        assertEquals(LanSuspicion.NONE, LanScan.suspicion(devBox))
        assertTrue(LanScan.flagsFor(devBox).contains(LanFlag.WEB_ADMIN_ONLY))

        val quiet = LanDevice("192.168.1.9")
        assertEquals(LanSuspicion.NONE, LanScan.suspicion(quiet))
        assertTrue(LanScan.flagsFor(quiet).isEmpty())
    }

    @Test
    fun testParseSsdpResponse() {
        val response = "HTTP/1.1 200 OK\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "ST: upnp:rootdevice\r\n" +
            "SERVER: Linux/4.4 UPnP/1.1 IPCamera/2.0\r\n" +
            "LOCATION: http://192.168.1.66:80/device.xml\r\n\r\n"
        val (st, server) = LanScan.parseSsdpResponse(response)
        assertEquals("upnp:rootdevice", st)
        assertEquals("Linux/4.4 UPnP/1.1 IPCamera/2.0", server)
    }

    @Test
    fun testParseSsdpResponseEmpty() {
        val (st, server) = LanScan.parseSsdpResponse("garbage")
        assertNull(st)
        assertNull(server)
    }

    @Test
    fun testParseWsDiscoveryMatch() {
        val xml = """<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
            <s:Body><d:ProbeMatches xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery">
            <d:ProbeMatch><d:Types>dn:NetworkVideoTransmitter ds:Device</d:Types>
            <d:XAddrs>http://192.168.1.88/onvif/device_service http://[fe80::1]/x</d:XAddrs>
            </d:ProbeMatch></d:ProbeMatches></s:Body></s:Envelope>"""
        val (types, hosts) = LanScan.parseWsDiscoveryMatch(xml)
        assertTrue(types.any { it.contains("NetworkVideoTransmitter") })
        assertTrue(hosts.contains("192.168.1.88"))
        assertTrue(hosts.contains("fe80::1"))
    }

    @Test
    fun testParseWsDiscoveryMatchNoMatch() {
        val (types, hosts) = LanScan.parseWsDiscoveryMatch("<html>not a probe</html>")
        assertTrue(types.isEmpty())
        assertTrue(hosts.isEmpty())
    }
}
