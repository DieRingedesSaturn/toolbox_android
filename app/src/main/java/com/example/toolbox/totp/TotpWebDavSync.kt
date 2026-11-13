package com.example.toolbox.totp

import com.example.toolbox.ledger.BackupTool
import com.example.toolbox.ledger.FileMerge
import com.example.toolbox.ledger.FileSyncResult
import com.example.toolbox.ledger.WholeFileWebDavSync
import com.example.toolbox.ledger.WebDavServiceProfile
import com.example.toolbox.ledger.WebDavClient
import com.example.toolbox.ledger.WebDavFailure
import java.time.ZoneId

data class TotpWebDavSyncResult(
    val totalTokens: Int,
    val pulledCount: Int,
    val pushedCount: Int,
    val snapshotName: String?,
    val snapshotFailure: WebDavFailure? = null,
)

class TotpWebDavSync(
    private val client: WebDavClient,
    private val passphrase: String,
    private val loadLocal: () -> List<TotpEntry>,
    private val applyMerged: (List<TotpEntry>) -> Unit,
    private val iterations: Int = TotpVaultCrypto.DEFAULT_ITERATIONS,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Reads a legacy file in the parent folder on the first Toolbox sync. */
    private val legacyClient: WebDavClient? = null,
    private val legacyClients: List<WebDavClient> = listOfNotNull(legacyClient),
) {

    internal val engine = WholeFileWebDavSync(
        client = client,
        tool = BackupTool.TOTP,
        fileName = VAULT_FILE,
        legacyClients = legacyClients,
        empty = { emptyList<TotpEntry>() },
        decode = { TotpVaultCrypto.decryptVault(it, passphrase) },
        encode = { entries, now -> TotpVaultCrypto.encryptVault(entries, passphrase, iterations, now) },
        merge = { local, remote ->
            val merged = TotpSyncMerge.merge(local, remote)
            FileMerge(merged.entries, merged.pulledCount, merged.pushedCount)
        },
        zone = zone,
    )

    fun pull(): List<TotpEntry>? = engine.read().payload

    fun sync(nowMillis: Long = System.currentTimeMillis()): TotpWebDavSyncResult =
        engine.sync(loadLocal, applyMerged, nowMillis).toTotpResult()

    companion object {
        const val VAULT_FILE = "totp-vault.json"

        suspend fun syncTargets(
            services: List<WebDavServiceProfile>,
            passphrase: String,
            loadLocal: () -> List<TotpEntry>,
            applyMerged: (List<TotpEntry>) -> Unit,
            nowMillis: Long = System.currentTimeMillis(),
        ): List<Result<TotpWebDavSyncResult>> {
            val engines = services.map { service ->
                TotpWebDavSync(service.syncClient(BackupTool.TOTP), passphrase,
                    loadLocal = { emptyList() }, applyMerged = {},
                    legacyClients = service.legacyClients(BackupTool.TOTP)).engine
            }
            return WholeFileWebDavSync.syncTargets(engines, loadLocal, applyMerged, nowMillis)
                .map { result -> result.map { it.toTotpResult() } }
        }
    }
}

private fun FileSyncResult<List<TotpEntry>>.toTotpResult() = TotpWebDavSyncResult(
    totalTokens = payload.count { it.deletedAtMillis == null },
    pulledCount = pulled,
    pushedCount = pushed,
    snapshotName = snapshotName,
    snapshotFailure = snapshotFailure,
)
