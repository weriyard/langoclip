package com.langoclip.app.data.saved

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SavedPhraseEntity::class], version = 1, exportSchema = false)
abstract class SavedPhraseDatabase : RoomDatabase() {

    abstract fun savedPhraseDao(): SavedPhraseDao

    companion object {
        @Volatile private var instance: SavedPhraseDatabase? = null

        fun getInstance(context: Context): SavedPhraseDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, SavedPhraseDatabase::class.java, "saved_phrases")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
