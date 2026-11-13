package com.example.toolbox.ui

import com.example.toolbox.ledger.BillingCycle
import com.example.toolbox.ledger.CycleUnit
import com.example.toolbox.ledger.LedgerCategory
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val ToolboxStrings.statusAmortized: String
    get() = if (chinese) "已摊销完" else "Amortized"

val ToolboxStrings.perDayUnit: String
    get() = if (chinese) "/天" else "/day"

val ToolboxStrings.perYearUnit: String
    get() = if (chinese) "/年" else "/yr"

val ToolboxStrings.emptyCostItemsHint: String
    get() = if (chinese) {
        "暂无成本项。新建支出时开启「计入平均成本」，即可追踪大件折旧与订阅周期成本。"
    } else {
        "No cost items yet. Enable 'Track average cost' when adding an expense to track assets and subscriptions."
    }

val ToolboxStrings.moreOptions: String
    get() = if (chinese) "更多选项" else "More options"

val ToolboxStrings.ledgerOpenFailed: String
    get() = if (chinese) "无法打开账本" else "Could not open ledger"

val ToolboxStrings.ledgerUpgradeBackupFailed: String
    get() = if (chinese) {
        "升级前副本保存失败，已停止升级并保留原数据。请检查可用存储空间后重试。"
    } else {
        "The pre-upgrade copy could not be saved. Upgrade was stopped and original data kept. Check available storage and retry."
    }

val ToolboxStrings.ledgerOpenFailedHint: String
    get() = if (chinese) {
        "账本读取失败，原数据库已保留。请重试。"
    } else {
        "The ledger could not be read. The original database was kept. Please retry."
    }

val ToolboxStrings.snapshotRestoreMenu: String
    get() = if (chinese) "恢复升级前副本…" else "Restore pre-upgrade copy…"

val ToolboxStrings.snapshotRestoreTitle: String
    get() = if (chinese) "升级前数据库副本" else "Pre-upgrade database copies"

val ToolboxStrings.snapshotRestoreEmpty: String
    get() = if (chinese) "暂无可恢复的副本" else "No copies available yet"

val ToolboxStrings.snapshotRestoreHint: String
    get() = if (chinese) {
        "所选副本通过校验后会整体替换当前账本；替换前会保存当前数据的副本，失败时保留原账本。"
    } else {
        "The selected copy is checked before replacing the ledger. Current data is saved first; a failed restore keeps the original ledger."
    }

val ToolboxStrings.snapshotRestoreAction: String
    get() = if (chinese) "恢复" else "Restore"

val ToolboxStrings.snapshotRestored: String
    get() = if (chinese) "已从副本恢复账本" else "Ledger restored from copy"

val ToolboxStrings.snapshotRestoreFailed: String
    get() = if (chinese) "恢复失败" else "Restore failed"

fun ToolboxStrings.snapshotSchemaVersion(version: Int): String =
    if (chinese) "结构 v$version" else "schema v$version"

val ToolboxStrings.csvHeaders: List<String>
    get() = if (chinese) {
        listOf(
            "日期", "类型", "标签", "名称", "金额", "币种",
            "汇率(CNY)", "折合人民币", "备注", "成本模式", "账户", "转入账户",
            "UUID",
        )
    } else {
        listOf(
            "Date", "Type", "Tags", "Title", "Amount", "Currency",
            "Rate (CNY)", "CNY amount", "Note", "Cost mode", "Account",
            "To account", "UUID",
        )
    }

val ToolboxStrings.ledgerWidgetTitle: String
    get() = if (chinese) "记账" else "Ledger"

val ToolboxStrings.ledgerTabOverview: String
    get() = if (chinese) "概览" else "Overview"

val ToolboxStrings.ledgerTabTransactions: String
    get() = if (chinese) "明细" else "Transactions"

val ToolboxStrings.ledgerTabCosts: String
    get() = if (chinese) "成本" else "Costs"

val ToolboxStrings.addEntryShort: String
    get() = if (chinese) "记一笔" else "Add"

val ToolboxStrings.allToolsLabel: String
    get() = if (chinese) "全部工具" else "All tools"

fun ToolboxStrings.monthEntriesCount(count: Int): String =
    if (chinese) "$count 笔" else "$count entr${if (count == 1) "y" else "ies"}"

val ToolboxStrings.monthExpenseLabel: String
    get() = if (chinese) "本月支出" else "Spending"

val ToolboxStrings.monthlyRegularExpenseLabel: String
    get() = if (chinese) "日常与月付" else "Everyday + monthly"

val ToolboxStrings.ordinaryExpenseLabel: String
    get() = if (chinese) "日常支出" else "Everyday"

val ToolboxStrings.monthlySubscriptionExpenseLabel: String
    get() = if (chinese) "月付订阅" else "Monthly subs"

val ToolboxStrings.oneTimeOutlayLabel: String
    get() = if (chinese) "一次性支出" else "One-time"

val ToolboxStrings.oneTimeOutlayCaption: String
    get() = if (chinese) "不计入结余" else "Excluded from net"

val ToolboxStrings.actualMonthExpenseLabel: String
    get() = if (chinese) "实际支出" else "Actual outlays"

val ToolboxStrings.monthlyRegularSpendingRule: String
    get() = if (chinese) {
        "结余只扣日常支出和已入账的月付订阅。年付、周付等周期款只计入实际付款的那个月，记一次性支出，不摊进结余。"
    } else {
        "Net deducts everyday spending and recorded monthly subscriptions. Yearly, weekly, and other cycle payments count only in the month they are paid, as one-time outlays."
    }

val ToolboxStrings.dailyHoldingCostRule: String
    get() = if (chinese) {
        "摊销持有，不是本月现金支出。周期付款只计入实际付款的那个月。"
    } else {
        "Amortized holding, not this month's cash spending. Cycle payments count only in the month they are paid."
    }

val ToolboxStrings.cycleCashInPaymentMonth: String
    get() = if (chinese) "计入付款月" else "Counts in the payment month"

fun ToolboxStrings.actualMonthExpenseLine(amount: String, forBudget: Boolean = false): String =
    if (chinese) {
        "实际支出 $amount" + if (forBudget) "（预算按此计算）" else ""
    } else {
        "Actual outlays $amount" + if (forBudget) " (used for budget)" else ""
    }

fun ToolboxStrings.oneTimeOutlayLine(amount: String): String =
    if (chinese) {
        "一次性支出 $amount（不计入结余）"
    } else {
        "One-time $amount (excluded from net)"
    }

val ToolboxStrings.monthIncomeLabel: String
    get() = if (chinese) "收入" else "Income"

val ToolboxStrings.monthNetLabel: String
    get() = if (chinese) "结余" else "Net"

fun ToolboxStrings.trendTitle(months: Int): String =
    if (chinese) "近 $months 个月实际收支" else "Cash flow over $months months"

val ToolboxStrings.trendExpenseLegend: String
    get() = actualMonthExpenseLabel

fun ToolboxStrings.trendMonthLabel(month: YearMonth): String =
    if (chinese) {
        "${month.monthValue}月"
    } else {
        month.month.getDisplayName(TextStyle.SHORT, Locale.US)
    }

fun ToolboxStrings.trendMonthDetail(month: YearMonth, expense: String, income: String): String =
    if (chinese) {
        "${formatLedgerMonth(month)} 实际支出 $expense · 收入 $income"
    } else {
        "${formatLedgerMonth(month)}: actual outlays $expense · income $income"
    }

fun ToolboxStrings.trendAverageExpense(months: Int, amount: String): String =
    if (chinese) {
        "近 $months 个完整月平均实际支出 $amount"
    } else {
        "Actual outlays over the last $months full months: $amount per month"
    }

val ToolboxStrings.trendTapHint: String
    get() = if (chinese) "点月份查看当月明细，长按复制" else "Tap a month to open its entries; long-press to copy"

val ToolboxStrings.budgetTitle: String
    get() = if (chinese) "预算" else "Budgets"

val ToolboxStrings.budgetMenu: String
    get() = if (chinese) "预算设置" else "Budgets"

val ToolboxStrings.ledgerOverviewOrderMenu: String
    get() = if (chinese) "概览卡片排序…" else "Overview card order…"

val ToolboxStrings.ledgerOverviewOrderTitle: String
    get() = if (chinese) "概览卡片排序" else "Overview card order"

val ToolboxStrings.ledgerOverviewOrderHint: String
    get() = if (chinese) {
        "拖动卡片调整顺序，修改会立即保存。"
    } else {
        "Drag a card to reorder it; changes save immediately."
    }

val ToolboxStrings.ledgerOverviewOrderDrag: String
    get() = if (chinese) "拖动排序" else "Drag to reorder"

val ToolboxStrings.ledgerOverviewMoveUp: String
    get() = if (chinese) "上移" else "Move up"

val ToolboxStrings.ledgerOverviewMoveDown: String
    get() = if (chinese) "下移" else "Move down"

val ToolboxStrings.ledgerOverviewOrderReset: String
    get() = if (chinese) "恢复默认" else "Reset"

fun ToolboxStrings.ledgerOverviewCardName(card: LedgerOverviewCard): String = when (card) {
    LedgerOverviewCard.MONTH -> if (chinese) "本月收支" else "Month summary"
    LedgerOverviewCard.RECENT -> if (chinese) "最近记录" else "Recent entries"
    LedgerOverviewCard.TAGS -> if (chinese) "按标签" else "By tag"
    LedgerOverviewCard.TREND -> if (chinese) "收支趋势" else "Trend"
    LedgerOverviewCard.COST -> if (chinese) "日均成本" else "Daily cost"
}

val ToolboxStrings.budgetMonthlyLabel: String
    get() = if (chinese) "每月总预算" else "Monthly budget"

val ToolboxStrings.budgetByTagLabel: String
    get() = if (chinese) "按标签（每月）" else "Per tag (monthly)"

val ToolboxStrings.budgetNonePlaceholder: String
    get() = if (chinese) "不设置" else "None"

val ToolboxStrings.budgetLocalOnlyNote: String
    get() = if (chinese) {
        "金额按人民币计算，留空表示不设置。预算只保存在本机，不包含在 JSON 备份和 WebDAV 同步中。"
    } else {
        "Amounts are in CNY; leave blank for none. Budgets stay on this device and are not part of JSON backups or WebDAV sync."
    }

val ToolboxStrings.budgetSetAction: String
    get() = if (chinese) "设置月度预算" else "Set a monthly budget"

fun ToolboxStrings.budgetRemaining(budget: String, remaining: String, perDay: String?): String =
    if (chinese) {
        "预算 $budget · 还剩 $remaining" + perDay?.let { " · 每天可用 $it" }.orEmpty()
    } else {
        "Budget $budget · $remaining left" + perDay?.let { " · $it/day" }.orEmpty()
    }

fun ToolboxStrings.budgetOver(budget: String, over: String): String =
    if (chinese) "预算 $budget · 已超支 $over" else "Budget $budget · over by $over"

fun ToolboxStrings.budgetOfTotal(spent: String, budget: String): String = "$spent / $budget"

val ToolboxStrings.noExpenseThisMonth: String
    get() = if (chinese) "本月暂无支出" else "No expenses this month"

val ToolboxStrings.recentEntriesTitle: String
    get() = if (chinese) "最近记录" else "Recent"

val ToolboxStrings.seeAllAction: String
    get() = if (chinese) "查看全部" else "See all"

val ToolboxStrings.emptyOverviewHint: String
    get() = if (chinese) {
        "还没有记录，点右下角「记一笔」开始记账。"
    } else {
        "No entries yet — tap 'Add' to record your first one."
    }

val ToolboxStrings.emptyMonthEntries: String
    get() = if (chinese) "本月暂无收支记录" else "No entries this month"

val ToolboxStrings.searchFieldHint: String
    get() = if (chinese) {
        "搜索标题、备注、标签、账户或金额"
    } else {
        "Search title, note, tag, account, or amount"
    }

val ToolboxStrings.subTag: String
    get() = if (chinese) "订阅" else "Sub"

val ToolboxStrings.entryDateLabel: String
    get() = if (chinese) "日期" else "Date"

val ToolboxStrings.prevMonth: String
    get() = if (chinese) "上个月" else "Previous month"

val ToolboxStrings.nextMonth: String
    get() = if (chinese) "下个月" else "Next month"

val ToolboxStrings.selectLedgerMonth: String
    get() = if (chinese) "选择年月" else "Choose month"

val ToolboxStrings.ledgerYearLabel: String
    get() = if (chinese) "年份" else "Year"

fun ToolboxStrings.ledgerYearRangeHint(latestYear: Int): String =
    if (chinese) "年份需在 1–$latestYear 之间" else "Enter a year from 1 to $latestYear"

val ToolboxStrings.previousLedgerYear: String
    get() = if (chinese) "上一年" else "Previous year"

val ToolboxStrings.nextLedgerYear: String
    get() = if (chinese) "下一年" else "Next year"

fun ToolboxStrings.ledgerMonthLabel(month: Int): String =
    if (chinese) "${month}月" else java.time.Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

val ToolboxStrings.searchTagsHint: String
    get() = if (chinese) "搜索标签" else "Search tags"

val ToolboxStrings.noMatchingTags: String
    get() = if (chinese) "没有匹配的标签" else "No matching tags"

val ToolboxStrings.addLedgerEntry: String
    get() = if (chinese) "记一笔" else "Add entry"

val ToolboxStrings.quickPicksLabel: String
    get() = if (chinese) "常用（点一下填好，确认后保存）" else "Frequent (tap to fill in, then save)"

val ToolboxStrings.ledgerSaveFailed: String
    get() = if (chinese) "未能保存，请重试；编辑内容已保留" else "Could not save. Your edits are kept; please retry."

val ToolboxStrings.editLedgerEntry: String
    get() = if (chinese) "编辑账目" else "Edit entry"

val ToolboxStrings.trackAverageCostOption: String
    get() = if (chinese) "计入平均成本" else "Track average cost"

val ToolboxStrings.trackAverageCostHint: String
    get() = if (chinese) {
        "开启后自动折算日均（¥/天）与月均（¥/月）成本，支持一次性买断大件与周期性订阅。"
    } else {
        "Calculates average daily (¥/day) and monthly (¥/mo) cost for one-time assets or recurring subscriptions."
    }

val ToolboxStrings.entryTitleLabel: String
    get() = if (chinese) "名称（选填，留空使用分类名）" else "Title (optional, defaults to category)"

val ToolboxStrings.entryAmountLabel: String
    get() = if (chinese) "金额" else "Amount"

val ToolboxStrings.salvageValueLabel: String
    get() = if (chinese) "预估残值 / 二手回血（选填，与金额同币种）" else "Estimated salvage value (optional, same currency)"

val ToolboxStrings.targetDaysLabel: String
    get() = if (chinese) "预计使用天数（选填，留空按实际天数摊销）" else "Target lifespan days (optional)"

val ToolboxStrings.billingCycleLabel: String
    get() = if (chinese) "计费周期" else "Billing cycle"

val ToolboxStrings.cycleWeekly: String
    get() = if (chinese) "按周（7天）" else "Weekly (7d)"

val ToolboxStrings.cycleMonthly: String
    get() = if (chinese) "按月" else "Monthly"

val ToolboxStrings.cycleQuarterly: String
    get() = if (chinese) "按季（3个月）" else "Quarterly"

val ToolboxStrings.cycleSemiAnnual: String
    get() = if (chinese) "每半年（6个月）" else "Semi-annual (6 mo)"

val ToolboxStrings.cycleYearly: String
    get() = if (chinese) "按年（12个月）" else "Yearly"

val ToolboxStrings.cycleCustomChip: String
    get() = if (chinese) "自定义" else "Custom"

val ToolboxStrings.customDaysInputLabel: String
    get() = if (chinese) "周期数" else "Interval count"

val ToolboxStrings.entryNoteLabel: String
    get() = if (chinese) "备注（选填）" else "Note (optional)"

val ToolboxStrings.expenseTypeLabel: String
    get() = if (chinese) "支出" else "Expense"

val ToolboxStrings.incomeTypeLabel: String
    get() = if (chinese) "收入" else "Income"

val ToolboxStrings.statusActiveInUse: String
    get() = if (chinese) "服役中" else "In use"

val ToolboxStrings.statusRetired: String
    get() = if (chinese) "已退役" else "Retired"

val ToolboxStrings.statusSubActive: String
    get() = if (chinese) "订阅中" else "Active"

val ToolboxStrings.statusSubStopped: String
    get() = if (chinese) "已停订" else "Stopped"

val ToolboxStrings.statusEnded: String
    get() = if (chinese) "已到期" else "Ended"

fun ToolboxStrings.decimalsNotAllowed(code: String): String =
    if (chinese) "$code 金额不支持小数" else "$code amounts can't have decimals"

fun ToolboxStrings.formatLedgerDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(
            DateTimeFormatter.ofPattern(
                if (chinese) "yyyy年M月d日 HH:mm" else "MMM d, yyyy HH:mm",
                if (chinese) Locale.CHINA else Locale.US,
            ),
        )

fun ToolboxStrings.ledgerCategory(category: LedgerCategory): String = when (category) {
    LedgerCategory.FOOD -> if (chinese) "餐饮" else "Food"
    LedgerCategory.TRANSPORT -> if (chinese) "交通" else "Transport"
    LedgerCategory.SHOPPING -> if (chinese) "购物" else "Shopping"
    LedgerCategory.HOUSING -> if (chinese) "居住" else "Housing"
    LedgerCategory.ELECTRONICS -> if (chinese) "数码" else "Electronics"
    LedgerCategory.ENTERTAINMENT -> if (chinese) "娱乐" else "Entertainment"
    LedgerCategory.HEALTH -> if (chinese) "健康" else "Health"
    LedgerCategory.EDUCATION -> if (chinese) "教育" else "Education"
    LedgerCategory.SALARY -> if (chinese) "工资" else "Salary"
    LedgerCategory.BONUS -> if (chinese) "奖金" else "Bonus"
    LedgerCategory.INVESTMENT -> if (chinese) "投资" else "Investment"
    LedgerCategory.OTHER -> if (chinese) "其它" else "Other"
}

fun ToolboxStrings.formatLedgerMonth(month: YearMonth): String =
    month.format(
        DateTimeFormatter.ofPattern(
            if (chinese) "yyyy年M月" else "MMMM yyyy",
            if (chinese) Locale.CHINA else Locale.US,
        ),
    )

fun ToolboxStrings.formatLedgerDay(date: LocalDate): String =
    date.format(
        DateTimeFormatter.ofPattern(
            if (chinese) "M月d日 EEE" else "EEE, MMM d",
            if (chinese) Locale.CHINA else Locale.US,
        ),
    )

fun ToolboxStrings.formatLedgerWeekday(date: LocalDate): String =
    date.format(
        DateTimeFormatter.ofPattern(
            "EEE",
            if (chinese) Locale.CHINA else Locale.US,
        ),
    )

fun ToolboxStrings.cycleName(
    cycle: BillingCycle,
    customDays: Int,
    customUnit: CycleUnit = CycleUnit.DAYS,
): String = when (cycle) {
    BillingCycle.WEEKLY -> cycleWeekly
    BillingCycle.MONTHLY -> cycleMonthly
    BillingCycle.QUARTERLY -> cycleQuarterly
    BillingCycle.SEMI_ANNUAL -> cycleSemiAnnual
    BillingCycle.YEARLY -> cycleYearly
    BillingCycle.CUSTOM_DAYS -> if (chinese) {
        "每 $customDays${cycleUnitName(customUnit)}"
    } else {
        "Every $customDays ${cycleUnitName(customUnit)}"
    }
}

fun ToolboxStrings.cycleUnitName(unit: CycleUnit): String = when (unit) {
    CycleUnit.DAYS -> if (chinese) "天" else "days"
    CycleUnit.WEEKS -> if (chinese) "周" else "weeks"
    CycleUnit.MONTHS -> if (chinese) "月" else "months"
    CycleUnit.YEARS -> if (chinese) "年" else "years"
}

val ToolboxStrings.oneTimeAssetMode: String
    get() = if (chinese) "一次性大件折旧" else "One-time asset"

val ToolboxStrings.deleteEntryConfirm: String
    get() = if (chinese) {
        "删除后该账目将从列表移除，并在同步时作为删除标记保留。确定删除？"
    } else {
        "Delete this entry? It will be removed from the list and kept as a deletion marker for sync."
    }

val ToolboxStrings.amountInvalidError: String
    get() = if (chinese) "请输入大于 0 的金额" else "Enter an amount greater than 0"

fun ToolboxStrings.oneTimeHeldInfo(
    price: String,
    salvage: String?,
    daysHeld: Int,
    targetDays: Int?,
    actualDaily: String?,
): String = if (chinese) {
    buildString {
        append("购入价 $price")
        salvage?.let { append(" · 残值 $it") }
        append(" · 已持有 $daysHeld 天")
        if (targetDays != null) {
            append("（目标 $targetDays 天")
            actualDaily?.let { append("，至今实际 $it/天") }
            append("）")
        }
    }
} else {
    buildString {
        append("Price $price")
        salvage?.let { append(" · Salvage $it") }
        append(" · Held ${daysHeld}d")
        if (targetDays != null) {
            append(" (target ${targetDays}d")
            actualDaily?.let { append(", actual $it/day") }
            append(")")
        }
    }
}

fun ToolboxStrings.purchasedOn(date: String): String =
    if (chinese) "购入 $date" else "Bought $date"

fun ToolboxStrings.daysCount(days: Int): String =
    if (chinese) "${days}天" else "${days}d"


// Tags

val ToolboxStrings.filterMatchAny: String
    get() = if (chinese) "任一标签" else "Any tag"

val ToolboxStrings.filterMatchAll: String
    get() = if (chinese) "全部标签" else "All tags"

val ToolboxStrings.clearFilter: String
    get() = if (chinese) "清除" else "Clear"

val ToolboxStrings.breakdownByTags: String
    get() = if (chinese) "按标签" else "By tag"

val ToolboxStrings.untaggedLabel: String
    get() = if (chinese) "未标记" else "Untagged"

val ToolboxStrings.multiTagNote: String
    get() = if (chinese) {
        "一笔记录有多个标签时会分别计入"
    } else {
        "Entries with multiple tags count under each of them"
    }

val ToolboxStrings.newTagChip: String
    get() = if (chinese) "＋ 新建标签" else "+ New tag"

val ToolboxStrings.addTagAction: String
    get() = if (chinese) "添加" else "Add"

fun ToolboxStrings.deleteTagConfirm(count: Int): String = if (chinese) {
    "该标签下有 $count 条记录，删除后这些记录将不再显示此标签。确定删除？"
} else {
    "$count entries will no longer show this tag. Delete it?"
}

val ToolboxStrings.recordAction: String
    get() = if (chinese) "记录" else "Record"

val ToolboxStrings.recordAllAction: String
    get() = if (chinese) "全部记录" else "Record all"

val ToolboxStrings.skipAction: String
    get() = if (chinese) "跳过" else "Skip"

val ToolboxStrings.skipRenewalsConfirm: String
    get() = if (chinese) {
        "跳过后这些续费不会计入支出，也不会再提示。确定跳过？"
    } else {
        "Skipped renewals won't count as expenses and won't be asked again. Skip them?"
    }

fun ToolboxStrings.useNearestCachedRate(date: String, source: String): String = if (chinese) {
    "使用最近缓存汇率（$date · $source）"
} else {
    "Use nearest cached rate ($date · $source)"
}

val ToolboxStrings.saleTagLabel: String
    get() = if (chinese) "卖出" else "Sale"

fun ToolboxStrings.saleLinkedInfo(parentTitle: String): String =
    if (chinese) "二手卖出 · 关联：$parentTitle" else "Resale · linked to $parentTitle"

val ToolboxStrings.recurringIncomeOption: String
    get() = if (chinese) "固定收入（周期性）" else "Recurring income"

fun ToolboxStrings.nextRenewalLabel(date: String, daysUntil: Int): String = if (chinese) {
    "下次续费 $date（${daysUntil}天后）"
} else {
    "Next renewal $date (in ${daysUntil}d)"
}

// Accounts & transfers

val ToolboxStrings.adjustmentTypeLabel: String
    get() = if (chinese) "余额调整" else "Balance adjustment"

val ToolboxStrings.adjustmentEditorHint: String
    get() = if (chinese) {
        "由「校准余额」生成的差额，不计入收支，也不在明细中显示"
    } else {
        "Created by Reconcile; affects the account balance only and is " +
            "not listed under Transactions"
    }

val ToolboxStrings.adjustmentTitleText: String
    get() = if (chinese) "余额调整" else "Balance adjustment"

val ToolboxStrings.archiveAction: String
    get() = if (chinese) "归档" else "Archive"

val ToolboxStrings.unarchiveAction: String
    get() = if (chinese) "取消归档" else "Unarchive"

val ToolboxStrings.archivedBadge: String
    get() = if (chinese) "已归档" else "Archived"

val ToolboxStrings.openingBalanceLabel: String
    get() = if (chinese) "初始余额" else "Opening balance"

val ToolboxStrings.openingDateLabel: String
    get() = if (chinese) "期初日期" else "Opening date"

val ToolboxStrings.currencyLockedHasEntries: String
    get() = if (chinese) "已有记录，币种不可修改" else "Currency locked (has entries)"

fun ToolboxStrings.deleteAccountConfirm(name: String): String =
    if (chinese) "删除账户「$name」？仅当它没有记录时可用。" else "Delete \"$name\"? Only when unused."

fun ToolboxStrings.signedAmount(amount: String, negative: Boolean): String =
    (if (negative) "−" else "+") + amount

// Tag management v2

val ToolboxStrings.mergeIntoAction: String
    get() = if (chinese) "合并到…" else "Merge into…"

fun ToolboxStrings.mergeTagConfirm(from: String, to: String, count: Int): String =
    if (chinese) {
        "「$from」的 $count 笔记录将改为「$to」，「$from」将被删除"
    } else {
        "$count entries move from \"$from\" to \"$to\"; \"$from\" is deleted"
    }

val ToolboxStrings.mergeTargetLabel: String
    get() = if (chinese) "选择要合并到的标签" else "Merge into which tag?"

val ToolboxStrings.thisMonthLabel: String
    get() = if (chinese) "本月" else "This month"

val ToolboxStrings.recommendedLabel: String
    get() = if (chinese) "推荐" else "Suggestions"

val ToolboxStrings.manageLabel: String
    get() = if (chinese) "管理" else "Manage"

val ToolboxStrings.copyAction: String
    get() = if (chinese) "复制" else "Copy"

fun ToolboxStrings.monthBalanceLabel(netText: String): String =
    if (chinese) "本月结余" else "Net this month"

val ToolboxStrings.saleAmountLabel: String
    get() = if (chinese) "转卖金额" else "Resale amount"

val ToolboxStrings.csvTagsHeader: String
    get() = if (chinese) "标签" else "Tags"
