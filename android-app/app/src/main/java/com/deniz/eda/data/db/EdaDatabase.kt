package com.deniz.eda.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Eda ana veritabani — eda.db'nin Room karsiligi.
 * 5 tablo: memory, diary, reminders, learning, logs
 */
@Database(
    entities = [
        MemoryEntity::class,
        DiaryEntity::class,
        ReminderEntity::class,
        LearningEntity::class,
        LogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EdaDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun diaryDao(): DiaryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun learningDao(): LearningDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: EdaDatabase? = null

        fun get(context: Context): EdaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EdaDatabase::class.java,
                    "eda.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
