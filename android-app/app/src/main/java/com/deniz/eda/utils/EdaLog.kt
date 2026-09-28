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
            val zaman = DigitUtils.formatTime("HH:mm:ss")
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
            val zaman = DigitUtils.formatTime("HH:mm:ss")
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

    /**
     * لاگ‌ها رو تو یه فایل txt با timestamp ذخیره می‌کنه
     * و مسیر فایل رو برمی‌گردونه (برای share).
     */
    fun export(context: Context, sleep: Boolean = false): File? {
        return try {
            val tumLoglar = oku(context, sleep)
            if (tumLoglar.isEmpty()) return null

            val zaman = DigitUtils.formatTime("yyyy-MM-dd_HH-mm-ss")
            val dosyaAdi = if (sleep) "eda_sleep_log_$zaman.txt" else "eda_log_$zaman.txt"
            val outDir = File(context.cacheDir, "exports")
            if (!outDir.exists()) outDir.mkdirs()
            val outFile = File(outDir, dosyaAdi)

            val baslik = "═══ Eda Log Raporu ═══\n" +
                    "Tarih: ${DigitUtils.formatTime("yyyy-MM-dd HH:mm:ss")}\n" +
                    "Satır sayısı: ${tumLoglar.size}\n" +
                    "═══════════════════════════════════\n\n"

            outFile.writeText(baslik + tumLoglar.joinToString("\n"))

            android.util.Log.i("EdaLog", "Export: ${outFile.absolutePath}")
            outFile
        } catch (e: Exception) {
            android.util.Log.e("EdaLog", "export hatasi: ${e.message}")
            null
        }
    }

    /**
     * لاگ‌ها رو share می‌کنه (WhatsApp, Telegram, Email, ...)
     */
    fun share(context: Context, sleep: Boolean = false) {
        try {
            val dosya = export(context, sleep)
            if (dosya == null || !dosya.exists()) {
                android.widget.Toast.makeText(context, "Log boş — paylaşılacak bir şey yok.", android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                dosya
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Eda Log Raporu")
                putExtra(android.content.Intent.EXTRA_TEXT, "Eda log kayıtları ekte.")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = android.content.Intent.createChooser(intent, "Log nasıl paylaşılsın?")
            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            android.util.Log.e("EdaLog", "share hatasi: ${e.message}")
            android.widget.Toast.makeText(context, "Paylaşma hatası: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}