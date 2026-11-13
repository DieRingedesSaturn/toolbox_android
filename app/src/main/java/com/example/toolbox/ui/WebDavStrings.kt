package com.example.toolbox.ui

import com.example.toolbox.ledger.WebDavFailure

val ToolboxStrings.webDavBackupMenu: String
    get() = if (chinese) "WebDAV 备份与同步" else "WebDAV backup & sync"

val ToolboxStrings.webDavSyncNow: String
    get() = if (chinese) "立即同步" else "Sync now"

val ToolboxStrings.webDavHelpText: String
    get() = if (chinese) {
        "粘贴服务器上的 HTTPS 文件夹地址（例如 https://dav.jianguoyun.com/dav/；坚果云需使用「应用密码」而非登录密码）。同步文件会放在该地址下的 Toolbox/ledger/ 或 Toolbox/totp/ 子目录，不存在时自动创建；首次同步会读取原地址的旧文件并合并，旧文件保留。账本内容以备份密码加密，可从历史备份菜单恢复特定版本。账本写入 ledger.json、带日期的 ledger-backup-*.json 快照（仅保留最新 10 份）以及 ledger-backups.json 索引。"
    } else {
        "Paste a folder HTTPS URL on your server (e.g. https://dav.jianguoyun.com/dav/ — Jianguoyun requires an app password, not your login password). Sync files go in a Toolbox/ledger/ or Toolbox/totp/ subfolder, created when missing. The first sync also reads and merges legacy files from the configured folder, leaving them untouched. Ledger contents are encrypted with the backup passphrase; restore a specific version from Backup history. The ledger writes ledger.json, dated ledger-backup-*.json snapshots (newest 10 kept), and the ledger-backups.json index."
    }

val ToolboxStrings.webDavFolderUrlLabel: String
    get() = if (chinese) "文件夹地址（HTTPS）" else "Folder URL (HTTPS)"

val ToolboxStrings.webDavUsernameLabel: String
    get() = if (chinese) "用户名" else "Username"

val ToolboxStrings.webDavPasswordLabel: String
    get() = if (chinese) "密码 / 应用密码" else "Password / app password"

val ToolboxStrings.webDavShowPassword: String
    get() = if (chinese) "显示密码" else "Show password"

val ToolboxStrings.webDavHidePassword: String
    get() = if (chinese) "隐藏密码" else "Hide password"

val ToolboxStrings.webDavTestConnection: String
    get() = if (chinese) "测试连接" else "Test connection"

val ToolboxStrings.webDavConfirmTestTitle: String
    get() = if (chinese) "确认发送凭据？" else "Send credentials?"

fun ToolboxStrings.webDavConfirmTestMessage(url: String): String = if (chinese) {
    "连接测试会把用户名和密码发送到：\n$url\n\n请确认域名和地址正确。"
} else {
    "The connection test will send the username and password to:\n$url\n\nConfirm that the host and path are correct."
}

val ToolboxStrings.webDavTestOk: String
    get() = if (chinese) "连接成功" else "Connection OK"

val ToolboxStrings.webDavConfigSaved: String
    get() = if (chinese) "WebDAV 设置已保存" else "WebDAV settings saved"

val ToolboxStrings.webDavStatusTitle: String
    get() = if (chinese) "同步状态" else "Sync status"

val ToolboxStrings.webDavLastSyncLabel: String
    get() = if (chinese) "上次同步时间" else "Last sync"

val ToolboxStrings.webDavLastResultLabel: String
    get() = if (chinese) "上次结果" else "Last result"

val ToolboxStrings.webDavClearButton: String
    get() = if (chinese) "清除 WebDAV 设置" else "Clear WebDAV settings"

val ToolboxStrings.webDavClearConfirmText: String
    get() = if (chinese) {
        "仅清除本机保存的地址、用户名与密码，不会删除服务器上的文件。确定清除？"
    } else {
        "Only removes the locally saved URL, username and password; server files are untouched. Clear?"
    }

val ToolboxStrings.webDavClearAction: String
    get() = if (chinese) "清除" else "Clear"

fun ToolboxStrings.webDavError(failure: WebDavFailure, httpCode: Int? = null): String = when (failure) {
    WebDavFailure.INVALID_URL ->
        if (chinese) "请输入以 https:// 开头的文件夹地址" else "Enter a folder URL starting with https://"
    WebDavFailure.UNAUTHORIZED ->
        if (chinese) {
            "用户名或密码错误（坚果云请使用应用密码）"
        } else {
            "Wrong username or password (for Jianguoyun use an app password)"
        }
    WebDavFailure.FOLDER_MISSING ->
        if (chinese) "文件夹不存在，请先在服务器上创建" else "Folder missing on the server — create it first"
    WebDavFailure.PRECONDITION_FAILED ->
        if (chinese) {
            "远端文件正被其他设备修改，请稍后重试"
        } else {
            "Remote file is being changed by another device; try again"
        }
    WebDavFailure.VERSION_UNAVAILABLE ->
        if (chinese) {
            "服务器没有提供可用于安全同步的强 ETag，已停止写入"
        } else {
            "Server did not provide a strong ETag; sync stopped before writing"
        }
    WebDavFailure.HTTP_ERROR ->
        if (chinese) {
            "服务器返回错误${httpCode?.let { " $it" } ?: ""}"
        } else {
            "Server returned an error${httpCode?.let { " $it" } ?: ""}"
        }
    WebDavFailure.NETWORK ->
        if (chinese) "网络连接失败或超时" else "Network connection failed or timed out"
    WebDavFailure.TLS ->
        if (chinese) "HTTPS 证书校验失败" else "HTTPS certificate check failed"
    WebDavFailure.INVALID_REMOTE_FILE ->
        if (chinese) {
            "远端 ledger.json 无法解析，已停止同步以免覆盖"
        } else {
            "Remote ledger.json could not be parsed; sync stopped to avoid overwriting it"
        }
    WebDavFailure.NEWER_SCHEMA ->
        if (chinese) "远端数据来自更新版本的应用，请先升级" else "Remote data comes from a newer app version; update first"
    WebDavFailure.PASSWORD_UNAVAILABLE ->
        if (chinese) "已保存的密码无法读取，请重新输入" else "Saved password can't be read; please re-enter it"
}

fun ToolboxStrings.webDavSyncSuccess(
    total: Int,
    pulled: Int,
    pushed: Int,
    snapshotName: String?,
): String = if (chinese) {
    "同步完成：共 $total 条 · 拉取 $pulled · 推送 $pushed" +
        (snapshotName?.let { " · 快照 $it" } ?: "")
} else {
    "Sync complete: $total total · $pulled pulled · $pushed pushed" +
        (snapshotName?.let { " · snapshot $it" } ?: "")
}

fun ToolboxStrings.webDavSnapshotFailed(message: String): String =
    if (chinese) "快照写入失败：$message" else "Snapshot upload failed: $message"

val ToolboxStrings.webDavSettingsCardTitle: String
    get() = if (chinese) "WebDAV 备份" else "WebDAV Backup"

val ToolboxStrings.webDavSettingsCardHint: String
    get() = if (chinese) {
        "添加 HTTPS WebDAV 服务并为各工具勾选备份目标。"
    } else {
        "Add HTTPS WebDAV services and select backup targets per tool."
    }

val ToolboxStrings.webDavServiceNameLabel: String
    get() = if (chinese) "服务名称" else "Service name"

val ToolboxStrings.webDavServiceNameHint: String
    get() = if (chinese) "如：坚果云、Nextcloud" else "e.g. Nextcloud, NAS"

val ToolboxStrings.webDavAddServiceAction: String
    get() = if (chinese) "＋ 添加 WebDAV 服务" else "+ Add WebDAV service"

val ToolboxStrings.webDavEditServiceTitle: String
    get() = if (chinese) "编辑 WebDAV 服务" else "Edit WebDAV service"

val ToolboxStrings.webDavNewServiceTitle: String
    get() = if (chinese) "添加 WebDAV 服务" else "Add WebDAV service"

fun ToolboxStrings.webDavDeleteServiceConfirm(name: String): String =
    if (chinese) "删除「$name」？" else "Delete \"$name\"?"

val ToolboxStrings.webDavTargetLedgerLabel: String
    get() = if (chinese) "记账备份目标" else "Ledger targets"

val ToolboxStrings.webDavTargetTotpLabel: String
    get() = if (chinese) "令牌备份目标" else "TOTP targets"

val ToolboxStrings.webDavNoServiceHint: String
    get() = if (chinese) "暂未添加服务" else "No services configured"

val ToolboxStrings.webDavNoTargetSelectedToast: String
    get() = if (chinese) "请先在「设置 → WebDAV 备份」中添加并勾选目标" else "Add and select a WebDAV target in Settings first"

val ToolboxStrings.webDavOpenSettingsAction: String
    get() = if (chinese) "设置 WebDAV" else "WebDAV Settings"

fun ToolboxStrings.webDavMultiSyncSummary(okCount: Int, totalCount: Int, detail: String): String =
    if (chinese) {
        "已同步 $okCount/$totalCount：$detail"
    } else {
        "Synced $okCount/$totalCount: $detail"
    }
