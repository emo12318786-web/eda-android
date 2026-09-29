package com.deniz.eda.utils

/**
 * تبدیل حروف فارسی/عربی به لاتین
 * برای TTS ترکی که نمی‌تونه حروف فارسی رو بخونه
 */
object PersianToLatin {

    /**
     * دیکشنری اسم‌های معروف (که حروف صدادارشون مشخصه)
     * اینا دقیق تبدیل می‌شن، نه حرف‌به‌حرف
     */
    private val dictionary = mapOf(
        // اسم‌های محله‌های تبریز
        "فجر" to "Fecr",
        "لاله غربی" to "Lale Garbi",
        "لاله شرقی" to "Lale Şerqi",
        "مسکنی" to "Meskeni",
        "ولیعصر" to "Veliasr",
        "منظریه" to "Manzariye",
        "باغمیشه" to "Bağmişe",
        "ائل‌گلی" to "El Gölü",
        "زعفرانیه" to "Zaferaniye",
        "ابوریحان" to "Ebureyhan",
        "امیرکبیر" to "Emir Kebir",
        "راه‌آهن" to "Rah Ahan",
        "یوسف‌آباد" to "Yusifabad",
        "قره‌آغاج" to "Qaraağac",
        "خسروشاه" to "Xosroşah",
        "شهرک" to "Şehrek",
        "نصف‌راه" to "Nesferah",
        "سردرود" to "Serdroud",
        "باسمنج" to "Basmenj",
        "خواجه" to "Hace",
        "سپاهان" to "Sepahan",
        "پاسداران" to "Pasdaran",
        "گلگشت" to "Golgasht",
        "میدان" to "Meydan",
        "خیابان" to "Hiaban",
        "کوچه" to "Kuce",
        "بلوار" to "Bulvar",
        "مجتمع" to "Muctema",
        "مسکونی" to "Meskeni",
        "صدف" to "Sadaf",
        "آبرسان" to "Abersan",
        "دانشگاه" to "Danişgah",
        "شهرستان" to "Şehristan",
        "بخش" to "Bahş",
        "مرکزی" to "Merkezi",
        
        // شهرها
        "تبریز" to "Tebriz",
        "تهران" to "Tahran",
        "اصفهان" to "İsfahan",
        "مشهد" to "Meşhed",
        "شیراز" to "Şiraz",
        "اهواز" to "Ahvaz",
        "قم" to "Qom",
        "کرج" to "Kerec",
        "رشت" to "Reşt",
        "یزد" to "Yezd",
        "کرمان" to "Kerman",
        "ارومیه" to "Urmiye",
        "اردبیل" to "Erdebil",
        "زنجان" to "Zencan",
        "همدان" to "Hemedan",
        "گرگان" to "Gürgan",
        "ساری" to "Sari",
        "بابل" to "Babol",
        "آمل" to "Amol",
        "بوشهر" to "Buşehr",
        "بندرعباس" to "Bender Abbas",
        "زاهدان" to "Zahedan",
        
        // کشورها
        "ایران" to "İran",
        "ترکیه" to "Türkiye",
        "آذربایجان" to "Azerbaycan",
        "عراق" to "Irak",
        "افغانستان" to "Afganistan",
    )

    /**
     * نقشه حروف (برای بقیه کلمات)
     */
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
     * اول دیکشنری رو چک می‌کنه، اگه نبود حرف‌به‌حرف
     */
    fun convert(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        // ۱. اول دیکشنری رو چک کن (کل عبارت)
        dictionary[trimmed]?.let { return it }

        // ۲. بعد هر کلمه رو جدا چک کن
        val words = trimmed.split(" ")
        val convertedWords = words.map { word ->
            dictionary[word] ?: charToLatin(word)
        }
        return convertedWords.joinToString(" ")
    }

    /**
     * تبدیل حرف‌به‌حرف
     */
    private fun charToLatin(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            val mapped = charMap[ch]
            if (mapped != null) {
                sb.append(mapped)
            } else {
                sb.append(ch)
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
