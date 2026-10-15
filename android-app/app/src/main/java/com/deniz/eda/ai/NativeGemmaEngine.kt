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

    @Volatile
    private var runtimeLoaded = false

    private var loadedModelPath: String? = null

    @Synchronized
    fun isRuntimeBundled(): Boolean {
        if (runtimeLoaded) return true
        return try {
            System.loadLibrary("eda_llama")
            runtimeLoaded = true
            if (state == State.UNAVAILABLE) state = State.READY
            true
        } catch (_: UnsatisfiedLinkError) {
            state = State.UNAVAILABLE
            false
        } catch (_: SecurityException) {
            state = State.UNAVAILABLE
            false
        }
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
            loadedModelPath = null
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
            try {
                nativeUnloadModel()
            } catch (_: UnsatisfiedLinkError) {
                state = State.UNAVAILABLE
                runtimeLoaded = false
            }
        }
        loadedModelPath = null
        if (state != State.UNAVAILABLE && state != State.CLOSED) {
            state = State.READY
        }
    }

    @Synchronized
    fun close() {
        unload()
        state = State.CLOSED
    }

    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String?
    private external fun nativeUnloadModel()
}
