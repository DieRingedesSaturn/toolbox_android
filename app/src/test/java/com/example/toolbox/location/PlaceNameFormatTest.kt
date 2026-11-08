package com.example.toolbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceNameFormatTest {
    @Test
    fun joinsDistinctAddressParts() {
        assertEquals(
            "珞喻路 1037 · 洪山区 · 武汉市 · 湖北省 · 中国",
            formatPlaceName(
                featureName = "珞喻路",
                thoroughfare = "珞喻路",
                subThoroughfare = "1037",
                subLocality = "洪山区",
                locality = "武汉市",
                subAdminArea = null,
                adminArea = "湖北省",
                countryName = "中国",
            ),
        )
    }

    @Test
    fun dropsPlusCodesAndDuplicateFeatureNames() {
        assertEquals(
            "Market St · San Francisco · California · United States",
            formatPlaceName(
                featureName = "7MM4+X6",
                thoroughfare = "Market St",
                subThoroughfare = null,
                subLocality = null,
                locality = "San Francisco",
                subAdminArea = null,
                adminArea = "California",
                countryName = "United States",
            ),
        )
    }

    @Test
    fun returnsNullWhenEveryFieldIsBlank() {
        assertNull(
            formatPlaceName(
                featureName = "  ",
                thoroughfare = null,
                subThoroughfare = "",
                subLocality = "null",
                locality = null,
                subAdminArea = null,
                adminArea = null,
                countryName = null,
            ),
        )
    }
}
