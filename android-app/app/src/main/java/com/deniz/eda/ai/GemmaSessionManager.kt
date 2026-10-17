package com.deniz.eda.ai

import android.content.Context
import android.net.Uri
import com.deniz.eda.core.Settings
import java.io.File

/** Process-wide manager so a loaded GGUF is reused for later questions. */
class GemmaSessionManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val store = GemmaModelStore(appContext)
    private val engine = NativeGemmaEngine()
    @Volatile private var loaded = false
    @Volatile private var loadedSlot: GemmaModelStore.Slot? = null

    data class Status(
        val runtimeAvailable: Boolean,
        val loaded: Boolean,
        val modelReady: Boolean,
        val selectedModel: String,
        val detail: String
    )

    companion object {
        @Volatile private var instance: GemmaSessionManager? = null
        fun get(context: Context): GemmaSessionManager =
            instance ?: synchronized(this) {
                instance ?: GemmaSessionManager(context.applicationContext).also { instance = it }
            }
    }

    private fun selectedSlot(): GemmaModelStore.Slot =
        GemmaModelStore.Slot.fromPreference(Settings.gemmaModel)

    @Synchronized
    fun selectModel(preferenceValue: String) {
        val next = GemmaModelStore.Slot.fromPreference(preferenceValue)
        if (loadedSlot != null && loadedSlot != next) {
            engine.unload()
            loaded = false
            loadedSlot = null
        }
        Settings.gemmaModel = next.preferenceValue
    }

    @Synchronized
    fun importModel(uri: Uri, slot: GemmaModelStore.Slot): GemmaModelStore.ModelInfo {
        engine.unload()
        loaded = false
        loadedSlot = null
        return store.importGguf(appContext, uri, slot)
    }

    @Synchronized
    fun status(): Status {
        val runtime = engine.isRuntimeBundled()
        val slot = selectedSlot()
        val ready = store.info(slot) != null
        return Status(
            runtimeAvailable = runtime,
            loaded = loaded && loadedSlot == slot,
            modelReady = ready,
            selectedModel = slot.displayName,
            detail = when {
                !runtime -> "موتور محلی در دسترس نیست"
                !ready -> "فایل ${slot.displayName} وارد نشده"
                loaded && loadedSlot == slot -> "${slot.displayName} بارگذاری شده و آماده است"
                else -> "${slot.displayName} آمادهٔ بارگذاری است"
            }
        )
    }

    /** Call on Dispatchers.IO. The chosen model stays loaded between questions. */
    @Synchronized
    fun generate(prompt: String, maxTokens: Int = 96): String? {
        if (prompt.isBlank() || !engine.isRuntimeBundled()) return null
        val slot = selectedSlot()
        val model: File = store.info(slot)?.file ?: return null
        if (!loaded || loadedSlot != slot) {
            engine.unload()
            loaded = false
            loadedSlot = null
            if (!engine.loadModel(model)) return null
            loaded = true
            loadedSlot = slot
        }
        val started = System.nanoTime()
        return try {
            engine.generate(prompt, maxTokens)?.also {
                android.util.Log.i("EdaGemmaTiming", "model=${slot.preferenceValue} inference_ms=${(System.nanoTime() - started) / 1_000_000}")
            }
        } catch (e: Exception) {
            android.util.Log.e("EdaGemmaTiming", "Generation failed for ${slot.preferenceValue}", e)
            null
        }
    }

    @Synchronized
    fun unload() {
        engine.unload()
        loaded = false
        loadedSlot = null
    }

    @Synchronized
    fun close() {
        engine.close()
        loaded = false
        loadedSlot = null
    }
}
