package com.deniz.eda.ai

import java.io.File

/**
 * Contract for the in-process native inference engine.
 *
 * This adapter intentionally fails closed until libeda_llama.so is packaged
 * into the APK. It never falls back to localhost:11434 or silently launches
 * Termux/Ollama. The JNI implementation must be added with the pinned
 * llama.cpp source before this engine can be enabled in production.
 */
class NativeGemmaEngine {

    enum class State { UNAVAILABLE, READY, MODEL_LOADED, CLOSED }

    @Volatile
    var state: State = State.UNAVAILABLE
        private set

    private var loadedModelPath: String? = null

    fun isRuntimeBundled(): Boolean = try {
        System.loadLibrary("eda_llama")
        state = State.READY
        true
    } catch (_: UnsatisfiedLinkError) {
        state = State.UNAVAILABLE
        false
    } catch (_: SecurityException) {
        state = State.UNAVAILABLE
        false
    }

    /**
     * Returns false until the native runtime is actually bundled.
     * Does not attempt any Ollama/Termux/network fallback.
     */
    @Synchronized
    fun loadModel(modelFile: File): Boolean {
        if (!isRuntimeBundled() || !modelFile.isFile || modelFile.length() < 1024 * 1024) {
            return false
        }
        return try {
            val ok = nativeLoadModel(modelFile.absolutePath)
            if (ok) {
                loadedModelPath = modelFile.absolutePath
                state = State.MODEL_LOADED
            }
            ok
        } catch (_: UnsatisfiedLinkError) {
            state = State.UNAVAILABLE
            false
        }
    }

    @Synchronized
    fun generate(prompt: String, maxTokens: Int = 192): String? {
        if (state != State.MODEL_LOADED || prompt.isBlank()) return null
        return try {
            nativeGenerate(prompt, maxTokens.coerceIn(1, 512))
        } catch (_: UnsatisfiedLinkError) {
            state = State.UNAVAILABLE
            loadedModelPath = null
            null
        }
    }

    @Synchronized
    fun unload() {
        if (state == State.MODEL_LOADED && isRuntimeBundled()) {
            try { nativeUnloadModel() } catch (_: UnsatisfiedLinkError) { }
        }
        loadedModelPath = null
        state = if (isRuntimeBundled()) State.READY else State.UNAVAILABLE
    }

    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String?
    private external fun nativeUnloadModel()
}
