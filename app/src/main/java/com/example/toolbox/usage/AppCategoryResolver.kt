package com.example.toolbox.usage

import android.content.pm.ApplicationInfo
import android.os.Build

object AppCategoryResolver {

    private val PACKAGE_CATEGORY_RULES: List<Pair<Regex, AppCategory>> = listOf(
        // Games
        Regex("^(com\\.miHoYo\\.|com\\.mihoyo\\.|com\\.hypergryph\\.|com\\.tencent\\.tmgp|com\\.kurogame\\.|com\\.proximabeta\\.|com\\.riotgames\\.|com\\.epicgames\\.|com\\.supercell\\.|com\\.mojang\\.|com\\.roblox\\.|com\\.ea\\.gp|com\\.activision\\.).*") to AppCategory.GAME,
        // Video & Short Video
        Regex("^(tv\\.danmaku\\.bili|com\\.ss\\.android\\.ugc\\.aweme|com\\.tencent\\.qqlive|com\\.youku\\.phone|com\\.qiyi\\.video|com\\.hunantv\\.imgo|com\\.google\\.android\\.youtube|com\\.netflix\\.mediaclient|com\\.kuaishou|com\\.smile\\.gifmaker|com\\.bilibili).*") to AppCategory.VIDEO,
        // Audio & Music & Podcasts
        Regex("^(com\\.netease\\.cloudmusic|com\\.tencent\\.qqmusic|com\\.kugou\\.android|com\\.kuwo\\.player|com\\.spotify\\.music|com\\.apple\\.android\\.music|fm\\.ximalaya|com\\.kugou).*") to AppCategory.AUDIO,
        // Social & Community & Instant Messaging
        Regex("^(com\\.tencent\\.mm|com\\.tencent\\.mobileqq|com\\.tencent\\.tim|com\\.sina\\.weibo|org\\.telegram\\.messenger|com\\.whatsapp|com\\.facebook|com\\.instagram\\.android|com\\.twitter\\.android|com\\.zhihu\\.android|com\\.coolapk\\.market|com\\.xingin\\.xhs|com\\.douban\\.frodo|com\\.discord).*") to AppCategory.SOCIAL,
        // Shopping & Lifestyle & Food
        Regex("^(com\\.taobao\\.taobao|com\\.jingdong\\.app\\.mall|com\\.sankuai\\.meituan|com\\.dianping\\.v1|com\\.xunmeng\\.pinduoduo|com\\.alibaba\\.wireless|me\\.ele|com\\.achievo\\.vipshop).*") to AppCategory.SHOPPING,
        // Travel & Maps & Navigation
        Regex("^(com\\.autonavi\\.minimap|com\\.baidu\\.BaiduMap|com\\.sdu\\.didi\\.psnger|com\\.tencent\\.map|com\\.google\\.android\\.apps\\.maps|ctrip\\.android\\.view|com\\.Qunar).*") to AppCategory.TRAVEL,
        // Productivity & Enterprise & Office
        Regex("^(cn\\.wps\\.moffice|com\\.microsoft\\.office|com\\.alibaba\\.android\\.rimet|com\\.tencent\\.wework|com\\.feishu|com\\.ss\\.android\\.lark|com\\.google\\.android\\.apps\\.docs|com\\.google\\.android\\.apps\\.sheets|com\\.notion).*") to AppCategory.PRODUCTIVITY,
        // News & Reading
        Regex("^(com\\.ss\\.android\\.article\\.news|com\\.tencent\\.news|com\\.netease\\.newsreader|com\\.sina\\.news|com\\.dragon\\.read|com\\.qq\\.reader).*") to AppCategory.NEWS,
        // Tools & System Utilities
        Regex("^(com\\.android\\.chrome|org\\.mozilla\\.firefox|com\\.quark\\.browser|bin\\.mt\\.plus|com\\.estrongs\\.android|com\\.example\\.toolbox).*") to AppCategory.TOOL,
    )

    fun resolve(appInfo: ApplicationInfo): AppCategory {
        val pkg = appInfo.packageName

        // 1. Match package name rules dictionary
        for ((regex, category) in PACKAGE_CATEGORY_RULES) {
            if (regex.containsMatchIn(pkg)) {
                return category
            }
        }

        // 2. Check Game flag (Available on all Android versions)
        @Suppress("DEPRECATION")
        if ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0) {
            return AppCategory.GAME
        }

        // 3. Check Android Native App Category (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAME
                ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.AUDIO
                ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.VIDEO
                ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
                ApplicationInfo.CATEGORY_NEWS -> return AppCategory.NEWS
                ApplicationInfo.CATEGORY_MAPS -> return AppCategory.TRAVEL
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.PRODUCTIVITY
                ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.VIDEO
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> return AppCategory.TOOL
            }
        }

        // 4. Default classification
        return if (isSystemApp(appInfo)) AppCategory.TOOL else AppCategory.OTHER
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }
}
