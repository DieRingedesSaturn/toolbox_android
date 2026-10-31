package com.example.toolbox.ledger

import com.example.toolbox.network.BoundedHttpResponse
import com.example.toolbox.network.HttpResponseTooLargeException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLException

enum class WebDavFailure {
    INVALID_URL,
    UNAUTHORIZED,
    FOLDER_MISSING,
    PRECONDITION_FAILED,
    VERSION_UNAVAILABLE,
    HTTP_ERROR,
    NETWORK,
    TLS,
    INVALID_REMOTE_FILE,
    NEWER_SCHEMA,
    PASSWORD_UNAVAILABLE,
}

class WebDavException(
    val failure: WebDavFailure,
    val httpCode: Int? = null,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

data class WebDavFile(
    val body: String,
    val etag: String?,
)

/**
 * Minimal file-based WebDAV client on plain HttpURLConnection. Only GET/PUT/DELETE
 * are used because Android's HttpURLConnection rejects WebDAV verbs such as
 * PROPFIND and MKCOL. The folder must already exist on the server.
 *
 * HTTPS enforcement is the caller's job (see [WebDavPaths.normalizeFolderUrl])
 * so JVM unit tests can point at a local http server.
 */
class WebDavClient(
    private val folderUrl: String,
    username: String,
    password: String,
) {
    private val authHeader = WebDavPaths.basicAuthHeader(username, password)

    fun get(name: String): WebDavFile? = execute(name, "GET") { conn ->
        conn.setRequestProperty("Cache-Control", "no-cache")
        val code = conn.responseCode
        if (code == 404) {
            null
        } else {
            throwForStatus(code, "GET")
            WebDavFile(
                body = BoundedHttpResponse.readText(conn.inputStream, MAX_RESPONSE_BYTES),
                etag = conn.getHeaderField("ETag"),
            )
        }
    }

    fun put(
        name: String,
        body: String,
        ifMatch: String? = null,
        ifNoneMatch: String? = null,
    ) {
        execute(name, "PUT") { conn ->
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            ifMatch?.let { conn.setRequestProperty("If-Match", it) }
            ifNoneMatch?.let { conn.setRequestProperty("If-None-Match", it) }
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            throwForStatus(conn.responseCode, "PUT")
        }
    }

    fun delete(name: String) {
        execute(name, "DELETE") { conn ->
            val code = conn.responseCode
            if (code != 404) throwForStatus(code, "DELETE")
        }
    }

    private fun <T> execute(
        name: String,
        method: String,
        block: (HttpURLConnection) -> T,
    ): T {
        val conn = (URL(WebDavPaths.fileUrl(folderUrl, name)).openConnection() as HttpURLConnection)
            .apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
                requestMethod = method
                setRequestProperty("Authorization", authHeader)
            }
        try {
            return block(conn)
        } catch (e: WebDavException) {
            throw e
        } catch (e: HttpResponseTooLargeException) {
            throw WebDavException(WebDavFailure.INVALID_REMOTE_FILE, cause = e)
        } catch (e: SSLException) {
            throw WebDavException(WebDavFailure.TLS, cause = e)
        } catch (e: IOException) {
            throw WebDavException(WebDavFailure.NETWORK, cause = e)
        } finally {
            conn.disconnect()
        }
    }

    private fun throwForStatus(code: Int, method: String) {
        failureForStatus(code, method)?.let { throw WebDavException(it, httpCode = code) }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_RESPONSE_BYTES = LedgerBackup.MAX_BYTES

        fun failureForStatus(code: Int, method: String): WebDavFailure? = when {
            code in 200..299 -> null
            code == 401 || code == 403 -> WebDavFailure.UNAUTHORIZED
            code == 412 -> WebDavFailure.PRECONDITION_FAILED
            method == "PUT" && (code == 404 || code == 409) -> WebDavFailure.FOLDER_MISSING
            else -> WebDavFailure.HTTP_ERROR
        }

        /** Only a quoted strong ETag is safe for conditional writes. */
        fun ifMatchValue(etag: String?): String? =
            etag?.trim()?.takeIf {
                it.length >= 2 && it.first() == '"' && it.last() == '"' &&
                    '"' !in it.substring(1, it.lastIndex)
            }
    }
}
