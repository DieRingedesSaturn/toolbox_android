package com.example.toolbox.ledger

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.net.URI
import java.net.URLEncoder
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

enum class BackupTool(val key: String) {
    LEDGER("ledger"),
    TOTP("totp"),
}

data class WebDavServiceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val folderUrl: String,
    val username: String,
    val password: String,
) {
    val isComplete: Boolean
        get() = folderUrl.isNotBlank() && username.isNotBlank() && password.isNotEmpty()

    fun toWebDavConfig(): WebDavConfig = WebDavConfig(
        folderUrl = folderUrl,
        username = username,
        password = password,
    )

    fun syncClient(tool: BackupTool): WebDavClient = WebDavClient(
        WebDavPaths.toolFolderUrl(folderUrl, tool), username, password,
        parentFolderUrl = WebDavPaths.toolboxFolderUrl(folderUrl),
    )

    fun connectionClient(): WebDavClient = WebDavClient(
        WebDavPaths.toolboxFolderUrl(folderUrl), username, password,
    )

    fun legacyClients(tool: BackupTool): List<WebDavClient> =
        WebDavPaths.legacyFolderUrls(folderUrl, tool).map { WebDavClient(it, username, password) }

}

data class WebDavConfig(
    val folderUrl: String,
    val username: String,
    val password: String,
) {
    val isComplete: Boolean
        get() = folderUrl.isNotBlank() && username.isNotBlank() && password.isNotEmpty()

    fun syncClient(): WebDavClient = WebDavClient(
        WebDavPaths.toolFolderUrl(folderUrl, BackupTool.LEDGER), username, password,
        parentFolderUrl = WebDavPaths.toolboxFolderUrl(folderUrl),
    )

    fun legacyClients(): List<WebDavClient> =
        WebDavPaths.legacyFolderUrls(folderUrl, BackupTool.LEDGER).map {
            WebDavClient(it, username, password)
        }

}

object WebDavPaths {

    fun normalizeFolderUrl(raw: String): String? {
        val trimmed = raw.trim().replace(" ", "%20")
        if (trimmed.isEmpty()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        val ascii = uri.toASCIIString()
        return if (ascii.endsWith("/")) ascii else "$ascii/"
    }

    fun defaultServiceName(folderUrl: String): String =
        runCatching { URI(folderUrl).host?.takeIf { it.isNotBlank() } }.getOrNull() ?: "WebDAV"

    /**
     * Files live in a Toolbox subfolder so a WebDAV root never receives
     * ledger.json or token files directly. The function is idempotent when the
     * configured URL already ends in Toolbox.
     */
    fun toolboxFolderUrl(folderUrl: String): String {
        val base = if (folderUrl.endsWith("/")) folderUrl else "$folderUrl/"
        val path = runCatching { URI(base).path.orEmpty() }.getOrDefault("")
        val segments = path.trim('/').split('/')
        if (segments.size >= 2 && segments[segments.lastIndex - 1].equals(TOOLBOX_FOLDER, true) &&
            BackupTool.entries.any { it.key.equals(segments.last(), true) }
        ) return URI(base).resolve("../").toASCIIString()
        val lastSegment = segments.lastOrNull().orEmpty()
        return if (lastSegment.equals(TOOLBOX_FOLDER, ignoreCase = true)) {
            base
        } else {
            base + "$TOOLBOX_FOLDER/"
        }
    }

    fun toolFolderUrl(folderUrl: String, tool: BackupTool): String =
        toolboxFolderUrl(folderUrl) + tool.key + "/"

    fun legacyFolderUrls(folderUrl: String, tool: BackupTool): List<String> {
        val original = folderUrl.trimEnd('/') + "/"
        val target = toolFolderUrl(original, tool)
        return listOf(toolboxFolderUrl(original), original).distinct().filter { it != target }
    }

    const val TOOLBOX_FOLDER = "Toolbox"

    fun fileUrl(folderUrl: String, name: String): String =
        folderUrl + URLEncoder.encode(name, Charsets.UTF_8.name()).replace("+", "%20")

    fun basicAuthHeader(username: String, password: String): String =
        "Basic " + java.util.Base64.getEncoder()
            .encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
}

/**
 * Persists one or more WebDAV endpoints locally and tracks which tools
 * ([BackupTool.LEDGER], [BackupTool.TOTP]) back up to each service.
 * Every password is encrypted with an Android Keystore AES/GCM key before it
 * touches SharedPreferences.
 */
class WebDavConfigStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    private fun ensureMigrated() {
        if (prefs.contains(KEY_SERVICES_JSON)) return
        val legacyUrl = prefs.getString(KEY_FOLDER_URL, "")?.trim().orEmpty()
        val legacyUser = prefs.getString(KEY_USERNAME, "")?.trim().orEmpty()
        val legacyPassEnc = prefs.getString(KEY_PASSWORD_ENC, null)
        if (legacyUrl.isEmpty() && legacyUser.isEmpty() && legacyPassEnc == null) return
        val legacyId = "legacy-default"
        val item = JSONObject()
            .put("id", legacyId)
            .put("name", WebDavPaths.defaultServiceName(legacyUrl))
            .put("folderUrl", legacyUrl)
            .put("username", legacyUser)
        if (!legacyPassEnc.isNullOrEmpty()) {
            item.put("passwordEnc", legacyPassEnc)
        }
        val array = JSONArray().put(item)
        prefs.edit {
            putString(KEY_SERVICES_JSON, array.toString())
            putStringSet(toolBindKey(BackupTool.LEDGER), setOf(legacyId))
        }
    }

    fun loadServices(): List<WebDavServiceProfile> {
        ensureMigrated()
        val raw = prefs.getString(KEY_SERVICES_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim().takeIf { it.isNotEmpty() } ?: continue
                    val folderUrl = obj.optString("folderUrl").trim()
                    val name = obj.optString("name").trim()
                        .ifEmpty { WebDavPaths.defaultServiceName(folderUrl) }
                    val username = obj.optString("username").trim()
                    val passwordEnc = obj.optString("passwordEnc").takeIf { it.isNotEmpty() }
                    val password = passwordEnc?.let { decrypt(it) } ?: ""
                    add(
                        WebDavServiceProfile(
                            id = id,
                            name = name,
                            folderUrl = folderUrl,
                            username = username,
                            password = password,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeServices(services: List<WebDavServiceProfile>) {
        val array = JSONArray()
        for (service in services) {
            val obj = JSONObject()
                .put("id", service.id)
                .put("name", service.name.trim().ifEmpty { WebDavPaths.defaultServiceName(service.folderUrl) })
                .put("folderUrl", service.folderUrl)
                .put("username", service.username)
            if (service.password.isNotEmpty()) {
                obj.put("passwordEnc", encrypt(service.password))
            }
            array.put(obj)
        }
        val first = services.firstOrNull()
        prefs.edit {
            putString(KEY_SERVICES_JSON, array.toString())
            if (first != null) {
                putString(KEY_FOLDER_URL, first.folderUrl)
                putString(KEY_USERNAME, first.username)
                if (first.password.isEmpty()) {
                    remove(KEY_PASSWORD_ENC)
                } else {
                    putString(KEY_PASSWORD_ENC, encrypt(first.password))
                }
            } else {
                remove(KEY_FOLDER_URL)
                remove(KEY_USERNAME)
                remove(KEY_PASSWORD_ENC)
            }
        }
    }

    fun upsertService(
        profile: WebDavServiceProfile,
        defaultTools: Set<BackupTool> = emptySet(),
    ) {
        val current = loadServices().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        val isNew = index < 0
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        writeServices(current)
        if (isNew && defaultTools.isNotEmpty()) {
            defaultTools.forEach { tool ->
                setServiceSelected(tool, profile.id, true)
            }
        }
    }

    fun deleteService(id: String) {
        val next = loadServices().filterNot { it.id == id }
        writeServices(next)
        BackupTool.entries.forEach { tool ->
            val updated = selectedServiceIds(tool) - id
            prefs.edit { putStringSet(toolBindKey(tool), updated) }
        }
    }

    fun selectedServiceIds(tool: BackupTool): Set<String> {
        ensureMigrated()
        val validIds = loadServices().map { it.id }.toSet()
        val stored = prefs.getStringSet(toolBindKey(tool), null).orEmpty()
        return stored.intersect(validIds)
    }

    fun setServiceSelected(tool: BackupTool, serviceId: String, selected: Boolean) {
        val current = selectedServiceIds(tool).toMutableSet()
        if (selected) current.add(serviceId) else current.remove(serviceId)
        prefs.edit { putStringSet(toolBindKey(tool), current) }
    }

    fun selectedServices(tool: BackupTool): List<WebDavServiceProfile> {
        val ids = selectedServiceIds(tool)
        return loadServices().filter { it.id in ids && it.isComplete }
    }

    fun load(): WebDavConfig = selectedServices(BackupTool.LEDGER).firstOrNull()
        ?.toWebDavConfig() ?: WebDavConfig("", "", "")

    fun isPasswordUnavailable(): Boolean {
        ensureMigrated()
        val raw = prefs.getString(KEY_SERVICES_JSON, null)
        if (raw != null) {
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).any { i ->
                    val enc = array.optJSONObject(i)?.optString("passwordEnc")
                        ?.takeIf { it.isNotEmpty() }
                    enc != null && decrypt(enc) == null
                }
            }.getOrDefault(false)
        }
        val encoded = prefs.getString(KEY_PASSWORD_ENC, null) ?: return false
        return decrypt(encoded) == null
    }

    fun save(config: WebDavConfig) {
        val existing = selectedServices(BackupTool.LEDGER).firstOrNull()
            ?: loadServices().firstOrNull()
        val profile = WebDavServiceProfile(
            id = existing?.id ?: "legacy-default",
            name = existing?.name ?: WebDavPaths.defaultServiceName(config.folderUrl),
            folderUrl = config.folderUrl,
            username = config.username,
            password = config.password,
        )
        upsertService(profile, defaultTools = setOf(BackupTool.LEDGER))
        setServiceSelected(BackupTool.LEDGER, profile.id, true)
    }

    fun clear() {
        prefs.edit { clear() }
    }

    fun lastSyncAtMillis(tool: BackupTool = BackupTool.LEDGER): Long? {
        val key = when (tool) {
            BackupTool.LEDGER -> KEY_LAST_SYNC_AT
            BackupTool.TOTP -> KEY_LAST_TOTP_SYNC_AT
        }
        return if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    }

    fun saveLastSync(millis: Long, tool: BackupTool = BackupTool.LEDGER) {
        val key = when (tool) {
            BackupTool.LEDGER -> KEY_LAST_SYNC_AT
            BackupTool.TOTP -> KEY_LAST_TOTP_SYNC_AT
        }
        prefs.edit { putLong(key, millis) }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = runCatching {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        if (raw.size <= GCM_IV_BYTES) return@runCatching null
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, raw.copyOfRange(0, GCM_IV_BYTES)),
        )
        String(cipher.doFinal(raw.copyOfRange(GCM_IV_BYTES, raw.size)), Charsets.UTF_8)
    }.getOrNull()

    companion object {
        private const val PREFS_NAME = "ledger_webdav"
        private const val KEY_FOLDER_URL = "folder_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD_ENC = "password_enc"
        private const val KEY_SERVICES_JSON = "services_json"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_TOTP_SYNC_AT = "last_totp_sync_at"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "toolbox_ledger_webdav"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128

        private fun toolBindKey(tool: BackupTool): String = "tool_services_${tool.key}"
    }
}
