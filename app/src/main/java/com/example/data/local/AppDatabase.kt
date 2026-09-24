package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.DocumentPageDao
import com.example.data.local.dao.LandDocumentDao
import com.example.data.local.entity.DocumentPageEntity
import com.example.data.local.entity.LandDocumentEntity

@Database(
    entities = [
        LandDocumentEntity::class,
        DocumentPageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun landDocumentDao(): LandDocumentDao
    abstract fun documentPageDao(): DocumentPageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "newaz_land_records.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
