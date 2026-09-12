package com.deniz.eda.core

import java.util.Locale

/** Orijinal eda_uyandi_mi() fonksiyonunun Kotlin karsiligi. */
object WakeWordDetector {

    fun uyandiMi(metinHam: String?, wakeWords: List<String> = Settings.WAKE_WORDS_VARSAYILAN): Boolean {
        if (metinHam.isNullOrBlank()) return false
        val metin = metinHam.lowercase(Locale.getDefault())
        val metinKelimeleri = Regex("\\w+").findAll(metin).map { it.value }.toList()

        for (wake in wakeWords) {
            val wakeKelimeleri = Regex("\\w+").findAll(wake.lowercase(Locale.getDefault())).map { it.value }.toList()
            if (wakeKelimeleri.isEmpty()) continue

            if (wakeKelimeleri.size == 1) {
                if (wakeKelimeleri[0] in metinKelimeleri) return true
            } else {
                for (i in 0..metinKelimeleri.size - wakeKelimeleri.size) {
                    if (metinKelimeleri.subList(i, i + wakeKelimeleri.size) == wakeKelimeleri) return true
                }
            }
        }
        return false
    }
}
