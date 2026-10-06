package com.deniz.eda.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.appcompat.app.AppCompatActivity

class SttActivity : AppCompatActivity() {
    companion object {
        const val ACTION_STT_RESULT = "com.deniz.eda.STT_RESULT"
        private const val REQ_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dinliyorum...")
        }
        try {
            startActivityForResult(i, REQ_CODE)
        } catch (e: Exception) {
            resultGonder(null)
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ_CODE) {
            val text = if (res == Activity.RESULT_OK) {
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            } else null
            resultGonder(text)
        }
        finish()
    }

    private fun resultGonder(text: String?) {
        val i = Intent(this, EdaForegroundService::class.java).apply {
            action = ACTION_STT_RESULT
            putExtra("stt_text", text ?: "")
        }
        try { startService(i) } catch (e: Exception) { }
    }
}
