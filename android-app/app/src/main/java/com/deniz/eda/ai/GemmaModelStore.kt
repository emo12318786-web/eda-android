package com.deniz.eda.ai

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Stores user-selected GGUF model files in EDA's private app directory.
 *
 * Importing a model is independent from Termux and Ollama. This class only
 * validates/stores model files; the native inference runtime is separate.
 */
class GemmaModelStore(context: Context) {
    private val root = File(context.filesDir, "models").apply { mkdirs() }

    enum class Slot(val fileName: String) {
        FAST("gemma3-1b.gguf"),
        DEEP("gemma2-2b.gguf")
    }

    data class ModelInfo(
        val slot: Slot,
        val file: File,
        val sizeBytes: Long,
        val sha256: String
    )

    @Synchronized
    fun importGguf(context: Context, uri: Uri, slot: Slot): ModelInfo {
        val temp = File(root, "${slot.fileName}.importing")
        val destination = File(root, slot.fileName)
        val backup = File(root, "${slot.fileName}.previous")
        temp.delete()
        backup.delete()

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            } ?: error("Cannot open selected model file")

            require(temp.length() > 1024 * 1024) {
                "Selected file is too small to be a GGUF model"
            }
            require(temp.inputStream().buffered().use { stream ->
                val magic = ByteArray(4)
                stream.read(magic) == 4 &&
                    magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
            }) { "Invalid model file: GGUF header not found" }

            // Keep the last working model if finalizing the new import fails.
            if (destination.exists()) {
                check(destination.renameTo(backup)) {
                    "Could not preserve the existing model before import"
                }
            }
            if (!temp.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                error("Could not finalize imported model")
            }
            backup.delete()
            return info(slot, destination)
        } catch (error: Throwable) {
            temp.delete()
            if (!destination.exists() && backup.exists()) backup.renameTo(destination)
            throw error
        } finally {
            // A backup is only disposable after a successful replacement.
            if (destination.exists()) backup.delete()
        }
    }

    fun info(slot: Slot): ModelInfo? {
        val file = File(root, slot.fileName)
        return if (file.isFile && file.length() > 1024 * 1024) info(slot, file) else null
    }

    fun hasBothModels(): Boolean = Slot.values().all { info(it) != null }

    fun delete(slot: Slot): Boolean = File(root, slot.fileName).delete()

    private fun info(slot: Slot, file: File): ModelInfo =
        ModelInfo(slot, file, file.length(), sha256(file))

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
