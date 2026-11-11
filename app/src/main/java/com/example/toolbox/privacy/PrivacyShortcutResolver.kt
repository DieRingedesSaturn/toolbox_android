package com.example.toolbox.privacy

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.example.toolbox.R

data class PrivacyShortcutRow(
    val shortcut: PrivacyShortcut,
    val selection: PrivacyShortcutSelection,
)

fun loadPrivacyRows(context: Context): List<PrivacyShortcutRow> {
    val appContext = context.applicationContext
    val lookup = PackageManagerShortcutLookup(
        packageManager = appContext.packageManager,
        hasGoogleAccount = firstGoogleAccount(appContext) != null,
    )
    return PrivacyShortcutCatalog.entries.map { shortcut ->
        PrivacyShortcutRow(
            shortcut = shortcut,
            selection = selectLaunchable(shortcut.candidates, lookup::lookup),
        )
    }
}

/**
 * Explicit components use [PackageManager.getActivityInfo], which can see a
 * non-exported activity when the package is visible. Actions use
 * [PackageManager.queryIntentActivities], which only returns activities this
 * app is allowed to start.
 */
internal class PackageManagerShortcutLookup(
    private val packageManager: PackageManager,
    private val hasGoogleAccount: Boolean,
) {
    fun lookup(candidate: PrivacyShortcutCandidate): List<ResolvedActivity> {
        if (candidate.requiresGoogleAccount && !hasGoogleAccount) return emptyList()
        val packageName = candidate.packageName
        val className = candidate.className
        return if (packageName != null && className != null) {
            lookupComponent(packageName, className)
        } else {
            lookupIntent(candidate)
        }
    }

    private fun lookupComponent(packageName: String, className: String): List<ResolvedActivity> {
        val info = try {
            packageManager.getActivityInfo(ComponentName(packageName, className), 0)
        } catch (_: NameNotFoundException) {
            return emptyList()
        } catch (_: SecurityException) {
            return emptyList()
        }
        if (!info.enabled) return emptyList()
        return listOf(
            ResolvedActivity(
                packageName = packageName,
                className = className,
                exported = info.exported,
            ),
        )
    }

    private fun lookupIntent(candidate: PrivacyShortcutCandidate): List<ResolvedActivity> {
        val matches = try {
            packageManager.queryIntentActivities(candidateIntent(candidate), PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: SecurityException) {
            return emptyList()
        }
        return matches.mapNotNull { resolve ->
            if (!resolve.activityInfo.enabled) return@mapNotNull null
            ResolvedActivity(
                packageName = resolve.activityInfo.packageName,
                className = resolve.activityInfo.name,
                exported = resolve.activityInfo.exported,
            )
        }
    }
}

fun launchPrivacyShortcut(context: Context, row: PrivacyShortcutRow): Result<Unit> {
    val candidate = row.selection.candidate ?: return Result.failure(ActivityNotFoundException())
    val activity = row.selection.activity ?: return Result.failure(ActivityNotFoundException())
    val intent = privacyLaunchIntent(
        candidate = candidate,
        activity = activity,
        googleAccount = if (candidate.requiresGoogleAccount) firstGoogleAccount(context) else null,
    )
    return try {
        context.startActivity(intent)
        Result.success(Unit)
    } catch (error: ActivityNotFoundException) {
        Result.failure(error)
    } catch (error: SecurityException) {
        Result.failure(error)
    }
}

fun requestPrivacyPinShortcut(context: Context, row: PrivacyShortcutRow, label: String): Boolean {
    val candidate = row.selection.candidate ?: return false
    val activity = row.selection.activity ?: return false
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
    val intent = privacyLaunchIntent(
        candidate = candidate,
        activity = activity,
        googleAccount = if (candidate.requiresGoogleAccount) firstGoogleAccount(context) else null,
    ).apply {
        if (action.isNullOrBlank()) action = Intent.ACTION_VIEW
    }
    val shortcut = ShortcutInfoCompat.Builder(context, "privacy-${row.shortcut.id}")
        .setShortLabel(label)
        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_module_privacy))
        .setIntent(intent)
        .build()
    return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
}

internal fun privacyLaunchIntent(
    candidate: PrivacyShortcutCandidate,
    activity: ResolvedActivity,
    googleAccount: Account?,
): Intent = Intent().apply {
    action = candidate.action?.takeIf { it.isNotBlank() } ?: Intent.ACTION_VIEW
    component = ComponentName(activity.packageName, activity.className)
    if (!candidate.dataUri.isNullOrBlank()) {
        data = candidate.dataUri.toUri()
    }
    if (candidate.requiresGoogleAccount && googleAccount != null) {
        putExtra("account", googleAccount)
    }
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

internal fun firstGoogleAccount(context: Context): Account? = try {
    AccountManager.get(context).getAccountsByType(GOOGLE_ACCOUNT_TYPE).firstOrNull()
} catch (_: SecurityException) {
    null
}

private fun candidateIntent(candidate: PrivacyShortcutCandidate): Intent = Intent(candidate.action).apply {
    addCategory(Intent.CATEGORY_DEFAULT)
    if (!candidate.dataUri.isNullOrBlank()) {
        data = Uri.parse(candidate.dataUri)
    }
}

private const val GOOGLE_ACCOUNT_TYPE = "com.google"
