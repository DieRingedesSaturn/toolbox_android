package com.example.toolbox.ledger

import com.example.toolbox.network.BoundedHttpResponse
import com.example.toolbox.network.HttpResponseTooLargeException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

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
 * File GET/PUT/DELETE use HttpURLConnection; MKCOL uses a bounded socket request
 * because Android's HttpURLConnection rejects that method. Creates the shared
 * Toolbox folder before its tool-specific child; the configured base must exist.
 *
 * HTTPS enforcement is the caller's job (see [WebDavPaths.normalizeFolderUrl])
 * so JVM unit tests can point at a local http server.
 */
class WebDavClient(
    private val folderUrl: String,
    username: String,
    password: String,
    private val parentFolderUrl: String? = null,
) {
    private val authHeader = WebDavPaths.basicAuthHeader(username, password)

    internal fun sameDestination(other: WebDavClient): Boolean =
        folderUrl == other.folderUrl && authHeader == other.authHeader

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

    fun testConnection() {
        ensureFolder()
        put(TEST_FILE, "{}")
        delete(TEST_FILE)
    }

    /**
     * Creates [folderUrl] with MKCOL. Android's HttpURLConnection rejects
     * WebDAV verbs, so this uses a minimal TLS-capable HTTP/1.1 socket.
     * An existing collection commonly answers 405; treat that as success.
     */
    fun ensureFolder() {
        parentFolderUrl?.takeIf { it != folderUrl }?.let(::ensureFolderAt)
        ensureFolderAt(folderUrl)
    }

    private fun ensureFolderAt(folder: String) {
        val url = URL(folder)
        val raw = Socket()
        try {
            val host = url.host.removeSurrounding("[", "]")
            val port = if (url.port != -1) url.port else url.defaultPort
            raw.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            raw.soTimeout = READ_TIMEOUT_MS
            val socket: Socket = if (url.protocol.equals("https", ignoreCase = true)) {
                val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(raw, host, port, true) as SSLSocket
                ssl.sslParameters = ssl.sslParameters.apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                    if (!isIpAddress(host)) {
                        serverNames = listOf(SNIHostName(host))
                    }
                }
                ssl.startHandshake()
                ssl
            } else {
                raw
            }
            val request = buildString {
                append("MKCOL ").append(url.file.ifEmpty { "/" }).append(" HTTP/1.1\r\n")
                append("Host: ").append(url.host)
                if (url.port != -1) append(':').append(url.port)
                append("\r\nAuthorization: ").append(authHeader)
                append("\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            }
            socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.ISO_8859_1))
                flush()
            }
            val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            var budget = 64 * 1024
            fun readResponseLine(): String {
                val line = StringBuilder()
                while (budget-- > 0) {
                    val char = reader.read()
                    if (char < 0) throw IOException("Incomplete MKCOL response")
                    if (char == 10) return line.toString().removeSuffix("\r")
                    line.append(char.toChar())
                }
                throw IOException("MKCOL response headers too large")
            }
            var informational = 0
            var code: Int
            do {
                val statusLine = readResponseLine()
                if (!statusLine.startsWith("HTTP/1.1 ") && !statusLine.startsWith("HTTP/1.0 ")) {
                    throw IOException("Malformed MKCOL response")
                }
                code = statusLine.split(' ', limit = 3).getOrNull(1)?.toIntOrNull()
                    ?: throw IOException("Malformed MKCOL status")
                if (code in 100..199) {
                    if (++informational > 4) throw IOException("Too many informational responses")
                    while (readResponseLine().isNotEmpty()) { /* Skip informational headers. */ }
                }
            } while (code in 100..199)
            when {
                code in 200..299 || code == 405 -> Unit
                code == 401 || code == 403 ->
                    throw WebDavException(WebDavFailure.UNAUTHORIZED, httpCode = code)
                code == 404 || code == 409 ->
                    throw WebDavException(WebDavFailure.FOLDER_MISSING, httpCode = code)
                else -> throwForStatus(code, "MKCOL")
            }
        } catch (e: WebDavException) {
            throw e
        } catch (e: SSLException) {
            throw WebDavException(WebDavFailure.TLS, cause = e)
        } catch (e: IOException) {
            throw WebDavException(WebDavFailure.NETWORK, cause = e)
        } finally {
            runCatching { raw.close() }
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
                // Never forward Basic credentials or conditional writes to a 3xx target.
                instanceFollowRedirects = false
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
        private const val MAX_RESPONSE_BYTES = LedgerBackupVault.MAX_BYTES.toLong()
        private const val TEST_FILE = ".toolbox-connection-test"

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

        private fun isIpAddress(host: String): Boolean =
            ':' in host || Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(host)
    }
}
