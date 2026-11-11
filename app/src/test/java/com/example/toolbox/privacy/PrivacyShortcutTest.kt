package com.example.toolbox.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyShortcutTest {
    private val first = PrivacyShortcutCandidate(
        packageName = "com.google.android.gms",
        className = "com.google.android.gms.app.settings.GoogleSettingsLink",
    )
    private val second = PrivacyShortcutCandidate(
        action = "com.google.android.gms.location.settings.LOCATION_ACCURACY",
    )

    @Test
    fun usesTheFirstExportedActivity() {
        val selection = selectLaunchable(listOf(first, second)) {
            listOf(ResolvedActivity("com.google.android.gms", "com.google.android.gms.app.settings.GoogleSettingsLink", exported = true))
        }
        assertTrue(selection.launchable)
        assertEquals(first, selection.candidate)
        assertEquals("com.google.android.gms.app.settings.GoogleSettingsLink", selection.activity?.className)
        assertNull(selection.unavailable)
    }

    @Test
    fun skipsANonExportedComponentAndUsesTheNextCandidate() {
        val seen = mutableListOf<PrivacyShortcutCandidate>()
        val selection = selectLaunchable(listOf(first, second)) { candidate ->
            seen += candidate
            if (candidate == first) {
                listOf(
                    ResolvedActivity(
                        "com.google.android.gms",
                        "com.google.android.gms.app.settings.GoogleSettingsActivity",
                        exported = false,
                    ),
                )
            } else {
                listOf(
                    ResolvedActivity(
                        "com.google.android.gms",
                        "com.google.android.gms.location.settings.LocationAccuracyActivity",
                        exported = true,
                    ),
                )
            }
        }
        assertEquals(listOf(first, second), seen)
        assertEquals(second, selection.candidate)
        assertEquals(
            "com.google.android.gms.location.settings.LocationAccuracyActivity",
            selection.activity?.className,
        )
        assertNull(selection.unavailable)
    }

    @Test
    fun reportsNotFoundWhenNothingResolves() {
        val selection = selectLaunchable(listOf(first, second)) { emptyList() }
        assertFalse(selection.launchable)
        assertEquals(PrivacyShortcutUnavailable.NOT_FOUND, selection.unavailable)
        assertNull(selection.activity)
    }

    @Test
    fun reportsNotExportedWhenEveryMatchIsPrivate() {
        val selection = selectLaunchable(listOf(first)) {
            listOf(ResolvedActivity("com.google.android.gms", first.className!!, exported = false))
        }
        assertEquals(PrivacyShortcutUnavailable.NOT_EXPORTED, selection.unavailable)
    }

    @Test
    fun prefersAnExportedMatchInsideOneCandidate() {
        val selection = selectLaunchable(listOf(second)) {
            listOf(
                ResolvedActivity("com.example.hidden", "Hidden", exported = false),
                ResolvedActivity("com.google.android.gms", "Exported", exported = true),
            )
        }
        assertEquals("Exported", selection.activity?.className)
    }

    @Test
    fun adbCommandIncludesActionComponentAndData() {
        val candidate = PrivacyShortcutCandidate(
            action = "android.settings.APPLICATION_DETAILS_SETTINGS",
            dataUri = "package:com.google.android.gms",
        )
        val activity = ResolvedActivity(
            packageName = "com.android.settings",
            className = "com.android.settings.applications.InstalledAppDetails",
            exported = true,
        )
        assertEquals(
            "adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS " +
                "-n com.android.settings/com.android.settings.applications.InstalledAppDetails " +
                "-d package:com.google.android.gms",
            privacyAdbCommand(candidate, activity),
        )
    }

    @Test
    fun adbCommandKeepsTheRequestedComponentWhenNothingResolved() {
        assertEquals(
            "adb shell am start -n com.google.android.gms/com.google.android.gms.app.settings.GoogleSettingsLink",
            privacyAdbCommand(first),
        )
    }

    @Test
    fun catalogTriesGoogleSettingsLinkBeforeLaterFallbacks() {
        val google = PrivacyShortcutCatalog.entries.first { it.id == "google_settings" }
        assertEquals(
            "com.google.android.gms.app.settings.GoogleSettingsLink",
            google.candidates.first().className,
        )
        assertTrue(google.candidates.any { it.className?.endsWith("GoogleSettingsIALink") == true })
        val accuracy = PrivacyShortcutCatalog.entries.first { it.id == "location_accuracy" }
        assertEquals(
            "com.google.android.gms.location.settings.LOCATION_ACCURACY",
            accuracy.candidates.first().action,
        )
        val verification = PrivacyShortcutCatalog.entries.first { it.id == "phone_number_verification" }
        assertEquals(
            "com.google.android.gms.constellation.ui.ConstellationSettingsActivity",
            verification.candidates.first().className,
        )
    }
}
