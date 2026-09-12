package com.deniz.eda.core

import android.content.Context
import com.deniz.eda.data.ReminderStore
import java.util.Calendar

/**
 * Orijinal hatirlatma_kontrol() dongusunun karsiligi. Servis her 30 saniyede
 * bir bunu cagirir; su anki saat/dakika, bekleyen bir hatirlatmayla
 * eslesiyorsa geri donen mesaji TTS ile soyler ve o hatirlatmayi
 * tamamlandi olarak isaretler.
 */
object ReminderChecker {

    fun kontrolEt(context: Context): List<String> {
        val simdi = Calendar.getInstance()
        val saat = simdi.get(Calendar.HOUR_OF_DAY)
        val dakika = simdi.get(Calendar.MINUTE)

        val tetiklenenler = ReminderStore.bekleyenler(context).filter {
            it.saat == saat && it.dakika == dakika
        }
        tetiklenenler.forEach { ReminderStore.tamamlandiIsaretle(context, it.id) }
        return tetiklenenler.map { it.metin }
    }
}
