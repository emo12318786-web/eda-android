package com.deniz.eda.utils

import java.util.Calendar

/**
 * Orijinal eda.py icindeki miladi_den_semsiye_çevir() / semsi_tarih() fonksiyonlarinin
 * birebir Kotlin karsiligi. Ayni algoritma, ayni sabitler.
 */
object JalaliCalendar {

    private val SEMSI_AYLAR = listOf(
        "Ferverdin", "Ordibehest", "Hordad", "Tir", "Mordad", "Sehriver",
        "Mehr", "Aban", "Azer", "Dey", "Behmen", "Esfend"
    )

    private val GUNLER = listOf("Pazartesi", "Sali", "Carsamba", "Persembe", "Cuma", "Cumartesi", "Pazar")

    data class SemsiTarih(val yil: Int, val ay: Int, val gun: Int) {
        fun ayAdi(): String = SEMSI_AYLAR[ay - 1]
    }

    fun miladidenSemsiyeCevir(gYilParam: Int, gAy: Int, gGun: Int): SemsiTarih {
        val gunSayisi = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2: Int = if (gAy > 2 && ((gYilParam % 4 == 0 && gYilParam % 100 != 0) || gYilParam % 400 == 0)) {
            gYilParam + 1
        } else {
            gYilParam
        }
        var toplamGun = (355666L + (365L * gYilParam) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
                ((gy2 + 399) / 400) + gGun + gunSayisi[gAy - 1])

        var jYil = -1595 + (33 * (toplamGun / 12053)).toInt()
        toplamGun %= 12053
        jYil += 4 * (toplamGun / 1461).toInt()
        toplamGun %= 1461
        if (toplamGun > 365) {
            jYil += ((toplamGun - 1) / 365).toInt()
            toplamGun = (toplamGun - 1) % 365
        }
        val jAy: Int
        val jGun: Int
        if (toplamGun < 186) {
            jAy = 1 + (toplamGun / 31).toInt()
            jGun = 1 + (toplamGun % 31).toInt()
        } else {
            jAy = 7 + ((toplamGun - 186) / 30).toInt()
            jGun = 1 + ((toplamGun - 186) % 30).toInt()
        }
        return SemsiTarih(jYil, jAy, jGun)
    }

    /** Bugunun tarihini "20 Sehriver 1405" + gun adi ("Cuma") olarak dondurur. */
    fun bugununSemsiTarihi(): Pair<String, String> {
        val simdi = Calendar.getInstance()
        val yil = simdi.get(Calendar.YEAR)
        val ay = simdi.get(Calendar.MONTH) + 1
        val gun = simdi.get(Calendar.DAY_OF_MONTH)
        val semsi = miladidenSemsiyeCevir(yil, ay, gun)

        // Calendar.DAY_OF_WEEK: Pazar=1 ... Cumartesi=7. Python'daki weekday() (Pazartesi=0)
        // ile ayni sirayi elde etmek icin donusturuyoruz.
        val javaGun = simdi.get(Calendar.DAY_OF_WEEK) // 1=Pazar..7=Cumartesi
        val pythonWeekday = (javaGun + 5) % 7 // 0=Pazartesi..6=Pazar
        val gunAdi = GUNLER[pythonWeekday]

        return Pair("${semsi.gun} ${semsi.ayAdi()} ${semsi.yil}", gunAdi)
    }
}
