package com.example.toolbox.usage

enum class AppCategory(
    val emoji: String,
    val nameZh: String,
    val nameEn: String,
    val colorRgb: Long,
) {
    ALL("📱", "全部", "All", 0xFF607D8B),
    GAME("🎮", "游戏", "Game", 0xFFE91E63),
    VIDEO("🎬", "影视", "Video", 0xFFFF5722),
    AUDIO("🎵", "音乐", "Music", 0xFF9C27B0),
    SOCIAL("💬", "社交", "Social", 0xFF2196F3),
    NEWS("📰", "资讯", "News", 0xFF00BCD4),
    SHOPPING("🛍️", "购物", "Shopping", 0xFFFF9800),
    PRODUCTIVITY("💼", "办公", "Work", 0xFF4CAF50),
    TRAVEL("🧭", "出行", "Travel", 0xFF009688),
    TOOL("🛠️", "工具", "Tool", 0xFF795548),
    OTHER("📦", "其它", "Other", 0xFF9E9E9E),
}
