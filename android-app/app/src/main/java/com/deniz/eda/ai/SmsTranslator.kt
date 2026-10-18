package com.deniz.eda.ai

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * On-device Google ML Kit SMS translator. Target language is always Turkish.
 * A missing/unsupported language model is a failure: never return source text as a fallback.
 */
object SmsTranslator {
    suspend fun translateToTurkish(context: Context, originalText: String): String? = withContext(Dispatchers.IO) {
        val sourceText = originalText.trim()
        if (sourceText.isBlank()) return@withContext null

        var translator: Translator? = null
        try {
            val languageIdentifier = LanguageIdentification.getClient()
            val sourceTag = try {
                Tasks.await(languageIdentifier.identifyLanguage(sourceText), 8, TimeUnit.SECONDS)
            } finally {
                languageIdentifier.close()
            }
            if (sourceTag.isNullOrBlank() || sourceTag == "und") {
                Log.w("SmsTranslator", "Could not identify SMS source language")
                return@withContext null
            }

            val sourceLanguage = TranslateLanguage.fromLanguageTag(sourceTag)
                ?: return@withContext null
            val targetLanguage = TranslateLanguage.TURKISH
            if (sourceLanguage == targetLanguage) return@withContext sourceText

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build()
            translator = Translation.getClient(options)

            // First use may download a language model. Allow a bounded wait; failures are safe.
            Tasks.await(
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()),
                45,
                TimeUnit.SECONDS
            )
            val translated = Tasks.await(translator.translate(sourceText), 20, TimeUnit.SECONDS).trim()
            if (translated.isBlank()) return@withContext null

            // Defensive check: do not present unchanged Arabic-script/Cyrillic/etc. as Turkish.
            val nonLatinSource = sourceText.any {
                Character.UnicodeScript.of(it.code) in setOf(
                    Character.UnicodeScript.ARABIC,
                    Character.UnicodeScript.CYRILLIC,
                    Character.UnicodeScript.HEBREW,
                    Character.UnicodeScript.DEVANAGARI,
                    Character.UnicodeScript.HAN,
                    Character.UnicodeScript.HIRAGANA,
                    Character.UnicodeScript.KATAKANA
                )
            }
            if (nonLatinSource && translated.equals(sourceText, ignoreCase = true)) {
                Log.w("SmsTranslator", "Output identical to non-Latin source; refusing to speak source")
                return@withContext null
            }
            translated
        } catch (e: Exception) {
            Log.w("SmsTranslator", "On-device translation failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            translator?.close()
        }
    }
}
