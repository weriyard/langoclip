package com.langoclip.app.data.lemma

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LemmaForm::class], version = 1, exportSchema = false)
abstract class LemmaDatabase : RoomDatabase() {

    abstract fun lemmaDao(): LemmaDao

    companion object {
        @Volatile private var instance: LemmaDatabase? = null

        // Returns null if en_lemmas.db hasn't been generated yet — Lemmatizer
        // falls through to heuristics in that case.
        fun getOptional(context: Context): LemmaDatabase? {
            instance?.let { return it }
            return try {
                synchronized(this) {
                    instance ?: Room
                        .databaseBuilder(context.applicationContext, LemmaDatabase::class.java, "en_lemmas")
                        .createFromAsset("en_lemmas.db")
                        .fallbackToDestructiveMigration()
                        .build()
                        .also { instance = it }
                }
            } catch (_: Exception) {
                // en_lemmas.db not yet generated — run tools/generate_lemmas.py first
                null
            }
        }
    }
}
