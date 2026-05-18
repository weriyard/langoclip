package com.floatingclipboard.data.example

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ExampleEntry::class], version = 1, exportSchema = false)
abstract class ExampleDatabase : RoomDatabase() {

    abstract fun exampleDao(): ExampleDao

    companion object {
        @Volatile private var instance: ExampleDatabase? = null

        // Returns null if en_examples.db hasn't been generated yet — DictionaryClient
        // simply doesn't enrich in that case.
        fun getOptional(context: Context): ExampleDatabase? {
            instance?.let { return it }
            return try {
                synchronized(this) {
                    instance ?: Room
                        .databaseBuilder(context.applicationContext, ExampleDatabase::class.java, "en_examples")
                        .createFromAsset("en_examples.db")
                        .fallbackToDestructiveMigration()
                        .build()
                        .also { instance = it }
                }
            } catch (_: Exception) {
                // en_examples.db not generated yet — run tools/generate_examples.py
                null
            }
        }
    }
}
