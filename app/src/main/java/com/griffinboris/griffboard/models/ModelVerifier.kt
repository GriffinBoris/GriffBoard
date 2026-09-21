package com.griffinboris.griffboard.models

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object ModelVerifier {
    fun copy(input: InputStream, output: OutputStream, model: WhisperModel, progress: (Long) -> Unit) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var received = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            received += count
            if (received > model.bytes) throw IOException("Model exceeds its expected size.")
            output.write(buffer, 0, count)
            digest.update(buffer, 0, count)
            progress(received)
        }
        if (received != model.bytes) throw IOException("Incomplete download. Please retry.")
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (hash != model.sha256) throw IOException("Model checksum did not match. Please retry.")
    }
}
