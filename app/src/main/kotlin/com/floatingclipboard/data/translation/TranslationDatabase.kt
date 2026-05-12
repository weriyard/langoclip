package com.floatingclipboard.data.translation

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TranslationEntry::class], version = 1, exportSchema = false)
abstract class TranslationDatabase : RoomDatabase() {

    abstract fun translationDao(): TranslationDao

    companion object {
        @Volatile private var instance: TranslationDatabase? = null

        fun getInstance(context: Context): TranslationDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, TranslationDatabase::class.java, "translations")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
