package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ImportAuditLogEntity
import com.example.data.local.entity.ImportQueueItemEntity
import com.example.data.local.entity.LandDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LandDocumentDao {

    @Query("SELECT * FROM land_documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<LandDocumentEntity>>

    @Query("SELECT * FROM land_documents ORDER BY createdAt DESC")
    suspend fun getAllDocumentsList(): List<LandDocumentEntity>

    @Query("SELECT * FROM land_documents WHERE id = :id LIMIT 1")
    fun getDocumentById(id: String): Flow<LandDocumentEntity?>

    @Query("SELECT * FROM land_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentByIdOnce(id: String): LandDocumentEntity?

    @Query("SELECT * FROM land_documents WHERE sha256 = :sha256 AND sha256 != '' LIMIT 1")
    suspend fun findDocumentBySha256(sha256: String): LandDocumentEntity?

    @Query("SELECT * FROM land_documents WHERE sourceCategory = :category ORDER BY createdAt DESC")
    fun getDocumentsByCategory(category: String): Flow<List<LandDocumentEntity>>

    @Query("SELECT * FROM land_documents WHERE classifiedType = :type ORDER BY dateCaptured DESC")
    fun getDocumentsByType(type: String): Flow<List<LandDocumentEntity>>

    @Query("SELECT * FROM land_documents WHERE sourceType = :sourceType ORDER BY createdAt DESC")
    fun getDocumentsBySourceType(sourceType: String): Flow<List<LandDocumentEntity>>

    @Query("SELECT * FROM land_documents WHERE ownersSummary LIKE '%' || :ownerName || '%' ORDER BY dateCaptured DESC")
    fun getDocumentsByOwner(ownerName: String): Flow<List<LandDocumentEntity>>

    @Query("SELECT * FROM land_documents WHERE dateCaptured BETWEEN :startDate AND :endDate ORDER BY dateCaptured DESC")
    fun getDocumentsByDateCapturedRange(startDate: Long, endDate: Long): Flow<List<LandDocumentEntity>>

    @Query("SELECT * FROM land_documents WHERE classifiedType = :type AND ownersSummary LIKE '%' || :ownerName || '%' ORDER BY dateCaptured DESC")
    fun getDocumentsByTypeAndOwner(type: String, ownerName: String): Flow<List<LandDocumentEntity>>

    @Query("SELECT * FROM land_documents WHERE isZeroByte = 1 ORDER BY createdAt DESC")
    fun getZeroByteDocuments(): Flow<List<LandDocumentEntity>>

    @Query("""
        SELECT * FROM land_documents 
        WHERE title LIKE '%' || :query || '%' 
           OR sourceFileName LIKE '%' || :query || '%'
           OR primaryKhatianNo LIKE '%' || :query || '%' 
           OR primaryDagNo LIKE '%' || :query || '%' 
           OR primaryMouza LIKE '%' || :query || '%' 
           OR primaryDistrict LIKE '%' || :query || '%' 
           OR ownersSummary LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    fun searchDocuments(query: String): Flow<List<LandDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: LandDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocuments(docs: List<LandDocumentEntity>)

    @Update
    suspend fun updateDocument(doc: LandDocumentEntity)

    @Query("UPDATE land_documents SET processingStatus = :processingStatus, status = :status, processingError = :error, processedAt = :processedAt WHERE id = :id")
    suspend fun updateProcessingStatus(
        id: String,
        processingStatus: String,
        status: String,
        error: String?,
        processedAt: Long? = System.currentTimeMillis()
    )

    @Query("DELETE FROM land_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)

    @Query("DELETE FROM land_documents")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM land_documents")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM land_documents WHERE status = 'VERIFIED' OR status = 'CONFIRMED'")
    fun getVerifiedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM land_documents WHERE isZeroByte = 1")
    fun getZeroByteCount(): Flow<Int>

    // --- Durable Bulk Import Queue Queries (Phase 4) ---

    @Query("SELECT * FROM import_queue_items ORDER BY createdAt ASC")
    fun observeAllQueueItems(): Flow<List<ImportQueueItemEntity>>

    @Query("SELECT * FROM import_queue_items ORDER BY createdAt ASC")
    suspend fun getAllQueueItemsOnce(): List<ImportQueueItemEntity>

    @Query("SELECT * FROM import_queue_items WHERE processingState IN ('PENDING', 'READY_FOR_PROCESSING', 'IMPORTED') ORDER BY createdAt ASC")
    suspend fun getPendingQueueItems(): List<ImportQueueItemEntity>

    @Query("SELECT * FROM import_queue_items WHERE processingState = 'FAILED' ORDER BY createdAt ASC")
    suspend fun getFailedQueueItems(): List<ImportQueueItemEntity>

    @Query("SELECT * FROM import_queue_items WHERE queueItemId = :queueItemId LIMIT 1")
    suspend fun getQueueItemById(queueItemId: String): ImportQueueItemEntity?

    @Query("SELECT * FROM import_queue_items WHERE sha256 = :sha256 AND sha256 != '' LIMIT 1")
    suspend fun findQueueItemBySha256(sha256: String): ImportQueueItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: ImportQueueItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<ImportQueueItemEntity>)

    @Update
    suspend fun updateQueueItem(item: ImportQueueItemEntity)

    @Query("DELETE FROM import_queue_items WHERE queueItemId = :queueItemId")
    suspend fun deleteQueueItemById(queueItemId: String)

    @Query("DELETE FROM import_queue_items WHERE isDuplicate = 1")
    suspend fun removeDuplicateQueueItems()

    @Query("DELETE FROM import_queue_items")
    suspend fun clearQueueItems()

    // --- Immutable Import Audit Log Queries (Phase 5) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: ImportAuditLogEntity)

    @Query("SELECT * FROM import_audit_logs WHERE documentId = :documentId ORDER BY timestamp ASC")
    fun observeAuditLogsForDocument(documentId: String): Flow<List<ImportAuditLogEntity>>

    @Query("SELECT * FROM import_audit_logs WHERE documentId = :documentId ORDER BY timestamp ASC")
    suspend fun getAuditLogsForDocumentOnce(documentId: String): List<ImportAuditLogEntity>

    @Query("SELECT * FROM import_audit_logs ORDER BY timestamp DESC LIMIT 200")
    suspend fun getRecentAuditLogsOnce(): List<ImportAuditLogEntity>
}
