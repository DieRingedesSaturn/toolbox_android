package com.example.toolbox.privacy

/**
 * One way to open a settings screen. Candidates are tried in order.
 * A normal app can start only exported activities; adb can start more.
 */
data class PrivacyShortcutCandidate(
    val packageName: String? = null,
    val className: String? = null,
    val action: String? = null,
    val dataUri: String? = null,
    /** When true, skip this candidate unless a Google account is visible. */
    val requiresGoogleAccount: Boolean = false,
)

data class ResolvedActivity(
    val packageName: String,
    val className: String,
    val exported: Boolean,
)

enum class PrivacyShortcutUnavailable {
    NOT_FOUND,
    NOT_EXPORTED,
}

/** Outcome of asking the launcher to pin a shortcut. */
enum class PrivacyPinResult {
    REQUESTED,
    UNSUPPORTED,
    PERMISSION_DENIED,
    FAILED,
}

data class PrivacyShortcutSelection(
    val candidate: PrivacyShortcutCandidate?,
    val activity: ResolvedActivity?,
    val unavailable: PrivacyShortcutUnavailable?,
) {
    val launchable: Boolean get() = activity != null && unavailable == null
}

data class PrivacyShortcut(
    val id: String,
    val titleEn: String,
    val titleZh: String,
    val descriptionEn: String,
    val descriptionZh: String,
    val candidates: List<PrivacyShortcutCandidate>,
) {
    fun title(chinese: Boolean): String = if (chinese) titleZh else titleEn

    fun description(chinese: Boolean): String = if (chinese) descriptionZh else descriptionEn
}

/**
 * Picks the first candidate that resolves to an exported activity.
 * Non-exported matches are remembered so the row can say why nothing opened.
 */
fun selectLaunchable(
    candidates: List<PrivacyShortcutCandidate>,
    lookup: (PrivacyShortcutCandidate) -> List<ResolvedActivity>,
): PrivacyShortcutSelection {
    var sawNonExported = false
    for (candidate in candidates) {
        val matches = lookup(candidate)
        val exported = matches.firstOrNull { it.exported }
        if (exported != null) {
            return PrivacyShortcutSelection(
                candidate = candidate,
                activity = exported,
                unavailable = null,
            )
        }
        if (matches.isNotEmpty()) sawNonExported = true
    }
    return PrivacyShortcutSelection(
        candidate = null,
        activity = null,
        unavailable = if (sawNonExported) {
            PrivacyShortcutUnavailable.NOT_EXPORTED
        } else {
            PrivacyShortcutUnavailable.NOT_FOUND
        },
    )
}

/** `adb shell am start` for the candidate, using the resolved component when known. */
fun privacyAdbCommand(
    candidate: PrivacyShortcutCandidate,
    activity: ResolvedActivity? = null,
): String {
    val command = StringBuilder("adb shell am start")
    if (!candidate.action.isNullOrBlank()) {
        command.append(" -a ").append(candidate.action)
    }
    val packageName = activity?.packageName ?: candidate.packageName
    val className = activity?.className ?: candidate.className
    if (!packageName.isNullOrBlank() && !className.isNullOrBlank()) {
        command.append(" -n ").append(packageName).append('/').append(className)
    }
    if (!candidate.dataUri.isNullOrBlank()) {
        command.append(" -d ").append(candidate.dataUri)
    }
    return command.toString()
}
