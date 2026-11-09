package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.DeedPartyEntity
import com.example.data.local.entity.DeedPlotScheduleEntity
import com.example.data.local.entity.DeedRecordEntity
import com.example.data.local.entity.DeedWithDetails
import com.example.data.local.entity.KhatianEntity
import com.example.data.local.entity.KhatianOwnerEntity
import com.example.data.local.entity.KhatianPlotEntity
import com.example.data.local.entity.KhatianWithDetails
import com.example.data.local.entity.LandChainLinkEntity
import com.example.data.local.entity.MutationPartyEntity
import com.example.data.local.entity.MutationPlotEntity
import com.example.data.local.entity.MutationRecordEntity
import com.example.data.local.entity.MutationWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtractedLandRecordDao {

    // ========================================================================
    // KHATIAN QUERIES (CS, SA, RS, BRS)
    // ========================================================================

    @Transaction
    @Query("SELECT * FROM extracted_khatians ORDER BY createdAt DESC")
    fun getAllKhatiansWithDetails(): Flow<List<KhatianWithDetails>>

    @Transaction
    @Query("SELECT * FROM extracted_khatians WHERE surveyType = :surveyType ORDER BY khatianNo ASC")
    fun getKhatiansBySurveyType(surveyType: String): Flow<List<KhatianWithDetails>>

    @Transaction
    @Query("SELECT * FROM extracted_khatians WHERE id = :id LIMIT 1")
    fun getKhatianWithDetailsById(id: String): Flow<KhatianWithDetails?>

    @Transaction
    @Query("SELECT * FROM extracted_khatians WHERE id = :id LIMIT 1")
    suspend fun getKhatianWithDetailsByIdOnce(id: String): KhatianWithDetails?

    @Transaction
    @Query("SELECT * FROM extracted_khatians WHERE mouza = :mouza AND surveyType = :surveyType ORDER BY khatianNo ASC")
    fun getKhatiansByMouzaAndSurvey(mouza: String, surveyType: String): Flow<List<KhatianWithDetails>>

    @Transaction
    @Query("SELECT * FROM extracted_khatians WHERE khatianNo = :khatianNo AND mouza = :mouza LIMIT 1")
    suspend fun findKhatianByNoAndMouza(khatianNo: String, mouza: String): KhatianWithDetails?

    @Query("SELECT * FROM extracted_khatians WHERE documentId = :documentId LIMIT 1")
    suspend fun getKhatianByDocumentId(documentId: String): KhatianEntity?

    @Transaction
    @Query("""
        SELECT DISTINCT k.* FROM extracted_khatians k
        LEFT JOIN khatian_owners o ON k.id = o.khatianId
        LEFT JOIN khatian_plots p ON k.id = p.khatianId
        WHERE k.khatianNo LIKE '%' || :query || '%'
           OR k.formerKhatianNo LIKE '%' || :query || '%'
           OR k.halKhatianNo LIKE '%' || :query || '%'
           OR k.mouza LIKE '%' || :query || '%'
           OR o.ownerName LIKE '%' || :query || '%'
           OR p.dagNo LIKE '%' || :query || '%'
        ORDER BY k.createdAt DESC
    """)
    fun searchKhatians(query: String): Flow<List<KhatianWithDetails>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKhatian(khatian: KhatianEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKhatianOwners(owners: List<KhatianOwnerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKhatianPlots(plots: List<KhatianPlotEntity>)

    @Transaction
    suspend fun insertFullKhatian(
        khatian: KhatianEntity,
        owners: List<KhatianOwnerEntity>,
        plots: List<KhatianPlotEntity>
    ) {
        insertKhatian(khatian)
        if (owners.isNotEmpty()) insertKhatianOwners(owners)
        if (plots.isNotEmpty()) insertKhatianPlots(plots)
    }

    @Update
    suspend fun updateKhatian(khatian: KhatianEntity)

    @Query("DELETE FROM extracted_khatians WHERE id = :id")
    suspend fun deleteKhatianById(id: String)

    @Query("SELECT COUNT(*) FROM extracted_khatians")
    fun getKhatianCount(): Flow<Int>

    // ========================================================================
    // DEED QUERIES (রেজিস্ট্রি দলিল)
    // ========================================================================

    @Transaction
    @Query("SELECT * FROM extracted_deeds ORDER BY deedYear DESC, createdAt DESC")
    fun getAllDeedsWithDetails(): Flow<List<DeedWithDetails>>

    @Transaction
    @Query("SELECT * FROM extracted_deeds WHERE id = :id LIMIT 1")
    fun getDeedWithDetailsById(id: String): Flow<DeedWithDetails?>

    @Transaction
    @Query("SELECT * FROM extracted_deeds WHERE id = :id LIMIT 1")
    suspend fun getDeedWithDetailsByIdOnce(id: String): DeedWithDetails?

    @Transaction
    @Query("SELECT * FROM extracted_deeds WHERE deedType = :deedType ORDER BY deedYear DESC")
    fun getDeedsByType(deedType: String): Flow<List<DeedWithDetails>>

    @Transaction
    @Query("SELECT * FROM extracted_deeds WHERE mouza = :mouza ORDER BY deedYear DESC")
    fun getDeedsByMouza(mouza: String): Flow<List<DeedWithDetails>>

    @Transaction
    @Query("""
        SELECT DISTINCT d.* FROM extracted_deeds d
        LEFT JOIN deed_parties p ON d.id = p.deedId
        LEFT JOIN deed_plot_schedules s ON d.id = s.deedId
        WHERE d.deedNo LIKE '%' || :query || '%'
           OR d.subRegistryOffice LIKE '%' || :query || '%'
           OR d.mouza LIKE '%' || :query || '%'
           OR p.name LIKE '%' || :query || '%'
           OR p.nidNumber LIKE '%' || :query || '%'
           OR s.dagNo LIKE '%' || :query || '%'
           OR s.rsKhatianNo LIKE '%' || :query || '%'
        ORDER BY d.createdAt DESC
    """)
    fun searchDeeds(query: String): Flow<List<DeedWithDetails>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeed(deed: DeedRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeedParties(parties: List<DeedPartyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeedPlotSchedules(plots: List<DeedPlotScheduleEntity>)

    @Transaction
    suspend fun insertFullDeed(
        deed: DeedRecordEntity,
        parties: List<DeedPartyEntity>,
        schedules: List<DeedPlotScheduleEntity>
    ) {
        insertDeed(deed)
        if (parties.isNotEmpty()) insertDeedParties(parties)
        if (schedules.isNotEmpty()) insertDeedPlotSchedules(schedules)
    }

    @Update
    suspend fun updateDeed(deed: DeedRecordEntity)

    @Query("DELETE FROM extracted_deeds WHERE id = :id")
    suspend fun deleteDeedById(id: String)

    @Query("SELECT COUNT(*) FROM extracted_deeds")
    fun getDeedCount(): Flow<Int>

    // ========================================================================
    // MUTATION QUERIES (নামজারি ও জমাভাগ)
    // ========================================================================

    @Transaction
    @Query("SELECT * FROM extracted_mutations ORDER BY createdAt DESC")
    fun getAllMutationsWithDetails(): Flow<List<MutationWithDetails>>

    @Transaction
    @Query("SELECT * FROM extracted_mutations WHERE id = :id LIMIT 1")
    fun getMutationWithDetailsById(id: String): Flow<MutationWithDetails?>

    @Transaction
    @Query("SELECT * FROM extracted_mutations WHERE id = :id LIMIT 1")
    suspend fun getMutationWithDetailsByIdOnce(id: String): MutationWithDetails?

    @Transaction
    @Query("SELECT * FROM extracted_mutations WHERE mutationStatus = :status ORDER BY createdAt DESC")
    fun getMutationsByStatus(status: String): Flow<List<MutationWithDetails>>

    @Transaction
    @Query("SELECT * FROM extracted_mutations WHERE mouza = :mouza ORDER BY createdAt DESC")
    fun getMutationsByMouza(mouza: String): Flow<List<MutationWithDetails>>

    @Transaction
    @Query("""
        SELECT DISTINCT m.* FROM extracted_mutations m
        LEFT JOIN mutation_parties p ON m.id = p.mutationId
        LEFT JOIN mutation_plots pl ON m.id = pl.mutationId
        WHERE m.caseNumber LIKE '%' || :query || '%'
           OR m.proposedKhatianNo LIKE '%' || :query || '%'
           OR m.dischargedKhatianNo LIKE '%' || :query || '%'
           OR m.mouza LIKE '%' || :query || '%'
           OR p.name LIKE '%' || :query || '%'
           OR pl.dagNo LIKE '%' || :query || '%'
        ORDER BY m.createdAt DESC
    """)
    fun searchMutations(query: String): Flow<List<MutationWithDetails>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMutation(mutation: MutationRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMutationParties(parties: List<MutationPartyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMutationPlots(plots: List<MutationPlotEntity>)

    @Transaction
    suspend fun insertFullMutation(
        mutation: MutationRecordEntity,
        parties: List<MutationPartyEntity>,
        plots: List<MutationPlotEntity>
    ) {
        insertMutation(mutation)
        if (parties.isNotEmpty()) insertMutationParties(parties)
        if (plots.isNotEmpty()) insertMutationPlots(plots)
    }

    @Update
    suspend fun updateMutation(mutation: MutationRecordEntity)

    @Query("DELETE FROM extracted_mutations WHERE id = :id")
    suspend fun deleteMutationById(id: String)

    @Query("SELECT COUNT(*) FROM extracted_mutations")
    fun getMutationCount(): Flow<Int>

    // ========================================================================
    // CROSS-SURVEY TITLE CHAIN LINKS (ধারাবাহিক স্বত্ব ও যোগসূত্র)
    // ========================================================================

    @Query("SELECT * FROM land_chain_links WHERE mouza = :mouza ORDER BY createdAt DESC")
    fun getChainLinksByMouza(mouza: String): Flow<List<LandChainLinkEntity>>

    @Query("""
        SELECT * FROM land_chain_links 
        WHERE (fromDagNo = :dagNo OR toDagNo = :dagNo)
          AND mouza = :mouza
        ORDER BY createdAt ASC
    """)
    fun getChainLinksForDag(dagNo: String, mouza: String): Flow<List<LandChainLinkEntity>>

    @Query("""
        SELECT * FROM land_chain_links 
        WHERE (fromKhatianNo = :khatianNo OR toKhatianNo = :khatianNo)
          AND mouza = :mouza
        ORDER BY createdAt ASC
    """)
    fun getChainLinksForKhatian(khatianNo: String, mouza: String): Flow<List<LandChainLinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChainLink(link: LandChainLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChainLinks(links: List<LandChainLinkEntity>)

    @Delete
    suspend fun deleteChainLink(link: LandChainLinkEntity)
}
