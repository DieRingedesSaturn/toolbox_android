package com.example.toolbox.ui

import com.example.toolbox.ledger.BackupRecoveryStore
import com.example.toolbox.ledger.BackupTool
import com.example.toolbox.monitor.MonitorMetric
import com.example.toolbox.totp.TotpVaultCrypto
import com.example.toolbox.totp.TotpVaultException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppSettingsBackupTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val pass = "settings-test-passphrase"

    @Before
    fun reset() {
        context.getSharedPreferences("toolbox_preferences", 0).edit().clear().commit()
    }

    @Test
    fun onlyAllowedPreferencesTravelAndRestorePreservesUnrelatedKeys() {
        val store = AppPreferences(context)
        store.saveLanguage(AppLanguage.CHINESE)
        store.saveThemeMode(ThemeMode.DARK)
        store.saveMonitorMetrics(setOf(MonitorMetric.entries.first()))
        store.saveMonitorOverlayOpacity(0.75f)
        context.getSharedPreferences("toolbox_preferences", 0).edit().putString("password", "private").commit()
        val snapshot = store.backupSettings()
        assertFalse(snapshot.has("password"))
        val encrypted = AppSettingsBackup.encode(snapshot.put("webdav_password", "never travel"), pass)
        assertFalse(AppSettingsBackup.decode(encrypted, pass).has("webdav_password"))
        assertFalse(encrypted.contains("CHINESE"))
        store.saveLanguage(AppLanguage.ENGLISH)
        store.saveThemeMode(ThemeMode.LIGHT)
        AppSettingsBackup.restore(context, encrypted, pass)
        assertEquals(AppLanguage.CHINESE, store.language())
        assertEquals(ThemeMode.DARK, store.themeMode())
        assertEquals(setOf(MonitorMetric.entries.first()), store.monitorMetrics())
        assertEquals(0.75f, store.monitorOverlayOpacity())
        assertEquals("private", context.getSharedPreferences("toolbox_preferences", 0).getString("password", null))
        val checkpoint = BackupRecoveryStore(context, BackupTool.SETTINGS).read()
        assertEquals("ENGLISH", AppSettingsBackup.decode(checkpoint, pass).getString("language"))
    }

    @Test
    fun wrongPasswordAndCrossToolEnvelopesCannotChangeSettings() {
        val store = AppPreferences(context)
        store.saveLanguage(AppLanguage.CHINESE)
        val encrypted = AppSettingsBackup.encode(store.backupSettings(), pass)
        store.saveLanguage(AppLanguage.ENGLISH)
        assertThrows(TotpVaultException::class.java) { AppSettingsBackup.restore(context, encrypted, "wrong-password") }
        assertEquals(AppLanguage.ENGLISH, store.language())
        assertThrows(TotpVaultException::class.java) { TotpVaultCrypto.decryptVault(encrypted, pass) }
    }

    @Test
    fun invalidKnownValueFailsBeforeAnyPreferenceChanges() {
        val store = AppPreferences(context)
        store.saveLanguage(AppLanguage.CHINESE)
        val invalid = JSONObject().put("language", "ENGLISH").put("monitor_overlay_fixed", "not a boolean")
        assertThrows(IllegalArgumentException::class.java) { store.restoreSettings(invalid) }
        assertEquals(AppLanguage.CHINESE, store.language())
        assertTrue(store.backupSettings().has("language"))
    }
}
