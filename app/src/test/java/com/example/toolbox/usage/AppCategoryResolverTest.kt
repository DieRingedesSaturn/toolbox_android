package com.example.toolbox.usage

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoryResolverTest {

    @Test
    fun testPackageRuleMatchingForPopularApps() {
        val bilibili = ApplicationInfo().apply { packageName = "tv.danmaku.bili" }
        assertEquals(AppCategory.VIDEO, AppCategoryResolver.resolve(bilibili))

        val wechat = ApplicationInfo().apply { packageName = "com.tencent.mm" }
        assertEquals(AppCategory.SOCIAL, AppCategoryResolver.resolve(wechat))

        val cloudmusic = ApplicationInfo().apply { packageName = "com.netease.cloudmusic" }
        assertEquals(AppCategory.AUDIO, AppCategoryResolver.resolve(cloudmusic))

        val genshin = ApplicationInfo().apply { packageName = "com.miHoYo.Yuanshen" }
        assertEquals(AppCategory.GAME, AppCategoryResolver.resolve(genshin))

        val taobao = ApplicationInfo().apply { packageName = "com.taobao.taobao" }
        assertEquals(AppCategory.SHOPPING, AppCategoryResolver.resolve(taobao))

        val amap = ApplicationInfo().apply { packageName = "com.autonavi.minimap" }
        assertEquals(AppCategory.TRAVEL, AppCategoryResolver.resolve(amap))

        val wps = ApplicationInfo().apply { packageName = "cn.wps.moffice_eng" }
        assertEquals(AppCategory.PRODUCTIVITY, AppCategoryResolver.resolve(wps))
    }

    @Test
    fun testGameFlagFallback() {
        @Suppress("DEPRECATION")
        val customGame = ApplicationInfo().apply {
            packageName = "com.unknown.indie.game"
            flags = ApplicationInfo.FLAG_IS_GAME
        }
        assertEquals(AppCategory.GAME, AppCategoryResolver.resolve(customGame))
    }

    @Test
    fun testDefaultFallback() {
        val unknownApp = ApplicationInfo().apply {
            packageName = "com.random.user.app"
        }
        assertEquals(AppCategory.OTHER, AppCategoryResolver.resolve(unknownApp))

        val systemApp = ApplicationInfo().apply {
            packageName = "com.system.internal.service"
            flags = ApplicationInfo.FLAG_SYSTEM
        }
        assertEquals(AppCategory.TOOL, AppCategoryResolver.resolve(systemApp))
    }
}
