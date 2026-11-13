package com.example.toolbox.ui

import com.example.toolbox.ledger.BackupFailure

val ToolboxStrings.backupPasswordLabel: String
    get() = if (chinese) "备份加密密码（非 WebDAV 登录密码）" else "Backup passphrase (not WebDAV login password)"

val ToolboxStrings.settingsBackupTitle: String
    get() = if (chinese) "软件设置备份" else "App settings backup"

val ToolboxStrings.settingsBackupHint: String
    get() = if (chinese) "手动加密备份到列表首个 WebDAV 的 Toolbox/settings/，保留 10 个历史版本。仅包含语言、主题、排序、监控等通用偏好；不包含服务凭据、账本、令牌或系统权限。密码需另外妥善保存。" else "Manually back up encrypted general preferences to Toolbox/settings/ on the first listed WebDAV service; keeps 10 versions. Includes language, theme, ordering and monitor preferences, without service credentials, ledger records, tokens or system permissions. Keep the passphrase separately."

fun ToolboxStrings.settingsBackupTarget(name: String): String = if (chinese) "备份服务：$name（列表首个）" else "Backup service: $name (first listed)"

val ToolboxStrings.settingsBackupNow: String
    get() = if (chinese) "立即备份设置" else "Back up settings now"

val ToolboxStrings.settingsBackupDone: String
    get() = if (chinese) "设置备份已保存" else "Settings backup saved"

val ToolboxStrings.backupPassphraseLoading: String
    get() = if (chinese) "正在读取已保存的备份密码…" else "Reading the saved backup passphrase…"

val ToolboxStrings.backupPassphraseLoadFailed: String
    get() = if (chinese) "无法读取已保存的备份密码，可重试或重新输入。" else "Could not read the saved backup passphrase. Retry or enter it again."

val ToolboxStrings.backupPassphraseSaveFailed: String
    get() = if (chinese) "备份已完成，但密码未能保存到本机，下次需要重新输入。" else "Backup finished, but the passphrase could not be saved on this device. You will need to enter it again next time."

val ToolboxStrings.retryAction: String
    get() = if (chinese) "重试" else "Retry"

val ToolboxStrings.backupPasswordTitle: String
    get() = if (chinese) "备份加密密码" else "Backup encryption passphrase"

val ToolboxStrings.backupPasswordHint: String
    get() = if (chinese) "至少 8 个字符，用于客户端加密；已有加密备份须输入原密码，忘记后无法恢复。更改输入的密码不会重加密旧版本，直接用新密码同步旧备份会失败。恢复旧明文备份时也需设置密码，以保护恢复前副本。" else "At least 8 characters; encrypts backups on this device. Use the original passphrase for existing encrypted backups. Lost passphrases cannot be recovered. Changing this input does not re-encrypt older versions; syncing an existing vault with a new passphrase fails. A passphrase is also needed to protect the checkpoint when restoring legacy plaintext."

val ToolboxStrings.backupDecryptFailed: String
    get() = if (chinese) "备份密码错误，或文件已损坏、格式不兼容。" else "Incorrect backup passphrase, damaged file, or incompatible format."

val ToolboxStrings.backupTimeUnknown: String
    get() = if (chinese) "时间未知" else "Time unavailable"

val ToolboxStrings.backupHistoryTitle: String
    get() = if (chinese) "历史备份与恢复" else "Backup history and restore"

val ToolboxStrings.backupBeforeRestore: String
    get() = if (chinese) "本机恢复前副本" else "Local pre-restore checkpoint"

val ToolboxStrings.backupLegacy: String
    get() = if (chinese) "旧目录" else "Legacy folder"

val ToolboxStrings.backupHistoryFailed: String
    get() = if (chinese) "读取或恢复失败，请检查密码与网络；尚未确认的恢复不会修改本机数据。" else "Read or restore failed. Check the passphrase and connection. Unconfirmed restores leave local data unchanged."

val ToolboxStrings.backupHistoryEmpty: String
    get() = if (chinese) "没有可用的历史版本" else "No backup versions found"

val ToolboxStrings.backupRestoreAction: String
    get() = if (chinese) "恢复" else "Restore"

fun ToolboxStrings.backupRestoreWarning(count: Int): String = if (chinese) {
    "此版本包含 $count 条有效记录。恢复前会保存加密的本机副本；恢复将替换当前数据，并生成新修订。下次手动同步会把这次恢复传播到所选服务和其他设备。"
} else "This version contains $count active records. An encrypted local checkpoint is saved first. Restore replaces current data as a new revision; the next manual sync propagates it to selected services and other devices."

val ToolboxStrings.settingsBackupSummary: String
    get() = if (chinese) "WebDAV 服务与设置备份" else "WebDAV services and settings backup"

val ToolboxStrings.perMonthUnit: String
    get() = if (chinese) "/月" else "/mo"

val ToolboxStrings.backupExportJson: String
    get() = if (chinese) "导出备份（JSON）" else "Export backup (JSON)"

val ToolboxStrings.backupExportCsv: String
    get() = if (chinese) "导出表格（CSV）" else "Export spreadsheet (CSV)"

val ToolboxStrings.backupImport: String
    get() = if (chinese) "从文件导入" else "Import from file"

val ToolboxStrings.backupImportTitle: String
    get() = if (chinese) "导入备份" else "Import backup"

val ToolboxStrings.backupFileEntriesLabel: String
    get() = if (chinese) "文件内记录" else "Entries in file"

val ToolboxStrings.backupDateRangeLabel: String
    get() = if (chinese) "日期范围" else "Date range"

val ToolboxStrings.backupExportedAtLabel: String
    get() = if (chinese) "导出时间" else "Exported at"

val ToolboxStrings.backupNoRecords: String
    get() = if (chinese) "无记录" else "No records"

val ToolboxStrings.backupMergeAction: String
    get() = if (chinese) "合并" else "Merge"

val ToolboxStrings.backupReplaceAction: String
    get() = if (chinese) "替换…" else "Replace…"

val ToolboxStrings.backupReplaceConfirmTitle: String
    get() = if (chinese) "确认替换为备份内容" else "Replace with backup?"

val ToolboxStrings.backupExportFailed: String
    get() = if (chinese) "导出失败" else "Export failed"

val ToolboxStrings.backupInvalidFile: String
    get() = if (chinese) "文件不是有效的 Toolbox 账本备份" else "Not a valid Toolbox ledger backup"

val ToolboxStrings.backupNewerSchema: String
    get() = if (chinese) "备份来自更新版本的应用，请先升级" else "Backup comes from a newer app version; update first"

val ToolboxStrings.backupTooLarge: String
    get() = if (chinese) "文件过大（超过 20 MB）" else "File too large (over 20 MB)"

val ToolboxStrings.backupReadFailed: String
    get() = if (chinese) "无法读取文件" else "Could not read the file"

fun ToolboxStrings.backupExported(count: Int): String =
    if (chinese) "已导出 $count 条记录" else "Exported $count entries"

val ToolboxStrings.backupReminderNever: String
    get() = if (chinese) {
        "账本还没有备份过。系统自动备份已关闭，卸载或换手机前请先备份。"
    } else {
        "This ledger has never been backed up. System backup is off, so back up before uninstalling or switching phones."
    }

fun ToolboxStrings.backupReminderDays(days: Long): String =
    if (chinese) {
        "已经 $days 天没有备份了，这期间账本有改动。"
    } else {
        "No backup for $days days, and the ledger has changed since."
    }

val ToolboxStrings.backupReminderExport: String
    get() = if (chinese) "导出 JSON" else "Export JSON"

val ToolboxStrings.backupReminderWebDav: String
    get() = if (chinese) "WebDAV 同步" else "WebDAV sync"

val ToolboxStrings.backupReminderLater: String
    get() = if (chinese) "稍后" else "Later"

fun ToolboxStrings.backupMergePreview(added: Int, updated: Int, unchanged: Int): String =
    if (chinese) {
        "合并：新增 $added，更新 $updated，不变 $unchanged"
    } else {
        "Merge: $added added, $updated updated, $unchanged unchanged"
    }

fun ToolboxStrings.backupMergedDone(added: Int, updated: Int): String =
    if (chinese) "已合并：新增 $added，更新 $updated" else "Merged: $added added, $updated updated"

fun ToolboxStrings.backupReplaceConfirmBody(kept: Int, deleted: Int): String = if (chinese) {
    "本地账本将与备份文件完全一致：保留 $kept 条，删除本地 $deleted 条。WebDAV 同步后其他设备也会随之更新。"
} else {
    "Your local ledger will match the backup exactly: $kept entries kept, $deleted local entries deleted. Other devices will pick this up on the next WebDAV sync."
}

fun ToolboxStrings.backupReplacedDone(kept: Int, deleted: Int): String =
    if (chinese) {
        "已替换：保留 $kept 条，删除 $deleted 条"
    } else {
        "Replaced: $kept kept, $deleted deleted"
    }

fun ToolboxStrings.backupError(failure: BackupFailure): String = when (failure) {
    BackupFailure.INVALID_FILE -> backupInvalidFile
    BackupFailure.NEWER_SCHEMA -> backupNewerSchema
    BackupFailure.TOO_LARGE -> backupTooLarge
}

fun ToolboxStrings.settingsRestoreWarning(count: Int): String = if (chinese) {
    "此版本包含 $count 项通用偏好。确认后先保存加密的恢复前副本，再替换通用设置。外观立即生效，其他设置重新打开对应页面后生效；WebDAV 配置、账本、令牌和权限不受影响。"
} else "This version contains $count general preferences. An encrypted checkpoint is saved before replacement. Appearance changes immediately; reopen other screens to load their settings. WebDAV configuration, ledger records, tokens and permissions remain unchanged."
