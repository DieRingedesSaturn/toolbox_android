package com.example.toolbox.ui


val ToolboxStrings.notificationPermissionTitle: String
    get() = if (chinese) "通知权限" else "Notification permission"

val ToolboxStrings.notificationReady: String
    get() = if (chinese) "通知权限已允许" else "Notification permission granted"

val ToolboxStrings.notificationPermissionHint: String
    get() = if (chinese) "允许通知以便在状态栏显示监控状态与快捷停止操作。" else "Allow notifications to see monitor status and stop action in status bar."

val ToolboxStrings.notificationLedgerTitle: String
    get() = if (chinese) "通知快速记账" else "Quick entry from notifications"

val ToolboxStrings.notificationLedgerEnable: String
    get() = if (chinese) "从通知识别收付款" else "Recognize payments from notifications"

val ToolboxStrings.notificationLedgerHint: String
    get() = if (chinese) {
        "系统会授权读取通知，Toolbox 收到后只处理下方选中的应用。识别出的金额和通知原文只保存在本机、最多 7 天；默认点选金额后仍需确认保存，在来源应用里开启自动入账后，仅高置信的收付款会直接入账并可撤销。关闭时清空全部记录；系统授权需在设置中撤销。"
    } else {
        "Android grants notification access; Toolbox processes only selected apps after receipt. Extracted amounts and the original notification text stay on this device for up to 7 days. Picking an amount still requires confirmation; when auto-book is enabled for a source, only confident payments are booked directly and can be undone. Turning this off erases every record; revoke access in Settings."
    }

val ToolboxStrings.notificationLedgerGrantAccess: String
    get() = if (chinese) "前往系统设置授权通知读取" else "Grant notification access in Settings"

val ToolboxStrings.notificationLedgerManageAccess: String
    get() = if (chinese) "在系统设置中管理通知读取" else "Manage notification access in Settings"

val ToolboxStrings.notificationLedgerCandidatesTab: String
    get() = if (chinese) "待记账" else "Suggestions"

val ToolboxStrings.notificationLedgerRecordsTab: String
    get() = if (chinese) "记录" else "Records"

val ToolboxStrings.notificationLedgerSourcesTab: String
    get() = if (chinese) "来源应用" else "Source apps"

val ToolboxStrings.notificationLedgerStatusOff: String
    get() = if (chinese) {
        "已关闭：不再接收任何通知，候选项已清空"
    } else {
        "Off: no notifications are received; suggestions cleared"
    }

val ToolboxStrings.notificationLedgerStatusNeedsAccess: String
    get() = if (chinese) "还需授予系统通知读取权限" else "Notification access required"

val ToolboxStrings.notificationLedgerStatusNeedsSources: String
    get() = if (chinese) "请选择来源应用" else "Choose source apps"

fun ToolboxStrings.notificationLedgerStatusReady(count: Int): String =
    if (chinese) "已开启 · $count 个来源应用" else "On · $count source app${if (count == 1) "" else "s"}"

val ToolboxStrings.notificationLedgerCandidateHint: String
    get() = if (chinese) "点选金额后检查并保存" else "Choose an amount, then review and save"

val ToolboxStrings.notificationLedgerEmpty: String
    get() = if (chinese) "暂无候选金额" else "No suggested amounts"

val ToolboxStrings.notificationLedgerEmptyHint: String
    get() = if (chinese) "等待所选应用发来新通知。" else "Waiting for a new notification from a selected app."

val ToolboxStrings.notificationLedgerSources: String
    get() = if (chinese) "选择来源应用" else "Choose source apps"

val ToolboxStrings.notificationLedgerAppSearch: String
    get() = if (chinese) "搜索应用名称或包名" else "Search app or package"

val ToolboxStrings.notificationLedgerSelectedOnly: String
    get() = if (chinese) "只看已选" else "Selected only"

val ToolboxStrings.notificationLedgerNoApps: String
    get() = if (chinese) "没有匹配的应用" else "No matching apps"

val ToolboxStrings.notificationLedgerDiscard: String
    get() = if (chinese) "忽略" else "Dismiss"

val ToolboxStrings.notificationLedgerRecordsHint: String
    get() = if (chinese) {
        "所选应用的通知会按时间留在这里（验证码类不保留原文，仅本机），点开可核对识别结果"
    } else {
        "Notifications from selected apps are listed here, kept only on this device; verification-code notices keep no text. Tap one to audit the parse result"
    }

val ToolboxStrings.notificationLedgerRecordsEmpty: String
    get() = if (chinese) "还没有收到所选应用的通知" else "Nothing received from selected apps yet"

val ToolboxStrings.notificationLedgerClearRecords: String
    get() = if (chinese) "清空记录" else "Clear records"

fun ToolboxStrings.notificationLedgerAmountsFound(count: Int): String =
    if (chinese) "识别到 $count 个金额" else "$count amount${if (count == 1) "" else "s"} found"

val ToolboxStrings.notificationLedgerReasonEmpty: String
    get() = if (chinese) "通知没有可读取的文本" else "No readable text in the notification"

val ToolboxStrings.notificationLedgerReasonCode: String
    get() = if (chinese) "验证码类通知，未保留原文" else "Verification-code notice; text not stored"

val ToolboxStrings.notificationLedgerReasonNoAmount: String
    get() = if (chinese) "没有识别出金额" else "No amount recognized"

val ToolboxStrings.notificationLedgerDefaults: String
    get() = if (chinese) "记账默认设置" else "Entry defaults"

val ToolboxStrings.notificationLedgerDefaultsHint: String
    get() = if (chinese) "仅预填此应用的候选账目，保存前可修改。设置仅保存在本机。" else
        "Prefill suggestions from this app; you can edit before saving. Settings stay on this device."

val ToolboxStrings.notificationLedgerFollowLastAccount: String
    get() = if (chinese) "使用上次记账账户" else "Use last entry's account"

val ToolboxStrings.notificationLedgerAutoBook: String
    get() = if (chinese) "高置信支付自动入账" else "Auto-book confident payments"

val ToolboxStrings.notificationLedgerAutoBookHint: String
    get() = if (chinese) {
        "仅当通知写明付款/收款动词、金额与方向时直接入账；转账、低置信和疑似重复仍只给建议，入账后可在待记账列表撤销。"
    } else {
        "Books directly only when a notification names a payment verb, an amount and a direction. Transfers, low-confidence matches and possible duplicates stay suggestions; booked entries can be undone in the suggestion list."
    }

val ToolboxStrings.notificationLedgerAutoBooked: String
    get() = if (chinese) "已自动入账" else "Auto-booked"

val ToolboxStrings.notificationLedgerAutoBookUndo: String
    get() = if (chinese) "撤销" else "Undo"

val ToolboxStrings.notificationLedgerAutoBookUndoDone: String
    get() = if (chinese) "已撤销这笔自动记账" else "Auto-booked entry removed"

val ToolboxStrings.notificationLedgerAutoBookUndoFailed: String
    get() = if (chinese) "撤销失败，请重试" else "Could not undo; please retry"

val ToolboxStrings.notificationLedgerTransferOut: String
    get() = if (chinese) "转出" else "Transfer out"

val ToolboxStrings.notificationLedgerTransferIn: String
    get() = if (chinese) "转入" else "Transfer in"

val ToolboxStrings.notificationLedgerPaymentLabel: String
    get() = if (chinese) "商户/对方" else "Merchant / party"

val ToolboxStrings.notificationLedgerDuplicateTitle: String
    get() = if (chinese) "可能已记过这笔账" else "Possible duplicate entry"

val ToolboxStrings.notificationLedgerDuplicateHint: String
    get() = if (chinese) "同一账户前后 5 分钟内有金额、币种和收支类型相同的账目，请检查。" else
        "Entries in this account within 5 minutes have the same amount, currency and type. Please review."

val ToolboxStrings.notificationLedgerSaveAnyway: String
    get() = if (chinese) "仍然保存" else "Save anyway"

val ToolboxStrings.notificationLedgerAlreadyRecorded: String
    get() = if (chinese) "已记账，忽略通知" else "Already recorded; dismiss suggestion"

val ToolboxStrings.notificationLedgerReviewEntry: String
    get() = if (chinese) "返回编辑" else "Back to editor"

fun ToolboxStrings.notificationLedgerDuplicateCount(count: Int): String =
    if (chinese) "共 $count 笔相似账目，显示最近 5 笔" else "$count similar entries; showing the latest 5"
