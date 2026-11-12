package com.example.toolbox.ledger

import android.content.Context
import android.util.AtomicFile
import java.io.File

/** One encrypted local checkpoint before a deliberate restore; excluded from Android backup. */
class BackupRecoveryStore(context: Context, tool: BackupTool) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "${tool.key}-before-restore.json"))
    fun exists(): Boolean = file.baseFile.exists()
    fun read(): String = file.openRead().use { it.readBytes().toString(Charsets.UTF_8) }
    fun save(encryptedBody: String) {
        val stream = file.startWrite()
        try {
            stream.write(encryptedBody.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (failure: Throwable) {
            file.failWrite(stream)
            throw failure
        }
    }
}
