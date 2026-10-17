package com.deniz.eda.ai

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Stores user-selected GGUF model files in EDA's private app directory. */
class GemmaModelStore(context: Context) {
    private val root = File(context.filesDir, "models").apply { mkdirs() }
    private val digestCache = mutableMapOf<String, CachedDigest>()

    private data class CachedDigest(val length: Long, val modified: Long, val sha256: String)

    enum class Slot(val fileName: String, val preferenceValue: String, val displayName: String) {
        GEMMA31("gemma3-1b.gguf", "gemma3:1b", "Gemma 3 1B"),
        GEMMA22("gemma2-2b.gguf", "gemma2:2b", "Gemma 2 2B");

        companion object {
            fun fromPreference(value: String): Slot =
                values().firstOrNull { it.preferenceValue == value } ?: GEMMA31
        }
    }

    data class ModelInfo(val slot: Slot, val file: File, val sizeBytes: Long, val sha256: String)

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
            require(temp.length() > 1024 * 1024) { "Selected file is too small to be a GGUF model" }
            require(temp.inputStream().buffered().use { stream ->
                val magic = ByteArray(4)
                stream.read(magic) == 4 && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
            }) { "Invalid model file: GGUF header not found" }

            if (destination.exists()) {
                check(destination.renameTo(backup)) { "Could not preserve the existing model before import" }
            }
            if (!temp.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                error("Could not finalize imported model")
            }
            backup.delete()
            digestCache.remove(destination.absolutePath)
            return info(slot, destination)
        } catch (error: Throwable) {
            temp.delete()
            if (!destination.exists() && backup.exists()) backup.renameTo(destination)
            throw error
        } finally {
            if (destination.exists()) backup.delete()
        }
    }

    @Synchronized
    fun info(slot: Slot): ModelInfo? {
        val file = File(root, slot.fileName)
        return if (file.isFile && file.length() > 1024 * 1024) info(slot, file) else null
    }

    @Synchronized
    fun hasModel(slot: Slot): Boolean = info(slot) != null

    @Synchronized
    fun delete(slot: Slot): Boolean {
        val file = File(root, slot.fileName)
        digestCache.remove(file.absolutePath)
        return file.delete()
    }

    @Synchronized
    private fun info(slot: Slot, file: File): ModelInfo = ModelInfo(slot, file, file.length(), sha256(file))

    @Synchronized
    private fun sha256(file: File): String {
        val key = file.absolutePath
        val length = file.length()
        val modified = file.lastModified()
        val cached = digestCache[key]
        if (cached != null && cached.length == length && cached.modified == modified) return cached.sha256
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                digest.update(buffer, 0, read)
            }
        }
        val value = digest.digest().joinToString("") { "%02x".format(it) }
        digestCache[key] = CachedDigest(length, modified, value)
        return value
    }
}
