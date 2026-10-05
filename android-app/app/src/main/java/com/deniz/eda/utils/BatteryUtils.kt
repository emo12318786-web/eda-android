package com.deniz.eda.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * termux-battery-status cagrisinin native karsiligi. Ek izin gerekmez -
 * ACTION_BATTERY_CHANGED sticky intent'i her zaman anlik veriyi doner.
 */
object BatteryUtils {

    data class PilBilgisi(val yuzde: Int, val sarjOluyorMu: Boolean, val durum: String)

    fun pilBilgisiAl(context: Context): PilBilgisi? {
        val intent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return null

        val seviye = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val olcek = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (seviye < 0 || olcek <= 0) return null
        val yuzde = (seviye * 100) / olcek

        val statusCode = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val sarjOluyorMu = statusCode == BatteryManager.BATTERY_STATUS_CHARGING ||
                statusCode == BatteryManager.BATTERY_STATUS_FULL
        val durum = when (statusCode) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "sarj oluyor"
            BatteryManager.BATTERY_STATUS_FULL -> "dolu"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "pilde"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "sarj olmuyor"
            else -> "UNKNOWN"
        }
        return PilBilgisi(yuzde, sarjOluyorMu, durum)
    }

    /** Hitap eki (orn. "deniz") cagiran taraftan (CommandProcessor) parametre olarak alinir. */
    fun pilDurumuMetni(context: Context, hitap: String): String {
        val bilgi = pilBilgisiAl(context) ?: return "Pil bilgisi alınamadı $hitap."
        return "Pil yüzde ${bilgi.yuzde}. Durum: ${bilgi.durum}."
    }
}
