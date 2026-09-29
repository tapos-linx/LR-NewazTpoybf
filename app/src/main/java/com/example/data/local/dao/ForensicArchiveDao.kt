package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrBlockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(block: OcrBlockEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blocks: List<OcrBlockEntity>)

    @Query("SELECT * FROM ocr_blocks WHERE documentId = :documentId ORDER BY pageNumber ASC, id ASC")
    fun getBlocksByDocument(documentId: Long): Flow<List<OcrBlockEntity>>

    @Query("SELECT * FROM ocr_blocks WHERE isUnclear = 1")
    fun getUnclearBlocks(): Flow<List<OcrBlockEntity>>
}

@Dao
interface HcrRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: HcrRecordEntity): Long

    @Update
    suspend fun update(record: HcrRecordEntity)

    @Query("SELECT * FROM hcr_records WHERE documentId = :documentId")
    fun getByDocument(documentId: Long): Flow<List<HcrRecordEntity>>

    @Query("SELECT * FROM hcr_records WHERE requiresManualReview = 1")
    fun getPendingReview(): Flow<List<HcrRecordEntity>>

    @Query("SELECT * FROM hcr_records ORDER BY id DESC")
    fun getAll(): Flow<List<HcrRecordEntity>>
}

@Dao
interface CadastralMapDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMap(map: CadastralMapEntity): Long

    @Query("SELECT * FROM cadastral_maps WHERE mouzaName = :mouza AND surveyType = :surveyType LIMIT 1")
    suspend fun findMap(mouza: String, surveyType: String): CadastralMapEntity?

    @Query("SELECT * FROM cadastral_maps ORDER BY id DESC")
    fun getAllMaps(): Flow<List<CadastralMapEntity>>
}

@Dao
interface CadastralPlotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlot(plot: CadastralPlotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plots: List<CadastralPlotEntity>)

    @Query("SELECT * FROM cadastral_plots WHERE mapId = :mapId")
    fun getPlotsByMap(mapId: Long): Flow<List<CadastralPlotEntity>>

    @Query("SELECT * FROM cadastral_plots WHERE dagNo = :dagNo OR dagNoBn = :dagNo")
    suspend fun findByDag(dagNo: String): List<CadastralPlotEntity>

    @Query("SELECT * FROM cadastral_plots WHERE isMatchedInheritance = 1")
    fun getHighlightedPlots(): Flow<List<CadastralPlotEntity>>
}

@Dao
interface LandOwnerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(owner: LandOwnerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(owners: List<LandOwnerEntity>)

    @Query("SELECT * FROM land_owners WHERE documentId = :documentId")
    fun getOwnersByDocument(documentId: Long): Flow<List<LandOwnerEntity>>

    @Query("SELECT * FROM land_owners WHERE ownerName LIKE '%' || :query || '%'")
    suspend fun searchOwners(query: String): List<LandOwnerEntity>
}

@Dao
interface EvidenceAuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audit: EvidenceAuditEntity): Long

    @Query("SELECT * FROM evidence_audits WHERE documentId = :documentId ORDER BY auditedAt DESC LIMIT 1")
    suspend fun getLatestAudit(documentId: Long): EvidenceAuditEntity?

    @Query("SELECT * FROM evidence_audits WHERE evidenceTier = :tier")
    fun getAuditsByTier(tier: String): Flow<List<EvidenceAuditEntity>>
}

@Dao
interface BatchJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: BatchJobEntity): Long

    @Update
    suspend fun update(job: BatchJobEntity)

    @Query("SELECT * FROM batch_jobs ORDER BY startedAt DESC")
    fun getAllJobs(): Flow<List<BatchJobEntity>>
}

@Dao
interface SearchIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(indexes: List<SearchIndexEntity>)

    @Query("SELECT documentId FROM search_index WHERE keyword LIKE :query || '%'")
    suspend fun searchDocumentIds(query: String): List<Long>
}
