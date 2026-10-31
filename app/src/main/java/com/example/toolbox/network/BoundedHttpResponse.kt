package com.example.toolbox.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class HttpResponseTooLargeException(maxBytes: Long) :
    IOException("HTTP response exceeds $maxBytes bytes")

/** Streams an HTTP body into memory while enforcing a hard byte limit. */
object BoundedHttpResponse {
    fun readText(input: InputStream, maxBytes: Long): String {
        require(maxBytes > 0) { "maxBytes must be positive" }
        val output = ByteArrayOutputStream(minOf(maxBytes, INITIAL_BUFFER_BYTES.toLong()).toInt())
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw HttpResponseTooLargeException(maxBytes)
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private const val INITIAL_BUFFER_BYTES = 16 * 1024
    private const val BUFFER_BYTES = 16 * 1024
}
