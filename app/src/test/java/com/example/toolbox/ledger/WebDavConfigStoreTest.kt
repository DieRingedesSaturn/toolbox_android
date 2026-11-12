package com.example.toolbox.ledger

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WebDavConfigStoreTest {
    @Test
    fun unboundLedgerDoesNotFallBackToAnotherToolsService() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("ledger_webdav", 0).edit().clear().commit()
        val store = WebDavConfigStore(context)
        // No password avoids invoking a real device Keystore in this JVM test.
        store.upsertService(WebDavServiceProfile(id = "only-totp", name = "TOTP",
            folderUrl = "https://example.invalid/dav/", username = "user", password = ""),
            defaultTools = setOf(BackupTool.TOTP))
        assertEquals(setOf("only-totp"), store.selectedServiceIds(BackupTool.TOTP))
        assertEquals(emptySet<String>(), store.selectedServiceIds(BackupTool.LEDGER))
        assertEquals(WebDavConfig("", "", ""), store.load())
    }
}
