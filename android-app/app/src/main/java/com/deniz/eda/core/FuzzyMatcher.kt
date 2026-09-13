package com.deniz.eda.core

import kotlin.math.max
import kotlin.math.min

object FuzzyMatcher {

    private const val VARSAYILAN_ESIK = 0.72

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val onceki = IntArray(b.length + 1) { it }
        val simdiki = IntArray(b.length + 1)
        for (i in 1..a.length) {
            simdiki[0] = i
            for (j in 1..b.length) {
                val maliyet = if (a[i - 1] == b[j - 1]) 0 else 1
                simdiki[j] = min(
                    min(simdiki[j - 1] + 1, onceki[j] + 1),
                    onceki[j - 1] + maliyet
                )
            }
            System.arraycopy(simdiki, 0, onceki, 0, simdiki.size)
        }
        return onceki[b.length]
    }

    fun benzerlik(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val mesafe = levenshtein(a.lowercase(), b.lowercase())
        val maksUzunluk = max(a.length, b.length)
        return 1.0 - (mesafe.toDouble() / maksUzunluk.toDouble())
    }

    fun icindeVarMi(metin: String, anahtar: String, esik: Double = VARSAYILAN_ESIK): Boolean {
        if (metin.isBlank() || anahtar.isBlank()) return false
        if (anahtar in metin) return true
        val metinKelimeleri = Regex("\\w+").findAll(metin.lowercase()).map { it.value }.toList()
        val anahtarKelimeleri = Regex("\\w+").findAll(anahtar.lowercase()).map { it.value }.toList()
        if (anahtarKelimeleri.isEmpty()) return false
        if (anahtarKelimeleri.size == 1) {
            val hedef = anahtarKelimeleri[0]
            val yerelEsik = if (hedef.length <= 3) esik + 0.1 else esik
            for (kelime in metinKelimeleri) {
                if (benzerlik(kelime, hedef) >= yerelEsik) return true
            }
            return false
        }
        for (i in 0..metinKelimeleri.size - anahtarKelimeleri.size) {
            var hepsiEslesti = true
            for (j in anahtarKelimeleri.indices) {
                if (benzerlik(metinKelimeleri[i + j], anahtarKelimeleri[j]) < esik) {
                    hepsiEslesti = false
                    break
                }
            }
            if (hepsiEslesti) return true
        }
        return false
    }

    fun herhangiBiri(metin: String, vararg anahtarlar: String): Boolean {
        return anahtarlar.any { icindeVarMi(metin, it) }
    }
}
