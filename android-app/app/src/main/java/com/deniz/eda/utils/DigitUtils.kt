package com.deniz.eda.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ابزار اعداد — همه اعداد همیشه لاتین (4.3، نه ۴.۳)
 * این برای TTS و لاگ حیاتیه.
 */
object DigitUtils {

    val LATIN = Locale.US

    // عدد اعشاری با فرمت لاتین
    fun formatFloat(value: Double, decimals: Int = 1): String =
        String.format(LATIN, "%.${decimals}f", value)

    // عدد صحیح
    fun formatInt(value: Int): String =
        String.format(LATIN, "%d", value)

    // قیمت با جداکننده هزار
    fun formatCurrency(value: Long): String =
        String.format(LATIN, "%,d", value)

    // زمان با اعداد لاتین
    fun formatTime(pattern: String, date: Date = Date()): String =
        SimpleDateFormat(pattern, LATIN).format(date)

    // تبدیل اعداد فارسی/عربی به لاتین
    fun normalizeToLatin(input: String): String {
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        val latin = "0123456789"
        var result = input
        for (i in 0..9) {
            result = result.replace(persian[i], latin[i])
            result = result.replace(arabic[i], latin[i])
        }
        return result
    }
}
