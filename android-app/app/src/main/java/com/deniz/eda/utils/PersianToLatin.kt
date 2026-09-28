package com.deniz.eda.utils

/**
 * تبدیل حروف فارسی/عربی به لاتین
 * برای TTS ترکی که نمی‌تونه حروف فارسی رو بخونه
 */
object PersianToLatin {

    private val charMap = mapOf(
        'ا' to "a", 'آ' to "a", 'أ' to "a", 'إ' to "e", 'ء' to "",
        'ب' to "b", 'پ' to "p",
        'ت' to "t", 'ث' to "s",
        'ج' to "c", 'چ' to "ç",
        'ح' to "h", 'خ' to "kh",
        'د' to "d", 'ذ' to "z",
        'ر' to "r", 'ز' to "z", 'ژ' to "j",
        'س' to "s", 'ش' to "ş",
        'ص' to "s", 'ض' to "z",
        'ط' to "t", 'ظ' to "z",
        'ع' to "a", 'غ' to "gh",
        'ف' to "f", 'ق' to "gh",
        'ک' to "k", 'گ' to "g",
        'ل' to "l", 'م' to "m", 'ن' to "n",
        'و' to "u", 'ه' to "e", 'ی' to "i", 'ي' to "i",
        'ئ' to "y",
        // اعداد فارسی
        '۰' to "0", '۱' to "1", '۲' to "2", '۳' to "3", '۴' to "4",
        '۵' to "5", '۶' to "6", '۷' to "7", '۸' to "8", '۹' to "9",
        // اعداد عربی
        '٠' to "0", '١' to "1", '٢' to "2", '٣' to "3", '٤' to "4",
        '٥' to "5", '٦' to "6", '٧' to "7", '٨' to "8", '٩' to "9",
    )

    /**
     * هر حرف فارسی/عربی رو به معادل لاتین تبدیل می‌کنه
     */
    fun convert(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            val mapped = charMap[ch]
            if (mapped != null) {
                sb.append(mapped)
            } else {
                sb.append(ch)  // حروف لاتین/نقطه/کاما بدون تغییر
            }
        }
        return sb.toString()
    }

    /**
     * چک می‌کنه اگه متن شامل حروف فارسی/عربی هست
     */
    fun hasPersian(input: String): Boolean {
        return input.any { it in charMap.keys }
    }
}
