package com.example.toolbox.ui


val ToolboxStrings.totpSearchHint: String
    get() = if (chinese) "搜索服务商或账号" else "Search issuer or account"

val ToolboxStrings.totpEmptyHint: String
    get() = if (chinese) "暂无令牌，可扫码或手动添加。" else "No tokens yet. Scan a QR code or add manually."

val ToolboxStrings.totpAddShort: String
    get() = if (chinese) "添加" else "Add"

val ToolboxStrings.totpScanQrAction: String
    get() = if (chinese) "扫码添加" else "Scan QR"

val ToolboxStrings.totpManualAddAction: String
    get() = if (chinese) "手动编辑" else "Manual input"

val ToolboxStrings.totpPickQrImageAction: String
    get() = if (chinese) "相册选图" else "From image"

val ToolboxStrings.totpScanDialogTitle: String
    get() = if (chinese) "扫码添加令牌" else "Scan QR Code"

val ToolboxStrings.totpScanDialogHint: String
    get() = if (chinese) "对准二维码自动添加，或从相册选择截图。" else "Point at a QR code or pick a screenshot."

val ToolboxStrings.totpCameraPermissionDenied: String
    get() = if (chinese) "未授予相机权限，可使用「相册选图」" else "Camera permission denied; use 'From image'"

val ToolboxStrings.totpQrNoCodeFound: String
    get() = if (chinese) "未识别到有效二维码" else "No valid QR code found"

fun ToolboxStrings.totpQrAddedToast(title: String): String =
    if (chinese) "已添加：$title" else "Added: $title"

val ToolboxStrings.totpNewTitle: String
    get() = if (chinese) "添加令牌" else "Add token"

val ToolboxStrings.totpEditTitle: String
    get() = if (chinese) "编辑令牌" else "Edit token"

val ToolboxStrings.totpUriFieldLabel: String
    get() = if (chinese) "otpauth:// 链接" else "otpauth:// URI"

val ToolboxStrings.totpShowUri: String
    get() = if (chinese) "显示链接" else "Show URI"

val ToolboxStrings.totpHideUri: String
    get() = if (chinese) "隐藏链接" else "Hide URI"

val ToolboxStrings.totpParseUriButton: String
    get() = if (chinese) "粘贴并解析链接" else "Paste & parse URI"

val ToolboxStrings.totpUriParsedToast: String
    get() = if (chinese) "已解析填入" else "Parsed from URI"

val ToolboxStrings.totpUriInvalidError: String
    get() = if (chinese) "无效的 otpauth:// 链接" else "Invalid otpauth:// URI"

val ToolboxStrings.totpIssuerLabel: String
    get() = if (chinese) "服务商" else "Service / Issuer"

val ToolboxStrings.totpIssuerHint: String
    get() = if (chinese) "如 Cloudflare、GitHub" else "e.g. Cloudflare, GitHub"

val ToolboxStrings.totpAccountLabel: String
    get() = if (chinese) "账号" else "Account"

val ToolboxStrings.totpAccountHint: String
    get() = if (chinese) "如 user@example.com" else "e.g. user@example.com"

val ToolboxStrings.totpSecretLabel: String
    get() = if (chinese) "Base32 密钥" else "Base32 secret"

val ToolboxStrings.totpSecretInvalidError: String
    get() = if (chinese) "密钥格式无效（仅限 A-Z、2-7）" else "Invalid Base32 secret (A-Z, 2-7)"

val ToolboxStrings.totpAdvancedOptionsLabel: String
    get() = if (chinese) "高级选项" else "Advanced"

val ToolboxStrings.totpAlgorithmLabel: String
    get() = if (chinese) "算法" else "Algorithm"

val ToolboxStrings.totpDigitsLabel: String
    get() = if (chinese) "位数" else "Digits"

val ToolboxStrings.totpPeriodLabel: String
    get() = if (chinese) "周期" else "Period"

val ToolboxStrings.totpCopyCodeLabel: String
    get() = if (chinese) "已复制验证码" else "Code copied"

val ToolboxStrings.totpCopyUriAction: String
    get() = if (chinese) "复制链接" else "Copy URI"

val ToolboxStrings.totpShowAllCodes: String
    get() = if (chinese) "显示全部验证码" else "Show all codes"

val ToolboxStrings.totpHideAllCodes: String
    get() = if (chinese) "隐藏全部验证码" else "Hide all codes"

fun ToolboxStrings.totpDeleteConfirm(name: String): String =
    if (chinese) "删除令牌「$name」？" else "Delete token \"$name\"?"

val ToolboxStrings.totpExportEncryptedMenu: String
    get() = if (chinese) "导出加密备份" else "Export backup"

val ToolboxStrings.totpImportEncryptedMenu: String
    get() = if (chinese) "导入加密备份" else "Import backup"

val ToolboxStrings.totpVaultPassphraseTitle: String
    get() = if (chinese) "备份加密密码" else "Backup Passphrase"

val ToolboxStrings.totpVaultPassphraseHint: String
    get() = if (chinese) {
        "仅用于 WebDAV 同步与导出备份文件时的端到端加密（本机存储已由系统硬件密钥自动加密）。"
    } else {
        "Used for end-to-end encryption when syncing to WebDAV or exporting backup files."
    }

val ToolboxStrings.totpVaultPassphraseLabel: String
    get() = if (chinese) "备份密码" else "Backup passphrase"

val ToolboxStrings.totpVaultPassphraseRemember: String
    get() = if (chinese) "在本机记住此密码" else "Remember on this device"

val ToolboxStrings.totpVaultPassphraseTooShort: String
    get() = if (chinese) "密码至少 8 位" else "At least 8 characters"

val ToolboxStrings.totpVaultWrongPassphrase: String
    get() = if (chinese) "备份密码错误或文件已损坏" else "Wrong passphrase or corrupted file"

val ToolboxStrings.totpLocalVaultUnreadable: String
    get() = if (chinese) {
        "无法读取本机令牌（密钥丢失或文件损坏）。已有内容未被改写，同步不会覆盖它们。若要用加密备份替换，请在导入时选择「替换」。"
    } else {
        "Couldn't read saved tokens (missing keystore key or damaged file). They were left unchanged, and sync will not overwrite them. Import → Replace can overwrite them with an encrypted backup."
    }

fun ToolboxStrings.totpSyncSuccess(total: Int, pulled: Int, pushed: Int): String =
    if (chinese) {
        "共 $total 项（拉取 $pulled · 推送 $pushed）"
    } else {
        "$total tokens (pulled $pulled · pushed $pushed)"
    }
