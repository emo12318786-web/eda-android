package com.deniz.eda.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/**
 * eda_db.py'deki tum fonksiyonlarin DAO karsiligi.
 * 5 tablo icin 5 DAO — hepsi tek dosyada.
 */

// ═══ ۱. MEMORY DAO ═══
@Dao
interface MemoryDao {
    @Insert
    suspend fun ekle(memory: MemoryEntity): Long

    @Query("SELECT * FROM memory ORDER BY tarih DESC LIMIT :limit")
    suspend fun listele(limit: Int = 10): List<MemoryEntity>

    @Query("SELECT * FROM memory WHERE metin LIKE '%' || :kelime || '%' ORDER BY tarih DESC LIMIT :limit")
    suspend fun ara(kelime: String, limit: Int = 10): List<MemoryEntity>

    @Query("DELETE FROM memory WHERE id = :id")
    suspend fun sil(id: Long)

    @Query("SELECT COUNT(*) FROM memory")
    suspend fun sayi(): Int
}

// ═══ ۲. DIARY DAO ═══
@Dao
interface DiaryDao {
    @Insert
    suspend fun ekle(diary: DiaryEntity): Long

    @Query("SELECT * FROM diary ORDER BY tarih DESC LIMIT :limit")
    suspend fun listele(limit: Int = 10): List<DiaryEntity>

    @Query("SELECT * FROM diary WHERE metin LIKE '%' || :kelime || '%' ORDER BY tarih DESC LIMIT :limit")
    suspend fun ara(kelime: String, limit: Int = 10): List<DiaryEntity>
}

// ═══ ۳. REMINDER DAO ═══
@Dao
interface ReminderDao {
    @Insert
    suspend fun ekle(reminder: ReminderEntity): Long

    @Query("SELECT * FROM reminders WHERE tamamlandi = 0 ORDER BY saat, dakika")
    suspend fun bekleyenler(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE saat = :saat AND dakika = :dakika AND tamamlandi = 0")
    suspend fun simdiOlan(saat: Int, dakika: Int): List<ReminderEntity>

    @Query("UPDATE reminders SET tamamlandi = 1 WHERE id = :id")
    suspend fun tamamla(id: Long)

    @Query("DELETE FROM reminders")
    suspend fun temizle()

    @Query("SELECT * FROM reminders ORDER BY saat, dakika")
    suspend fun hepsi(): List<ReminderEntity>
}

// ═══ ۴. LEARNING DAO ═══
@Dao
interface LearningDao {
    @Insert
    suspend fun ekle(learning: LearningEntity): Long

    @Query("SELECT * FROM learning WHERE kelime = :kelime LIMIT 1")
    suspend fun ara(kelime: String): LearningEntity?

    @Query("SELECT * FROM learning ORDER BY tarih DESC LIMIT :limit")
    suspend fun listele(limit: Int = 50): List<LearningEntity>

    @Query("DELETE FROM learning WHERE id = :id")
    suspend fun sil(id: Long)
}

// ═══ ۵. LOG DAO ═══
@Dao
interface LogDao {
    @Insert
    suspend fun ekle(log: LogEntity): Long

    @Query("SELECT * FROM logs ORDER BY tarih DESC LIMIT :limit")
    suspend fun listele(limit: Int = 30): List<LogEntity>

    @Query("SELECT * FROM logs WHERE kullaniciMetin LIKE '%' || :kelime || '%' OR edaCevap LIKE '%' || :kelime || '%' ORDER BY tarih DESC LIMIT :limit")
    suspend fun ara(kelime: String, limit: Int = 30): List<LogEntity>

    @Query("SELECT COUNT(*) FROM logs")
    suspend fun sayi(): Int

    @Query("SELECT kaynak, COUNT(*) as adet FROM logs GROUP BY kaynak ORDER BY adet DESC")
    suspend fun istatistik(): List<LogIstatistik>

    @Query("DELETE FROM logs WHERE tarih < :eskiTarih")
    suspend fun eskileriSil(eskiTarih: Long)
}

data class LogIstatistik(val kaynak: String, val adet: Int)
