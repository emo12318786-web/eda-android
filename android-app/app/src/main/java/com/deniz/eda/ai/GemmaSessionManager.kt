package com.deniz.eda.ai

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Owns the single native Gemma session used by EDA.
 *
 * The manager deliberately keeps at most one model loaded. Switching model
 * first releases the previous native context, then loads the selected GGUF.
 * It does not start Ollama, Termux, or a network provider as a fallback.
 */
class GemmaSessionManager(context: Context) {
    private val appContext = context.applicationContext
    private val store = GemmaModelStore(appContext)
    private val engine = NativeGemmaEngine()

    enum class Task { LIGHT, DEEP }

    data class Status(
        val runtimeAvailable: Boolean,
        val loadedSlot: GemmaModelStore.Slot?,
        val lightModelReady: Boolean,
        val deepModelReady: Boolean,
        val detail: String
    )

    @Volatile
    private var loadedSlot: GemmaModelStore.Slot? = null

    @Synchronized
    fun importModel(uri: Uri, slot: GemmaModelStore.Slot): GemmaModelStore.ModelInfo =
        store.importGguf(appContext, uri, slot)

    @Synchronized
    fun status(): Status {
        val runtime = engine.isRuntimeBundled()
        return Status(
            runtimeAvailable = runtime,
            loadedSlot = loadedSlot,
            lightModelReady = store.info(GemmaModelStore.Slot.FAST) != null,
            deepModelReady = store.info(GemmaModelStore.Slot.DEEP) != null,
            detail = when {
                !runtime -> "Native inference library is not packaged"
                loadedSlot != null -> "Model loaded: ${loadedSlot!!.name}"
                else -> "Native runtime ready; no model loaded"
            }
        )
    }

    /**
     * Runs one request. Models are loaded on demand and only one stays loaded.
     * The caller should invoke this from Dispatchers.IO, not the main thread.
     */
    @Synchronized
    fun generate(task: Task, prompt: String, maxTokens: Int = 192): String? {
        if (prompt.isBlank() || !engine.isRuntimeBundled()) return null

        val requested = when (task) {
            Task.LIGHT -> GemmaModelStore.Slot.FAST
            Task.DEEP -> GemmaModelStore.Slot.DEEP
        }
        val model: File = store.info(requested)?.file ?: return null

        if (loadedSlot != requested) {
            if (loadedSlot != null) {
                engine.unload()
                loadedSlot = null
            }
            if (!engine.loadModel(model)) return null
            loadedSlot = requested
        }
        return engine.generate(prompt, maxTokens)
    }

    @Synchronized
    fun unload() {
        engine.unload()
        loadedSlot = null
    }

    @Synchronized
    fun close() {
        engine.close()
        loadedSlot = null
    }
}
