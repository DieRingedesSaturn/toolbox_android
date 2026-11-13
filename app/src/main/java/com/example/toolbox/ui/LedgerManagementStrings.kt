package com.example.toolbox.ui


fun ToolboxStrings.tagFilterCount(count: Int): String =
    if (count == 0) tagFilterAction else "$tagFilterAction · $count"

val ToolboxStrings.pendingSyncRecords: String
    get() = if (chinese) "待推送增量记录 (PENDING_PUSH)" else "Pending sync records (PENDING_PUSH)"

val ToolboxStrings.manageTagsMenu: String
    get() = if (chinese) "管理标签" else "Manage tags"

val ToolboxStrings.tagFilterAction: String
    get() = if (chinese) "筛选" else "Filter"

val ToolboxStrings.filteredBadge: String
    get() = if (chinese) "已筛选" else "Filtered"

val ToolboxStrings.tagNameLabel: String
    get() = if (chinese) "标签名" else "Tag name"

val ToolboxStrings.tagNameRequired: String
    get() = if (chinese) "请输入标签名" else "Enter a tag name"

val ToolboxStrings.tagNameDuplicate: String
    get() = if (chinese) "已有同名标签" else "A tag with this name already exists"

val ToolboxStrings.tagColorLabel: String
    get() = if (chinese) "颜色" else "Color"

val ToolboxStrings.tagEmojiLabel: String
    get() = if (chinese) "表情（选填，最多2字符）" else "Emoji (optional, max 2 chars)"

fun ToolboxStrings.tagEntriesCount(count: Int): String =
    if (chinese) "$count 条记录" else "$count entries"

val ToolboxStrings.tagsEmptyHint: String
    get() = if (chinese) "暂无标签" else "No tags yet"

// Renewals & linked entries

val ToolboxStrings.accountsTabLabel: String
    get() = if (chinese) "账户" else "Accounts"

val ToolboxStrings.defaultAccountName: String
    get() = if (chinese) "默认账户" else "Default account"

val ToolboxStrings.transferTypeLabel: String
    get() = if (chinese) "转账" else "Transfer"

val ToolboxStrings.accountFieldLabel: String
    get() = if (chinese) "账户" else "Account"

val ToolboxStrings.transferFromLabel: String
    get() = if (chinese) "转出账户" else "From"

val ToolboxStrings.transferToLabel: String
    get() = if (chinese) "转入账户" else "To"

val ToolboxStrings.sameAccountError: String
    get() = if (chinese) "转入与转出账户不能相同" else "Accounts must differ"

fun ToolboxStrings.accountAmountLabel(action: String, currency: String): String = if (chinese) {
    "账户${action}金额（$currency）"
} else {
    "Account $action amount ($currency)"
}

val ToolboxStrings.accountDebitWord: String
    get() = if (chinese) "扣款" else "debit"

val ToolboxStrings.accountCreditWord: String
    get() = if (chinese) "入账" else "credit"

val ToolboxStrings.totalAssetsLabel: String
    get() = if (chinese) "总资产" else "Total assets"

val ToolboxStrings.partialConversionNote: String
    get() = if (chinese) "部分账户未折算" else "Some accounts not converted"

val ToolboxStrings.newAccountAction: String
    get() = if (chinese) "＋ 新建账户" else "+ New account"

val ToolboxStrings.accountBalanceLabel: String
    get() = if (chinese) "余额" else "Balance"

val ToolboxStrings.reconcileAction: String
    get() = if (chinese) "校准余额" else "Reconcile"

val ToolboxStrings.reconcilePrompt: String
    get() = if (chinese) "输入账户的实际余额" else "Enter the actual balance"

val ToolboxStrings.balanceMatchesToast: String
    get() = if (chinese) "余额一致" else "Balance matches"

val ToolboxStrings.accountNameLabel: String
    get() = if (chinese) "账户名称" else "Account name"

val ToolboxStrings.accountCurrencyLabel: String
    get() = if (chinese) "币种" else "Currency"

val ToolboxStrings.accountDeleteBlockedHint: String
    get() = if (chinese) "该账户有关联记录，无法删除，可先归档" else "Has entries — archive instead of deleting"

val ToolboxStrings.accountTransactionsLabel: String
    get() = if (chinese) "交易明细" else "Transactions"

val ToolboxStrings.accountEmptyHint: String
    get() = if (chinese) "还没有账户，点下方新建" else "No accounts yet"

fun ToolboxStrings.totalAssetsText(amount: String, partial: Boolean): String = if (chinese) {
    "总资产 ≈ $amount" + if (partial) "（部分账户未折算）" else ""
} else {
    "Total assets ≈ $amount" + if (partial) " (partial)" else ""
}

fun ToolboxStrings.transferLine(from: String, to: String): String = "$from → $to"

val ToolboxStrings.tagLabel: String
    get() = if (chinese) "标签" else "Tags"

fun ToolboxStrings.tagRowCaption(count: Int, expenseText: String, incomeText: String?): String =
    if (chinese) {
        "$count 笔 · 累计 $expenseText" + (incomeText?.let { " · 收 $it" } ?: "")
    } else {
        "$count entries · $expenseText total" + (incomeText?.let { " · in $it" } ?: "")
    }

val ToolboxStrings.totalLabel: String
    get() = if (chinese) "累计" else "All time"

val ToolboxStrings.manageTagsChip: String
    get() = if (chinese) "管理标签" else "Tags"

fun ToolboxStrings.selectedCountLabel(count: Int): String =
    if (chinese) "已选 $count" else "$count selected"

val ToolboxStrings.addTagsAction: String
    get() = if (chinese) "添加标签" else "Add tag"

val ToolboxStrings.removeTagsAction: String
    get() = if (chinese) "移除标签" else "Remove tag"

val ToolboxStrings.tagGroupTech: String
    get() = if (chinese) "科技与订阅" else "Tech & subscriptions"

val ToolboxStrings.tagGroupLife: String
    get() = if (chinese) "生活" else "Daily life"

val ToolboxStrings.tagGroupIncome: String
    get() = if (chinese) "收入" else "Income"

/** Preset tag suggestions (emoji, name) by group — localized. */

val ToolboxStrings.tagPresets: List<Pair<String, List<Pair<String, String>>>>
    get() = if (chinese) {
        listOf(
            "科技与订阅" to listOf(
                "🖥️" to "VPS", "🤖" to "AI", "🌐" to "网络", "☁️" to "云服务",
                "🔑" to "域名", "📡" to "话费流量", "💻" to "软件", "📱" to "数码",
                "🎧" to "外设", "🎬" to "视频会员", "🎵" to "音乐", "🎮" to "游戏",
            ),
            "生活" to listOf(
                "🍜" to "餐饮", "☕" to "咖啡", "🛒" to "日用", "🛍️" to "购物",
                "🏠" to "房租", "💡" to "水电", "🚇" to "交通", "🚗" to "汽车",
                "✈️" to "旅行", "💊" to "医疗", "📚" to "学习", "👕" to "服饰",
                "🐱" to "宠物", "🎁" to "礼物",
            ),
            "收入" to listOf(
                "💼" to "工资", "💰" to "奖金", "📈" to "投资", "🧧" to "红包",
                "🪙" to "副业",
            ),
        )
    } else {
        listOf(
            "Tech & subscriptions" to listOf(
                "🖥️" to "VPS", "🤖" to "AI", "🌐" to "Network", "☁️" to "Cloud",
                "🔑" to "Domain", "📡" to "Phone plan", "💻" to "Software",
                "📱" to "Gadgets", "🎧" to "Peripherals", "🎬" to "Streaming",
                "🎵" to "Music", "🎮" to "Games",
            ),
            "Daily life" to listOf(
                "🍜" to "Food", "☕" to "Coffee", "🛒" to "Essentials",
                "🛍️" to "Shopping", "🏠" to "Rent", "💡" to "Utilities",
                "🚇" to "Transit", "🚗" to "Car", "✈️" to "Travel",
                "💊" to "Health", "📚" to "Learning", "👕" to "Clothes",
                "🐱" to "Pets", "🎁" to "Gifts",
            ),
            "Income" to listOf(
                "💼" to "Salary", "💰" to "Bonus", "📈" to "Investing",
                "🧧" to "Red envelope", "🪙" to "Side job",
            ),
        )
    }

/** Emoji-only quick picks for account emoji fields. */

val ToolboxStrings.accountEmojiPresets: List<String>
    get() = listOf("🏦", "💳", "💴", "💵", "💶", "👛", "🐷", "📱", "🪙", "💰")

fun ToolboxStrings.stoppedOnLine(date: String): String =
    if (chinese) "已停订 $date" else "Stopped $date"

// FX sources
