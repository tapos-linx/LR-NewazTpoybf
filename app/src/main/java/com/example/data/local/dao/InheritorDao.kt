package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.InheritorEntity
import com.example.data.local.entity.WarishCertificateEntity
import com.example.data.local.entity.WarishCertificateWithInheritors
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for Inheritors (Warishan) and Warish Certificates.
 * Handles persisting, querying, updating, searching, and managing the master dataset.
 */
@Dao
interface InheritorDao {

    // ==========================================
    // CERTIFICATES (সনদসমূহ)
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCertificate(certificate: WarishCertificateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCertificates(certificates: List<WarishCertificateEntity>)

    @Update
    suspend fun updateCertificate(certificate: WarishCertificateEntity)

    @Delete
    suspend fun deleteCertificate(certificate: WarishCertificateEntity)

    @Query("DELETE FROM warish_certificates WHERE id = :id")
    suspend fun deleteCertificateById(id: String)

    @Query("DELETE FROM warish_certificates")
    suspend fun deleteAllCertificates()

    @Query("SELECT COUNT(*) FROM warish_certificates")
    suspend fun getCertificateCount(): Int

    @Query("SELECT COUNT(*) FROM warish_certificates WHERE isMasterDataset = 1")
    suspend fun getMasterCertificateCount(): Int

    @Query("SELECT * FROM warish_certificates ORDER BY id ASC")
    fun getAllCertificates(): Flow<List<WarishCertificateEntity>>

    @Query("SELECT * FROM warish_certificates WHERE id = :id LIMIT 1")
    suspend fun getCertificateById(id: String): WarishCertificateEntity?

    @Query("SELECT * FROM warish_certificates WHERE certificateNo = :certificateNo LIMIT 1")
    suspend fun getCertificateByNo(certificateNo: String): WarishCertificateEntity?

    // ==========================================
    // INHERITORS / WARISHAN (ওয়ারিশগণ)
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInheritor(inheritor: InheritorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInheritors(inheritors: List<InheritorEntity>)

    @Update
    suspend fun updateInheritor(inheritor: InheritorEntity)

    @Delete
    suspend fun deleteInheritor(inheritor: InheritorEntity)

    @Query("DELETE FROM inheritors WHERE id = :id")
    suspend fun deleteInheritorById(id: String)

    @Query("DELETE FROM inheritors WHERE certificateId = :certificateId")
    suspend fun deleteInheritorsForCertificate(certificateId: String)

    @Query("DELETE FROM inheritors")
    suspend fun deleteAllInheritors()

    @Query("SELECT COUNT(*) FROM inheritors")
    suspend fun getInheritorCount(): Int

    @Query("SELECT * FROM inheritors ORDER BY id ASC")
    fun getAllInheritors(): Flow<List<InheritorEntity>>

    @Query("SELECT * FROM inheritors WHERE certificateId = :certificateId ORDER BY id ASC")
    fun getInheritorsForCertificate(certificateId: String): Flow<List<InheritorEntity>>

    @Query("SELECT * FROM inheritors WHERE certificateId = :certificateId ORDER BY id ASC")
    suspend fun getInheritorsForCertificateOnce(certificateId: String): List<InheritorEntity>

    @Query("""
        SELECT * FROM inheritors 
        WHERE name LIKE '%' || :query || '%' 
           OR nidOrBirthCert LIKE '%' || :query || '%' 
           OR relationship LIKE '%' || :query || '%'
        ORDER BY id ASC
    """)
    fun searchInheritors(query: String): Flow<List<InheritorEntity>>

    // ==========================================
    // COMPOSITE RELATIONS (সনদ ও ওয়ারিশ সমন্বিত)
    // ==========================================

    @Transaction
    @Query("SELECT * FROM warish_certificates ORDER BY id ASC")
    fun getAllCertificatesWithInheritors(): Flow<List<WarishCertificateWithInheritors>>

    @Transaction
    @Query("SELECT * FROM warish_certificates WHERE isMasterDataset = 1 ORDER BY id ASC")
    fun getMasterCertificatesWithInheritors(): Flow<List<WarishCertificateWithInheritors>>

    @Transaction
    @Query("SELECT * FROM warish_certificates ORDER BY id ASC")
    suspend fun getAllCertificatesWithInheritorsOnce(): List<WarishCertificateWithInheritors>

    @Transaction
    @Query("SELECT * FROM warish_certificates WHERE id = :id LIMIT 1")
    suspend fun getCertificateWithInheritorsById(id: String): WarishCertificateWithInheritors?

    @Transaction
    @Query("SELECT * FROM warish_certificates WHERE certificateNo = :certificateNo LIMIT 1")
    suspend fun getCertificateWithInheritorsByNo(certificateNo: String): WarishCertificateWithInheritors?

    /**
     * Atomically store or update a certificate along with all of its inheritors.
     */
    @Transaction
    suspend fun upsertCertificateWithInheritors(
        certificate: WarishCertificateEntity,
        inheritors: List<InheritorEntity>
    ) {
        insertCertificate(certificate)
        deleteInheritorsForCertificate(certificate.id)
        insertInheritors(inheritors)
    }

    /**
     * Atomically batch-persist multiple certificates and all their corresponding inheritors.
     */
    @Transaction
    suspend fun persistMasterCertificates(
        certificates: List<WarishCertificateEntity>,
        allInheritors: List<InheritorEntity>
    ) {
        insertCertificates(certificates)
        insertInheritors(allInheritors)
    }
}
