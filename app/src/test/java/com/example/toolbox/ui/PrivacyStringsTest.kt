package com.example.toolbox.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyStringsTest {
    @Test
    fun introDistinguishesNotFoundFromNotExported() {
        val english = ToolboxStrings(AppLanguage.ENGLISH).privacyIntro
        val chinese = ToolboxStrings(AppLanguage.CHINESE).privacyIntro
        assertTrue(english.contains("not found"))
        assertTrue(english.contains("not exported"))
        assertFalse(english.contains("Grey", ignoreCase = true))
        assertTrue(chinese.contains("未找到"))
        assertTrue(chinese.contains("未导出"))
        assertFalse(chinese.contains("灰色"))
    }
}
