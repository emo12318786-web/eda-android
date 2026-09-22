package com.deniz.eda.utils

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * لاگ ساده با فایل — بدون Room DB
 * دو تا فایل: eda_log.txt (EDA) و sleep_log.txt (Sleep)
 */
object EdaLog {

    private const val FILE_EDA = "eda_log.txt"
    private const val FILE_SLEEP = "sleep_log.txt"
    private const val MAX_LINES = 200

    private fun dosya(context: Context, sleep: Boolean = false): File {
        val name = if (sleep) FILE_SLEEP else FILE_EDA
        return File(context.filesDir, name)
    }

    /**
     * یه خط لاگ اضافه می‌کنه
     * فرمت: [HH:mm:ss] کullanıcı → eda
     */
    fun ekle(
        context: Context,
        kullanici: String,
        eda: String,
        kaynak: String = "komut",
        sleep: Boolean = false
    ) {
        try {
            val f = dosya(context, sleep)
            val zaman = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val satir = "[$zaman] $kullanici → $eda ($kaynak)\n"

            // اضافه کردن به فایل
            f.appendText(satir)

            // اگه فایل بزرگ شد، خطوط قدیمی رو حذف کن
            val lines = f.readLines()
            if (lines.size > MAX_LINES) {
                val yeni = lines.takeLast(MAX_LINES)
                f.writeText(yeni.joinToString("\n") + "\n")
            }

            android.util.Log.d("EdaLog", "log eklendi: $satir")
        } catch (e: Exception) {
            android.util.Log.e("EdaLog", "ekleme hatasi: ${e.message}")
        }
    }

    /**
     * یه خط لاگ ساده (فقط متن)
     */
    fun basit(context: Context, mesaj: String, sleep: Boolean = false) {
        try {
            val f = dosya(context, sleep)
            val zaman = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            f.appendText("[$zaman] $mesaj\n")
        } catch (e: Exception) {
            android.util.Log.e("EdaLog", "basit hatasi: ${e.message}")
        }
    }

    /**
     * همه خطوط رو برمی‌گردونه (قدیمی به جدید)
     */
    fun oku(context: Context, sleep: Boolean = false): List<String> {
        return try {
            val f = dosya(context, sleep)
            if (!f.exists()) return emptyList()
            f.readLines().filter { it.isNotBlank() }
        } catch (e: Exception) {
            android.util.Log.e("EdaLog", "okuma hatasi: ${e.message}")
            emptyList()
        }
    }

    /**
     * همه چیز رو پاک می‌کنه
     */
    fun temizle(context: Context, sleep: Boolean = false) {
        try {
            dosya(context, sleep).delete()
        } catch (e: Exception) {
            android.util.Log.e("EdaLog", "temizleme hatasi: ${e.message}")
        }
    }
}
