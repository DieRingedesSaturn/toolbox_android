package com.example.toolbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TerrainElevationParserTest {

    @Test
    fun parsesOpenMeteoResponseSuccessfully() {
        val json = """
            {
              "elevation": [128.5]
            }
        """.trimIndent()

        val info = LocationInfoReader.parseOpenMeteoElevation(json)
        assertNotNull(info)
        assertEquals(128.5, info?.elevationMeters ?: 0.0, 0.001)
        assertEquals("Open-Meteo DEM", info?.source)
    }

    @Test
    fun parsesOpenElevationResponseSuccessfully() {
        val json = """
            {
              "results": [
                {
                  "latitude": 22.543,
                  "longitude": 114.057,
                  "elevation": 42.0
                }
              ]
            }
        """.trimIndent()

        val info = LocationInfoReader.parseOpenElevation(json)
        assertNotNull(info)
        assertEquals(42.0, info?.elevationMeters ?: 0.0, 0.001)
        assertEquals("Open-Elevation DEM", info?.source)
    }

    @Test
    fun handlesEmptyOrInvalidResponses() {
        assertNull(LocationInfoReader.parseOpenMeteoElevation("""{"elevation":[]}"""))
        assertNull(LocationInfoReader.parseOpenElevation("""{"results":[]}"""))
    }
}
