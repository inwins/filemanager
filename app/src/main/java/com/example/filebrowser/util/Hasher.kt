package com.example.filebrowser.util

import java.io.InputStream
import java.security.MessageDigest

data class HashResult(val md5: String, val sha1: String, val sha256: String)

object Hasher {
    fun hash(
        input: InputStream,
        onProgress: (Long) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): HashResult? {
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        var total = 0L
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                if (!shouldContinue()) return null
                val read = stream.read(buffer)
                if (read <= 0) break
                md5.update(buffer, 0, read)
                sha1.update(buffer, 0, read)
                sha256.update(buffer, 0, read)
                total += read
                onProgress(total)
            }
        }
        return HashResult(md5.hex(), sha1.hex(), sha256.hex())
    }

    private fun MessageDigest.hex(): String =
        digest().joinToString("") { "%02x".format(it) }
}
