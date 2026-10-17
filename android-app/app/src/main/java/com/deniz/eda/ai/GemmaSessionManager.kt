package com.deniz.eda.ai

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Owns EDA's single native Gemma 3 1B session.
 * Only one GGUF model is supported and kept loaded at a time.
 * No Termux, Ollama, or network fallback is started by this manager.
 */
class GemmaSessionManager(context: Context) {
    private val appContext = context.applicationContext
    private val store = GemmaModelStore(appContext)
    private val engine = NativeGemmaEngine()

    data class Status(
        val runtimeAvailable: Boolean,
        val loaded: Boolean,
        val modelReady: Boolean,
        val detail: String
    )

    @Volatile
    private var loaded = false

    @Synchronized
    fun importModel(uri: Uri): GemmaModelStore.ModelInfo {
        engine.unload()
        loaded = false
        return store.importGguf(appContext, uri, GemmaModelStore.Slot.GEMMA31)
    }

    @Synchronized
    fun status(): Status {
        val runtime = engine.isRuntimeBundled()
        val ready = store.info(GemmaModelStore.Slot.GEMMA31) != null
        return Status(
            runtimeAvailable = runtime,
            loaded = loaded,
            modelReady = ready,
            detail = when {
                !runtime -> "Native inference library is not packaged"
                loaded -> "Gemma 3 1B loaded"
                ready -> "Gemma 3 1B ready; not loaded"
                else -> "Import a Gemma 3 1B GGUF model"
            }
        )
    }

    /** Run inference on Dispatchers.IO; never keep a second model in memory. */
    @Synchronized
    fun generate(prompt: String, maxTokens: Int = 160): String? {
        if (prompt.isBlank() || !engine.isRuntimeBundled()) return null
        val model: File = store.info(GemmaModelStore.Slot.GEMMA31)?.file ?: return null

        if (!loaded) {
            if (!engine.loadModel(model)) return null
            loaded = true
        }
        return engine.generate(prompt, maxTokens)
    }

    @Synchronized
    fun unload() {
        engine.unload()
        loaded = false
    }

    @Synchronized
    fun close() {
        engine.close()
        loaded = false
    }
}
