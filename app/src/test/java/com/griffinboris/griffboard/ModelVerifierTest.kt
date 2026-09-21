package com.griffinboris.griffboard

import com.griffinboris.griffboard.models.ModelCatalog
import com.griffinboris.griffboard.models.ModelVerifier
import com.griffinboris.griffboard.models.WhisperModel
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest

class ModelVerifierTest {
    private val content = "verified model fixture".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    private val model = WhisperModel("fixture", "Fixture", "", content.size.toLong(), hash)

    @Test fun copiesVerifiedBytesAndReportsProgress() {
        val output = ByteArrayOutputStream()
        var count = 0L
        ModelVerifier.copy(content.inputStream(), output, model) { count = it }
        assertArrayEquals(content, output.toByteArray())
        assertEquals(model.bytes, count)
    }
    @Test fun rejectsTruncatedFiles() {
        assertThrows(IOException::class.java) {
            ModelVerifier.copy(content.dropLast(1).toByteArray().inputStream(), ByteArrayOutputStream(), model) {}
        }
    }
    @Test fun rejectsOversizedFiles() {
        assertThrows(IOException::class.java) {
            ModelVerifier.copy((content + 0).inputStream(), ByteArrayOutputStream(), model) {}
        }
    }
    @Test fun rejectsCorruptionEvenWhenSizeMatches() {
        assertThrows(IOException::class.java) {
            ModelVerifier.copy(ByteArray(content.size).inputStream(), ByteArrayOutputStream(), model) {}
        }
    }
    @Test fun cancellationStopsTheCopy() {
        assertThrows(InterruptedException::class.java) {
            ModelVerifier.copy(content.inputStream(), ByteArrayOutputStream(), model) { throw InterruptedException() }
        }
    }
    @Test fun catalogHasUniquePinnedWhisperModels() {
        assertEquals(ModelCatalog.models.size, ModelCatalog.models.map { it.id }.toSet().size)
        ModelCatalog.models.forEach {
            assertTrue(it.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(it.revision.matches(Regex("[0-9a-f]{40}")))
            assertTrue(it.url.startsWith("https://huggingface.co/ggerganov/whisper.cpp/resolve/"))
            assertTrue(it.filename.endsWith(".bin"))
            assertTrue(it.bytes > 0)
        }
    }
}
