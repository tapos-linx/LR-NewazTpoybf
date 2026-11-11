package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.dao.ExtractedLandRecordDao
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository abstracting offline Room persistence for extracted land records:
 * CS, SA, RS, BRS Khatians, Registered Deeds, and Mutation Cases.
 */
class ExtractedLandRecordRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    private val dao: ExtractedLandRecordDao = database.extractedLandRecordDao()

    // Observables
    val allKhatians: Flow<List<KhatianWithDetails>> = dao.getAllKhatiansWithDetails()
    val allDeeds: Flow<List<DeedWithDetails>> = dao.getAllDeedsWithDetails()
    val allMutations: Flow<List<MutationWithDetails>> = dao.getAllMutationsWithDetails()

    val khatianCount: Flow<Int> = dao.getKhatianCount()
    val deedCount: Flow<Int> = dao.getDeedCount()
    val mutationCount: Flow<Int> = dao.getMutationCount()

    // Filtered Khatian Queries
    fun getKhatiansBySurveyType(surveyType: String): Flow<List<KhatianWithDetails>> =
        dao.getKhatiansBySurveyType(surveyType)

    fun getKhatianDetailsById(id: String): Flow<KhatianWithDetails?> =
        dao.getKhatianWithDetailsById(id)

    suspend fun getKhatianDetailsByIdOnce(id: String): KhatianWithDetails? =
        dao.getKhatianWithDetailsByIdOnce(id)

    fun searchKhatians(query: String): Flow<List<KhatianWithDetails>> =
        dao.searchKhatians(query)

    // Filtered Deed Queries
    fun getDeedDetailsById(id: String): Flow<DeedWithDetails?> =
        dao.getDeedWithDetailsById(id)

    suspend fun getDeedDetailsByIdOnce(id: String): DeedWithDetails? =
        dao.getDeedWithDetailsByIdOnce(id)

    fun getDeedsByType(deedType: String): Flow<List<DeedWithDetails>> =
        dao.getDeedsByType(deedType)

    fun searchDeeds(query: String): Flow<List<DeedWithDetails>> =
        dao.searchDeeds(query)

    // Filtered Mutation Queries
    fun getMutationDetailsById(id: String): Flow<MutationWithDetails?> =
        dao.getMutationWithDetailsById(id)

    suspend fun getMutationDetailsByIdOnce(id: String): MutationWithDetails? =
        dao.getMutationWithDetailsByIdOnce(id)

    fun getMutationsByStatus(status: String): Flow<List<MutationWithDetails>> =
        dao.getMutationsByStatus(status)

    fun searchMutations(query: String): Flow<List<MutationWithDetails>> =
        dao.searchMutations(query)

    // Title Chain Links
    fun getChainLinksByMouza(mouza: String): Flow<List<LandChainLinkEntity>> =
        dao.getChainLinksByMouza(mouza)

    fun getChainLinksForDag(dagNo: String, mouza: String): Flow<List<LandChainLinkEntity>> =
        dao.getChainLinksForDag(dagNo, mouza)

    fun getChainLinksForKhatian(khatianNo: String, mouza: String): Flow<List<LandChainLinkEntity>> =
        dao.getChainLinksForKhatian(khatianNo, mouza)

    // Persistence Actions
    suspend fun saveFullKhatian(
        khatian: KhatianEntity,
        owners: List<KhatianOwnerEntity>,
        plots: List<KhatianPlotEntity>
    ) = withContext(Dispatchers.IO) {
        dao.insertFullKhatian(khatian, owners, plots)
    }

    suspend fun saveFullDeed(
        deed: DeedRecordEntity,
        parties: List<DeedPartyEntity>,
        schedules: List<DeedPlotScheduleEntity>
    ) = withContext(Dispatchers.IO) {
        dao.insertFullDeed(deed, parties, schedules)
    }

    suspend fun saveFullMutation(
        mutation: MutationRecordEntity,
        parties: List<MutationPartyEntity>,
        plots: List<MutationPlotEntity>
    ) = withContext(Dispatchers.IO) {
        dao.insertFullMutation(mutation, parties, plots)
    }

    suspend fun saveChainLink(link: LandChainLinkEntity) = withContext(Dispatchers.IO) {
        dao.insertChainLink(link)
    }

    suspend fun deleteKhatian(id: String) = withContext(Dispatchers.IO) {
        dao.deleteKhatianById(id)
    }

    suspend fun deleteDeed(id: String) = withContext(Dispatchers.IO) {
        dao.deleteDeedById(id)
    }

    suspend fun deleteMutation(id: String) = withContext(Dispatchers.IO) {
        dao.deleteMutationById(id)
    }
}
