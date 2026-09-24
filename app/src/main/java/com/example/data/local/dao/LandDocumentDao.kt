package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.LandDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LandDocumentDao {

    @Query("SELECT * FROM land_documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<LandDocumentEntity>>

    @Query("SELECT * FROM land_documents WHERE id = :id LIMIT 1")
    fun getDocumentById(id: String): Flow<LandDocumentEntity?>

    @Query("SELECT * FROM land_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentByIdOnce(id: String): LandDocumentEntity?

    @Query("SELECT * FROM land_documents WHERE sourceCategory = :category ORDER BY createdAt DESC")
    fun getDocumentsByCategory(category: String): Flow<List<LandDocumentEntity>>

    @Query("SELECT * FROM land_documents WHERE classifiedType = :type ORDER BY dateCaptured DESC")
    fun getDocumentsByType(type: String): Flow<List<LandDocumentEntity>>

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

    @Query("DELETE FROM land_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)

    @Query("DELETE FROM land_documents")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM land_documents")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM land_documents WHERE status = 'VERIFIED'")
    fun getVerifiedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM land_documents WHERE isZeroByte = 1")
    fun getZeroByteCount(): Flow<Int>
}
