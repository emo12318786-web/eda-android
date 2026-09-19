package com.deniz.eda.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Ayarlarin merkezi yonetimi. Orijinal Python surumundeki DEFAULT_SETTINGS +
 * ayar_getir()/ayar_degistir() ikilisinin Kotlin karsiligidir.
 *
 * SharedPreferences kullanilir: senkron okuma/yazma, servis dongusu icinde
 * (coroutine gerektirmeden) rahatca cagrilabilir - orijinal json_yükle/json_kaydet
 * ile ayni sadelikte.
 */
object Settings {

    private const val PREFS_NAME = "eda_settings"
    private lateinit var prefs: SharedPreferences

    // --- Varsayilan degerler (DEFAULT_SETTINGS karsiligidir) ---
    const val KULLANICI_ADI_VARSAYILAN = "denizçim"
    val WAKE_WORDS_VARSAYILAN = listOf("eda", "e da", "hey eda", "uyan eda")

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getString(key: String, varsayilan: String): String = prefs.getString(key, varsayilan) ?: varsayilan
    fun getBool(key: String, varsayilan: Boolean): Boolean = prefs.getBoolean(key, varsayilan)
    fun getInt(key: String, varsayilan: Int): Int = prefs.getInt(key, varsayilan)
    fun getFloat(key: String, varsayilan: Float): Float = prefs.getFloat(key, varsayilan)
    fun getStringSet(key: String, varsayilan: Set<String>): Set<String> =
        prefs.getStringSet(key, varsayilan) ?: varsayilan

    fun setString(key: String, deger: String) = prefs.edit().putString(key, deger).apply()
    fun setBool(key: String, deger: Boolean) = prefs.edit().putBoolean(key, deger).apply()
    fun setInt(key: String, deger: Int) = prefs.edit().putInt(key, deger).apply()
    fun setFloat(key: String, deger: Float) = prefs.edit().putFloat(key, deger).apply()

    // --- Kisayollar (siklikla kullanilan ayarlar) ---
    var kullaniciAdi: String
        get() = getString("kullanici_adi", KULLANICI_ADI_VARSAYILAN)
        set(v) = setString("kullanici_adi", v)

    var aiSaglayici: String
        get() = getString("ai_provider", "groq")
        set(v) = setString("ai_provider", v)

    var groqApiKey: String
        get() = getString("groq_api_key", "")
        set(v) = setString("groq_api_key", v)

    var deepseekApiKey: String
        get() = getString("deepseek_api_key", "")
        set(v) = setString("deepseek_api_key", v)

    var openrouterApiKey: String
        get() = getString("openrouter_api_key", "")
        set(v) = setString("openrouter_api_key", v)

    var ogrenmeAktif: Boolean
        get() = getBool("ogrenme_aktif", true)
        set(v) = setBool("ogrenme_aktif", v)

    // Uyku modu pil tasarrufu ayarlari (ayni mantik: Termux surumundeki
    // UYKU_BEKLEME_ARALIGI ile ayni amac)
    var uykuBeklemeAraligiMs: Long
        get() = getInt("uyku_bekleme_araligi_ms", 1500).toLong()
        set(v) = setInt("uyku_bekleme_araligi_ms", v.toInt())

    var pilBildirimAraligiDk: Int
        get() = getInt("pil_bildirim_araligi_dk", 15)
        set(v) = setInt("pil_bildirim_araligi_dk", v)

    // Dolar/altin fiyati icin (orn. navasan.tech) - kullanici kendi anahtarini girer.
    var navasanApiKey: String
        get() = getString("navasan_api_key", "")
        set(v) = setString("navasan_api_key", v)

    // Guvenlik numarasi — konum gonderme icin kullanilir
    var guvenlikNumara: String
        get() = getString("guvenlik_numara", "")
        set(v) = setString("guvenlik_numara", v)

    var guvenlikModuAktif: Boolean
        get() = getBool("guvenlik_modu_aktif", false)
        set(v) = setBool("guvenlik_modu_aktif", v)

    var arabaModuAktif: Boolean
        get() = getBool("araba_modu_aktif", false)
        set(v) = setBool("araba_modu_aktif", v)

    // Orijinal script'teki "beep_aktif" ayarinin karsiligi - dinlemeye
    // baslarken kullaniciya duyulur bir sinyal verilip verilmeyecegini tutar.
    // Mod sistemi — eda.py'deki EDA_MODU karsiligi
    var sessizMod: Boolean
        get() = getBool("sessiz_mod", false)
        set(v) = setBool("sessiz_mod", v)

    // حالت خواب — مثل eda.py یا APK فعلی
    // true = خواب عمیق (میکروفون خاموش، فقط pil bildirimi، مصرف ~1%)
    // false = خواب بیدار (میکروفون روشن، با "Eda" بیدار، مصرف ~2-3%)
    var derinUyku: Boolean
        get() = getBool("derin_uyku", false)  // پیش‌فرض: خواب بیدار (مثل APK فعلی)
        set(v) = setBool("derin_uyku", v)

    var pilBildirimAktif: Boolean
        get() = getBool("pil_bildirim_aktif", true)
        set(v) = setBool("pil_bildirim_aktif", v)

    var beepAktif: Boolean
        get() = getBool("beep_aktif", false)
        set(v) = setBool("beep_aktif", v)
}
