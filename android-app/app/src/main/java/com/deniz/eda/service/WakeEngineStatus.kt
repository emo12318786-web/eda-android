package com.deniz.eda.service

import org.json.JSONObject

/**
 * Live status shared by wake-word listeners and the local control panel.
 * "NOT_INTEGRATED" is intentionally distinct from "OFF" so the UI never
 * claims an engine is installed or running when it is not.
 */
object WakeEngineStatus {
    @Volatile var voskState: String = "STOPPED"
    @Volatile var voskDetail: String = "Not prepared"
    @Volatile var voskLastHeard: String = ""
    @Volatile var voskExpected: String = "eda"
    @Volatile var voskLastMatch: Boolean = false
    @Volatile var sherpaLastHeard: String = ""
    @Volatile var sherpaExpected: String = "eda"
    @Volatile var sherpaLastMatch: Boolean = false
    @Volatile var activeEngine: String = "VOSK"
    @Volatile var sherpaState: String = "NOT_INTEGRATED"
    @Volatile var sherpaDetail: String = "Sherpa-ONNX is not integrated in this build"

    fun toJson(): JSONObject = JSONObject().apply {
        put("activeEngine", activeEngine)
        put("wakeWord", if (activeEngine == "SHERPA") sherpaExpected else voskExpected)
        put("lastHeard", if (activeEngine == "SHERPA") sherpaLastHeard else voskLastHeard)
        put("lastMatch", if (activeEngine == "SHERPA") sherpaLastMatch else voskLastMatch)
        put("vosk", JSONObject().apply {
            put("state", voskState)
            put("detail", voskDetail)
            put("lastHeard", voskLastHeard)
            put("expected", voskExpected)
            put("lastMatch", voskLastMatch)
        })
        put("sherpa", JSONObject().apply {
            put("state", sherpaState)
            put("detail", sherpaDetail)
            put("lastHeard", sherpaLastHeard)
            put("expected", sherpaExpected)
            put("lastMatch", sherpaLastMatch)
        })
    }
}
