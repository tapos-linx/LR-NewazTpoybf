package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.*
import com.example.data.local.entity.*

@Database(
    entities = [
        LandDocumentEntity::class,
        DocumentPageEntity::class,
        OcrBlockEntity::class,
        HcrRecordEntity::class,
        CadastralMapEntity::class,
        CadastralPlotEntity::class,
        LandOwnerEntity::class,
        EvidenceAuditEntity::class,
        BatchJobEntity::class,
        SearchIndexEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun landDocumentDao(): LandDocumentDao
    abstract fun documentPageDao(): DocumentPageDao
    abstract fun ocrBlockDao(): OcrBlockDao
    abstract fun hcrRecordDao(): HcrRecordDao
    abstract fun cadastralMapDao(): CadastralMapDao
    abstract fun cadastralPlotDao(): CadastralPlotDao
    abstract fun landOwnerDao(): LandOwnerDao
    abstract fun evidenceAuditDao(): EvidenceAuditDao
    abstract fun batchJobDao(): BatchJobDao
    abstract fun searchIndexDao(): SearchIndexDao

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
