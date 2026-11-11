package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.BatchJobDao
import com.example.data.local.dao.CadastralMapDao
import com.example.data.local.dao.CadastralPlotDao
import com.example.data.local.dao.DocumentPageDao
import com.example.data.local.dao.EvidenceAuditDao
import com.example.data.local.dao.ExtractedLandRecordDao
import com.example.data.local.dao.HcrRecordDao
import com.example.data.local.dao.InheritorDao
import com.example.data.local.dao.LandDocumentDao
import com.example.data.local.dao.LandOwnerDao
import com.example.data.local.dao.OcrBlockDao
import com.example.data.local.dao.SearchIndexDao
import com.example.data.local.entity.BatchJobEntity
import com.example.data.local.entity.CadastralMapEntity
import com.example.data.local.entity.CadastralPlotEntity
import com.example.data.local.entity.DeedPartyEntity
import com.example.data.local.entity.DeedPlotScheduleEntity
import com.example.data.local.entity.DeedRecordEntity
import com.example.data.local.entity.DocumentPageEntity
import com.example.data.local.entity.EvidenceAuditEntity
import com.example.data.local.entity.HcrRecordEntity
import com.example.data.local.entity.ImportAuditLogEntity
import com.example.data.local.entity.ImportQueueItemEntity
import com.example.data.local.entity.InheritorEntity
import com.example.data.local.entity.KhatianEntity
import com.example.data.local.entity.KhatianOwnerEntity
import com.example.data.local.entity.KhatianPlotEntity
import com.example.data.local.entity.LandChainLinkEntity
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.local.entity.LandOwnerEntity
import com.example.data.local.entity.MutationPartyEntity
import com.example.data.local.entity.MutationPlotEntity
import com.example.data.local.entity.MutationRecordEntity
import com.example.data.local.entity.OcrBlockEntity
import com.example.data.local.entity.SearchIndexEntity
import com.example.data.local.entity.WarishCertificateEntity

@Database(
    entities = [
        LandDocumentEntity::class,
        DocumentPageEntity::class,
        ImportQueueItemEntity::class,
        ImportAuditLogEntity::class,
        // Forensic Archive Entities
        OcrBlockEntity::class,
        HcrRecordEntity::class,
        CadastralMapEntity::class,
        CadastralPlotEntity::class,
        LandOwnerEntity::class,
        EvidenceAuditEntity::class,
        BatchJobEntity::class,
        SearchIndexEntity::class,
        // Warish & Inheritor Master Entities
        WarishCertificateEntity::class,
        InheritorEntity::class,
        // Structured Land Record Entities (CS, SA, RS, BRS, Deeds, Mutations, Title Chain)
        KhatianEntity::class,
        KhatianOwnerEntity::class,
        KhatianPlotEntity::class,
        DeedRecordEntity::class,
        DeedPartyEntity::class,
        DeedPlotScheduleEntity::class,
        MutationRecordEntity::class,
        MutationPartyEntity::class,
        MutationPlotEntity::class,
        LandChainLinkEntity::class
    ],
    version = 7,
    exportSchema = true
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
    abstract fun inheritorDao(): InheritorDao
    abstract fun extractedLandRecordDao(): ExtractedLandRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            db.query("PRAGMA table_info($table)").use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0 && cursor.getString(nameIdx) == column) {
                        return true
                    }
                }
            }
            return false
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "land_documents", "dateCaptured")) {
                    db.execSQL(
                        "ALTER TABLE land_documents ADD COLUMN dateCaptured INTEGER NOT NULL DEFAULT 0"
                    )
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `extracted_khatians` (
                        `id` TEXT NOT NULL,
                        `documentId` TEXT,
                        `surveyType` TEXT NOT NULL,
                        `khatianNo` TEXT NOT NULL,
                        `formerKhatianNo` TEXT,
                        `halKhatianNo` TEXT,
                        `district` TEXT NOT NULL,
                        `upazila` TEXT NOT NULL,
                        `mouza` TEXT NOT NULL,
                        `jlNo` TEXT NOT NULL,
                        `touziNo` TEXT,
                        `sheetNo` TEXT,
                        `annualRent` TEXT,
                        `cess` TEXT,
                        `isVerified` INTEGER NOT NULL,
                        `confidenceScore` REAL NOT NULL,
                        `extractionSource` TEXT NOT NULL,
                        `rawText` TEXT,
                        `remarks` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `warish_certificates` (
                        `id` TEXT NOT NULL,
                        `certificateNo` TEXT NOT NULL,
                        `certificateDate` TEXT NOT NULL,
                        `deceasedName` TEXT NOT NULL,
                        `fatherOrHusbandName` TEXT NOT NULL,
                        `motherName` TEXT NOT NULL,
                        `villageOrArea` TEXT NOT NULL,
                        `unionOrWard` TEXT NOT NULL,
                        `postOffice` TEXT NOT NULL,
                        `upazila` TEXT NOT NULL,
                        `district` TEXT NOT NULL,
                        `dateOfDeath` TEXT NOT NULL,
                        `issuerDesignation` TEXT NOT NULL,
                        `targetDagNumbersJson` TEXT NOT NULL,
                        `targetKhatianNumbersJson` TEXT NOT NULL,
                        `targetMouza` TEXT NOT NULL,
                        `totalHeirsCount` INTEGER NOT NULL,
                        `totalShareSum` REAL NOT NULL,
                        `isMasterDataset` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `evidence_audits` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `documentId` INTEGER NOT NULL,
                        `evidenceTier` TEXT NOT NULL,
                        `confidenceScore` REAL NOT NULL,
                        `hissaBalanced` INTEGER NOT NULL,
                        `discrepanciesJson` TEXT,
                        `chainNotes` TEXT,
                        `auditedBy` TEXT NOT NULL,
                        `auditedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `batch_jobs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `jobName` TEXT NOT NULL,
                        `totalFiles` INTEGER NOT NULL,
                        `processedFiles` INTEGER NOT NULL,
                        `duplicateFiles` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `errorMessage` TEXT,
                        `startedAt` INTEGER NOT NULL,
                        `completedAt` INTEGER
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "land_documents", "storedFilename")) {
                    db.execSQL("ALTER TABLE land_documents ADD COLUMN storedFilename TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "land_documents", "mimeType")) {
                    db.execSQL("ALTER TABLE land_documents ADD COLUMN mimeType TEXT NOT NULL DEFAULT 'application/octet-stream'")
                }
                if (!hasColumn(db, "land_documents", "sourceType")) {
                    db.execSQL("ALTER TABLE land_documents ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'ANDROID_SAF_FILE'")
                }
                if (!hasColumn(db, "land_documents", "googleDriveFileId")) {
                    db.execSQL("ALTER TABLE land_documents ADD COLUMN googleDriveFileId TEXT")
                }
                if (!hasColumn(db, "land_documents", "sha256")) {
                    db.execSQL("ALTER TABLE land_documents ADD COLUMN sha256 TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "land_documents", "importTimestamp")) {
                    db.execSQL("ALTER TABLE land_documents ADD COLUMN importTimestamp INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "land_documents", "processingStatus")) {
                    db.execSQL("ALTER TABLE land_documents ADD COLUMN processingStatus TEXT NOT NULL DEFAULT 'IMPORTED'")
                }
                if (!hasColumn(db, "land_documents", "processingError")) {
                    db.execSQL("ALTER TABLE land_documents ADD COLUMN processingError TEXT")
                }
                if (!hasColumn(db, "land_documents", "verificationStatus")) {
                    db.execSQL("ALTER TABLE land_documents ADD COLUMN verificationStatus TEXT NOT NULL DEFAULT 'UNVERIFIED'")
                }

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_land_documents_sha256` ON `land_documents` (`sha256`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_land_documents_processingStatus` ON `land_documents` (`processingStatus`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_land_documents_sourceType` ON `land_documents` (`sourceType`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `import_queue_items` (
                        `queueItemId` TEXT NOT NULL,
                        `batchId` TEXT NOT NULL,
                        `documentId` TEXT,
                        `originalFilename` TEXT NOT NULL,
                        `storedFilePath` TEXT,
                        `mimeType` TEXT NOT NULL,
                        `fileFormat` TEXT NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `sourceUri` TEXT NOT NULL,
                        `parentFolder` TEXT NOT NULL,
                        `googleDriveFileId` TEXT,
                        `sha256` TEXT NOT NULL,
                        `isDuplicate` INTEGER NOT NULL,
                        `duplicateOfDocumentId` TEXT,
                        `isZeroByte` INTEGER NOT NULL,
                        `surveyType` TEXT NOT NULL,
                        `mouzaName` TEXT,
                        `processingState` TEXT NOT NULL,
                        `progressFraction` REAL NOT NULL,
                        `errorMessage` TEXT,
                        `retryCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`queueItemId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_queue_items_batchId` ON `import_queue_items` (`batchId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_queue_items_processingState` ON `import_queue_items` (`processingState`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_queue_items_sha256` ON `import_queue_items` (`sha256`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_queue_items_sourceType` ON `import_queue_items` (`sourceType`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `import_audit_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `documentId` TEXT NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `sourceReference` TEXT NOT NULL,
                        `sha256` TEXT NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `actor` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_audit_logs_documentId` ON `import_audit_logs` (`documentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_audit_logs_eventType` ON `import_audit_logs` (`eventType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_audit_logs_timestamp` ON `import_audit_logs` (`timestamp`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "newaz_land_records.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
