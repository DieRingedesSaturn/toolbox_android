package com.example.toolbox.location

import android.location.GnssStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssStatusDataTest {

    @Test
    fun mapsConstellationTypesCorrectly() {
        assertEquals(GnssConstellation.GPS, LocationInfoReader.mapConstellationType(GnssStatus.CONSTELLATION_GPS))
        assertEquals(GnssConstellation.BEIDOU, LocationInfoReader.mapConstellationType(GnssStatus.CONSTELLATION_BEIDOU))
        assertEquals(GnssConstellation.GLONASS, LocationInfoReader.mapConstellationType(GnssStatus.CONSTELLATION_GLONASS))
        assertEquals(GnssConstellation.GALILEO, LocationInfoReader.mapConstellationType(GnssStatus.CONSTELLATION_GALILEO))
        assertEquals(GnssConstellation.QZSS, LocationInfoReader.mapConstellationType(GnssStatus.CONSTELLATION_QZSS))
        assertEquals(GnssConstellation.NAVIC, LocationInfoReader.mapConstellationType(GnssStatus.CONSTELLATION_IRNSS))
        assertEquals(GnssConstellation.SBAS, LocationInfoReader.mapConstellationType(GnssStatus.CONSTELLATION_SBAS))
        assertEquals(GnssConstellation.UNKNOWN, LocationInfoReader.mapConstellationType(999))
    }

    @Test
    fun validatesConstellationLabelsAndColors() {
        assertEquals("BDS", GnssConstellation.BEIDOU.shortLabel())
        assertEquals("GPS", GnssConstellation.GPS.shortLabel())
        assertEquals("GLO", GnssConstellation.GLONASS.shortLabel())
        assertEquals("GAL", GnssConstellation.GALILEO.shortLabel())

        assertTrue(GnssConstellation.BEIDOU.displayName(true).contains("北斗"))
        assertTrue(GnssConstellation.GPS.displayName(true).contains("GPS"))
    }

    @Test
    fun sortsSatellitesByUsedInFixAndSignalStrength() {
        val sat1 = SatelliteInfo(
            svid = 1,
            constellation = GnssConstellation.GPS,
            cn0DbHz = 22f,
            elevationDegrees = 30f,
            azimuthDegrees = 45f,
            usedInFix = false,
        )
        val sat2 = SatelliteInfo(
            svid = 2,
            constellation = GnssConstellation.BEIDOU,
            cn0DbHz = 35f,
            elevationDegrees = 60f,
            azimuthDegrees = 120f,
            usedInFix = true,
        )
        val sat3 = SatelliteInfo(
            svid = 3,
            constellation = GnssConstellation.BEIDOU,
            cn0DbHz = 38f,
            elevationDegrees = 75f,
            azimuthDegrees = 180f,
            usedInFix = true,
        )

        val list = mutableListOf(sat1, sat2, sat3)
        list.sortWith(compareByDescending<SatelliteInfo> { it.usedInFix }.thenByDescending { it.cn0DbHz })

        assertEquals(3, list[0].svid) // usedInFix & 38 dB-Hz
        assertEquals(2, list[1].svid) // usedInFix & 35 dB-Hz
        assertEquals(1, list[2].svid) // not used & 22 dB-Hz
    }
}
