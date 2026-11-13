package com.example.toolbox.ui


val ToolboxStrings.amortizeUntilLabel: String
    get() = if (chinese) {
        "摊销结束日期（选填，填写后代替预计使用天数）"
    } else {
        "Amortize until (optional, replaces lifespan days)"
    }

val ToolboxStrings.lifespanFromEndDate: String
    get() = if (chinese) "按摊销结束日期计算" else "Set by the amortization end date"

fun ToolboxStrings.amortizesUntil(date: String): String =
    if (chinese) "摊销至 $date" else "Amortizing until $date"

fun ToolboxStrings.amortizedOn(date: String): String =
    if (chinese) "已于 $date 摊销完" else "Fully amortized $date"

val ToolboxStrings.costModeNone: String
    get() = if (chinese) "不计成本" else "Not tracked"

val ToolboxStrings.dailyBurnRateTitle: String
    get() = if (chinese) "持有成本看板" else "Holding cost"

val ToolboxStrings.totalDailyCostLabel: String
    get() = if (chinese) "日均持有" else "Daily holding"

val ToolboxStrings.totalYearlyCostLabel: String
    get() = if (chinese) "折合每年" else "Yearly equivalent"

val ToolboxStrings.dailyCostCaption: String
    get() = if (chinese) "日均持有成本" else "Daily holding cost"

val ToolboxStrings.costSectionAssets: String
    get() = if (chinese) "长期资产" else "Assets"

val ToolboxStrings.costSectionSubscriptions: String
    get() = if (chinese) "订阅" else "Subscriptions"

val ToolboxStrings.assetTag: String
    get() = if (chinese) "长期资产" else "Asset"

val ToolboxStrings.costModeOneTime: String
    get() = if (chinese) "一次性资产" else "One-time asset"

val ToolboxStrings.costModePeriodic: String
    get() = if (chinese) "周期订阅" else "Subscription"

val ToolboxStrings.costEndDateLabel: String
    get() = if (chinese) {
        "到期日期（选填，到期后自动停止计入）"
    } else {
        "End date (optional — stops counting after)"
    }

val ToolboxStrings.costEndDateNone: String
    get() = if (chinese) "无到期" else "No end date"

fun ToolboxStrings.costEndsOn(date: String): String =
    if (chinese) "到期 $date" else "Ends $date"

fun ToolboxStrings.costEndedOn(date: String): String =
    if (chinese) "已于 $date 到期" else "Ended $date"

val ToolboxStrings.costEndBeforeStartError: String
    get() = if (chinese) "结束日期必须晚于记账日期" else "The end date must be after the entry date"

fun ToolboxStrings.costItemsCount(assets: Int, subscriptions: Int): String =
    if (chinese) {
        "长期资产 $assets 项 · 订阅 $subscriptions 项"
    } else {
        "$assets asset${if (assets == 1) "" else "s"} · $subscriptions subscription${if (subscriptions == 1) "" else "s"}"
    }

fun ToolboxStrings.usedDaysTarget(usedDays: Int, targetDays: Int): String =
    if (chinese) "已用 $usedDays / 目标 $targetDays 天" else "Used $usedDays of $targetDays target days"

val ToolboxStrings.subscriptionMode: String
    get() = if (chinese) "周期订阅" else "Subscription"

val ToolboxStrings.retireAction: String
    get() = if (chinese) "设为已退役" else "Retire"

val ToolboxStrings.reactivateAction: String
    get() = if (chinese) "恢复服役" else "Reactivate"

val ToolboxStrings.stopSubscriptionAction: String
    get() = if (chinese) "停订…" else "Stop…"

val ToolboxStrings.resumeSubscriptionAction: String
    get() = if (chinese) "恢复订阅" else "Resume"

val ToolboxStrings.costShowDetails: String
    get() = if (chinese) "展开成本详情" else "Expand cost details"

val ToolboxStrings.costHideDetails: String
    get() = if (chinese) "收起成本详情" else "Collapse cost details"

val ToolboxStrings.lifespanActualDays: String
    get() = if (chinese) "按实际持有天数" else "Actual days held"

fun ToolboxStrings.lifespanYearsPreset(years: Int, days: Int): String =
    if (chinese) "${years}年(${days}天)" else "$years yr${if (years == 1) "" else "s"} (${days}d)"

val ToolboxStrings.assetPriceLabel: String
    get() = if (chinese) "购入价" else "Price"

val ToolboxStrings.assetCnyLabel: String
    get() = if (chinese) "合人民币" else "CNY"

val ToolboxStrings.assetSalvageLabel: String
    get() = if (chinese) "残值" else "Salvage"

val ToolboxStrings.assetNetCostLabel: String
    get() = if (chinese) "净成本" else "Net cost"

val ToolboxStrings.assetHeldLabel: String
    get() = if (chinese) "已持有" else "Held"

val ToolboxStrings.assetRemainingDaysLabel: String
    get() = if (chinese) "剩余" else "Left"

val ToolboxStrings.assetRemainingBookLabel: String
    get() = if (chinese) "剩余账面" else "Book value"

val ToolboxStrings.assetActualDailyLabel: String
    get() = if (chinese) "至今实际" else "Actual"

val ToolboxStrings.assetYearlyLabel: String
    get() = if (chinese) "年摊销" else "Yearly"

val ToolboxStrings.pendingRenewalsTitle: String
    get() = if (chinese) "待确认的周期收支" else "Pending recurring items"

fun ToolboxStrings.renewalDatesSummary(count: Int, dates: String): String =
    "$count${if (chinese) " 次 · " else "x · "}$dates"

fun ToolboxStrings.renewalsRecorded(count: Int): String =
    if (chinese) "已记录 $count 次续费" else "Recorded $count renewals"

fun ToolboxStrings.renewalRateMissing(count: Int): String = if (chinese) {
    "$count 次续费无法获取当日汇率"
} else {
    "Couldn't fetch the day's rate for $count renewals"
}

val ToolboxStrings.renewalTagLabel: String
    get() = if (chinese) "续费" else "Renewal"

fun ToolboxStrings.renewalOrdinalInfo(index: Int): String =
    if (chinese) "订阅续费 · 第 $index 次" else "Subscription renewal · #$index"

val ToolboxStrings.fixedIncomeSection: String
    get() = if (chinese) "固定收入" else "Fixed income"

fun ToolboxStrings.accumulatedPayments(count: Int, total: String): String = if (chinese) {
    "累计支付 $count 次 · 合计 $total"
} else {
    "$count payments · $total total"
}

fun ToolboxStrings.accumulatedIncome(count: Int, total: String): String = if (chinese) {
    "累计收款 $count 次 · 合计 $total"
} else {
    "$count payments received · $total total"
}

val ToolboxStrings.nextPaydayLabel: String
    get() = if (chinese) "下次收款" else "Next payday"

val ToolboxStrings.endUseAction: String
    get() = if (chinese) "结束使用…" else "End use…"

val ToolboxStrings.sellOption: String
    get() = if (chinese) "转卖二手" else "Resell"

val ToolboxStrings.scrapOption: String
    get() = if (chinese) "报废 · 停用" else "Scrap · retire"

val ToolboxStrings.disposalDateLabel: String
    get() = if (chinese) "结束日期" else "End date"

fun ToolboxStrings.soldLine(date: String, amount: String): String =
    if (chinese) "已转卖 $date · $amount" else "Resold $date · $amount"

fun ToolboxStrings.scrappedLine(date: String): String =
    if (chinese) "已报废 $date" else "Scrapped $date"

fun ToolboxStrings.soldTitlePrefix(title: String): String =
    if (chinese) "转卖：$title" else "Resold: $title"

val ToolboxStrings.undoDisposalAction: String
    get() = if (chinese) "撤销" else "Undo"

val ToolboxStrings.undoDisposalConfirm: String
    get() = if (chinese) {
        "撤销后资产恢复在用，卖出记录会被删除。确定撤销？"
    } else {
        "Undo restores the asset; the sale record is removed. Continue?"
    }

fun ToolboxStrings.disposalPreview(days: Int, finalCost: String, daily: String): String = if (chinese) {
    "持有 $days 天 · 最终成本 $finalCost · 日均 $daily"
} else {
    "Held $days days · final cost $finalCost · $daily/day"
}

val ToolboxStrings.stopSubscriptionTitle: String
    get() = if (chinese) "停订日期" else "Stop date"

fun ToolboxStrings.holdingCostPreview(daily: String, yearly: String): String =
    if (chinese) {
        "日均持有 $daily$perDayUnit · 折合每年 $yearly$perYearUnit。摊销不是每月实付。"
    } else {
        "Daily holding $daily$perDayUnit · $yearly$perYearUnit amortized. Not monthly cash."
    }

// Multi-WebDAV settings & TOTP
