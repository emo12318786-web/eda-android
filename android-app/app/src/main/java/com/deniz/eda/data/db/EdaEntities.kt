package com.deniz.eda.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * eda_db.py'deki SQLite tablolarinin Kotlin/Room karsiligi.
 * 5 tablo: memory, diary, reminders, learning, logs
 */

// ═══ ۱. MEMORY — Hatiralar ═══
@Entity(tableName = "memory")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metin: String,
    val kategori: String = "genel",
    val tarih: Long = System.currentTimeMillis()
)

// ═══ ۲. DIARY — Gunluk ═══
@Entity(tableName = "diary")
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metin: String,
    val tarih: Long = System.currentTimeMillis()
)

// ═══ ۳. REMINDERS — Hatirlaticilar ═══
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metin: String,
    val saat: Int,
    val dakika: Int,
    val tamamlandi: Boolean = false,
    val tarih: Long = System.currentTimeMillis()
)

// ═══ ۴. LEARNING — Ogrenme ═══
@Entity(tableName = "learning")
data class LearningEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kelime: String,
    val komut: String,
    val tarih: Long = System.currentTimeMillis()
)

// ═══ ۵. LOGS — Kayitlar ═══
@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kullaniciMetin: String,
    val edaCevap: String,
    val kaynak: String = "bilinmiyor",
    val sureMs: Long = 0,
    val tarih: Long = System.currentTimeMillis()
)
