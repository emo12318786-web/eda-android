package com.deniz.eda.ai

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object TermuxDeepChat {
    fun run(context: Context, question: String, callback: (String) -> Unit) {
        val callbackIntent = Intent(context, TermuxDeepChatReceiver::class.java).setAction("com.deniz.eda.TERMUX_RESULT")
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val pending = PendingIntent.getBroadcast(context, (System.nanoTime() and 0x7fffffff).toInt(), callbackIntent, flags)
        val script = "ollama run gemma2:2b " + "'" + question.replace("'", "'\\''") + "'"
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "EDA Derin Sohbet")
            putExtra("com.termux.RUN_COMMAND_COMMAND_DESCRIPTION", "Gemma 2:2B sorusunu çalıştırır.")
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending)
        }
        try {
            context.startService(intent)
            TermuxDeepChatReceiver.callback = callback
        } catch (e: Exception) {
            Log.e("TermuxDeepChat", "Could not start Termux RUN_COMMAND", e)
            callback("Termux başlatılamadı. EDA için RUN_COMMAND iznini ve Termux allow-external-apps ayarını etkinleştir.")
        }
    }
}

class TermuxDeepChatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = intent.getBundleExtra("com.termux.RUN_COMMAND_PLUGIN_RESULT")
            ?: intent.extras?.getBundle("result")
        val output = result?.getString("stdout").orEmpty()
        val error = result?.getString("errmsg").orEmpty()
        val answer = output.trim().ifBlank { error.trim() }.ifBlank {
            "Termux'tan yanıt alınamadı. Termux sürümünü, Ollama'yı ve RUN_COMMAND iznini kontrol et."
        }
        callback?.invoke(answer)
        callback = null
    }
    companion object { @Volatile var callback: ((String) -> Unit)? = null }
}
