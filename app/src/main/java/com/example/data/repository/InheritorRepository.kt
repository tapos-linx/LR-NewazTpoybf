package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.dao.InheritorDao
import com.example.data.local.entity.InheritorEntity
import com.example.data.local.entity.WarishCertificateEntity
import com.example.data.local.entity.WarishCertificateWithInheritors
import com.example.domain.matcher.HeirRecord
import com.example.domain.matcher.WarishCertificate
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository for managing Warish Certificates and Inheritors (Warishan).
 * Provides persistence, updates, and reactive streaming of the Master Dataset
 * backed by Room Database.
 */
class InheritorRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    private val inheritorDao: InheritorDao = database.inheritorDao()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listStringType = Types.newParameterizedType(List::class.java, String::class.java)
    private val listStringAdapter = moshi.adapter<List<String>>(listStringType)

    /**
     * Reactive stream of all Warish certificates with their inheritors as domain models.
     */
    val allCertificatesFlow: Flow<List<WarishCertificate>> =
        inheritorDao.getAllCertificatesWithInheritors().map { list ->
            list.map { it.toDomainModel() }
        }

    /**
     * Reactive stream of only Master Dataset certificates.
     */
    val masterCertificatesFlow: Flow<List<WarishCertificate>> =
        inheritorDao.getMasterCertificatesWithInheritors().map { list ->
            list.map { it.toDomainModel() }
        }

    /**
     * Total count of certificates stored in Room.
     */
    suspend fun getCertificateCount(): Int = withContext(Dispatchers.IO) {
        inheritorDao.getCertificateCount()
    }

    /**
     * Total count of inheritors stored in Room.
     */
    suspend fun getInheritorCount(): Int = withContext(Dispatchers.IO) {
        inheritorDao.getInheritorCount()
    }

    /**
     * Fetch a specific certificate with its inheritors by certificate ID.
     */
    suspend fun getCertificateById(id: String): WarishCertificate? = withContext(Dispatchers.IO) {
        inheritorDao.getCertificateWithInheritorsById(id)?.toDomainModel()
    }

    /**
     * Fetch a specific certificate with its inheritors by certificate number.
     */
    suspend fun getCertificateByNo(certificateNo: String): WarishCertificate? = withContext(Dispatchers.IO) {
        inheritorDao.getCertificateWithInheritorsByNo(certificateNo)?.toDomainModel()
    }

    /**
     * Search inheritors by name, NID, or relationship.
     */
    fun searchInheritors(query: String): Flow<List<InheritorEntity>> {
        return inheritorDao.searchInheritors(query)
    }

    /**
     * Store or update a single certificate and all its inheritors in Room.
     */
    suspend fun saveOrUpdateCertificate(certificate: WarishCertificate) = withContext(Dispatchers.IO) {
        val certEntity = certificate.toEntity()
        val heirEntities = certificate.heirs.map { it.toEntity(certificate.id, certificate.certificateNo) }
        inheritorDao.upsertCertificateWithInheritors(certEntity, heirEntities)
    }

    /**
     * Update an individual inheritor's record in Room.
     */
    suspend fun updateInheritor(inheritor: InheritorEntity) = withContext(Dispatchers.IO) {
        inheritorDao.updateInheritor(inheritor)
    }

    /**
     * Delete a certificate and all cascaded inheritors by ID.
     */
    suspend fun deleteCertificateById(id: String) = withContext(Dispatchers.IO) {
        inheritorDao.deleteCertificateById(id)
    }

    /**
     * Persists provided certificates and heirs into Room database.
     */
    suspend fun persistMasterCertificatesList(
        masterCertificates: List<WarishCertificate>,
        forceReset: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val currentCount = inheritorDao.getMasterCertificateCount()
        val currentHeirCount = inheritorDao.getInheritorCount()
        val expectedHeirCount = masterCertificates.sumOf { it.heirs.size }
        if (currentCount < masterCertificates.size || currentHeirCount != expectedHeirCount || forceReset) {
            val certEntities = masterCertificates.map { it.toEntity(isMaster = true) }
            val heirEntities = masterCertificates.flatMap { cert ->
                cert.heirs.map { heir -> heir.toEntity(cert.id, cert.certificateNo, isMaster = true) }
            }
            inheritorDao.persistMasterCertificates(certEntities, heirEntities)
        }
    }

    // ==========================================
    // CONVERTERS & MAPPERS
    // ==========================================

    private fun WarishCertificateWithInheritors.toDomainModel(): WarishCertificate {
        val dags = try {
            listStringAdapter.fromJson(certificate.targetDagNumbersJson) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val khatians = try {
            listStringAdapter.fromJson(certificate.targetKhatianNumbersJson) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        return WarishCertificate(
            id = certificate.id,
            certificateNo = certificate.certificateNo,
            certificateDate = certificate.certificateDate,
            deceasedName = certificate.deceasedName,
            fatherOrHusbandName = certificate.fatherOrHusbandName,
            motherName = certificate.motherName,
            villageOrArea = certificate.villageOrArea,
            unionOrWard = certificate.unionOrWard,
            postOffice = certificate.postOffice,
            upazila = certificate.upazila,
            district = certificate.district,
            dateOfDeath = certificate.dateOfDeath,
            issuerDesignation = certificate.issuerDesignation,
            targetDagNumbers = dags,
            targetMouza = certificate.targetMouza,
            targetKhatianNumbers = khatians,
            notes = certificate.notes,
            heirs = inheritors.sortedWith(
                compareBy(
                    { it.id.substringAfterLast("-h").toIntOrNull() ?: Int.MAX_VALUE },
                    { it.id }
                )
            ).map { it.toDomainModel() }
        )
    }

    private fun InheritorEntity.toDomainModel(): HeirRecord {
        return HeirRecord(
            id = id,
            serialNo = serialNo,
            name = name,
            relationship = relationship,
            shareHissa = shareHissa,
            shareFractionLabel = shareFractionLabel,
            nidOrBirthCert = nidOrBirthCert,
            dateOfBirth = dateOfBirth,
            remarks = remarks,
            isDeceased = isDeceased
        )
    }

    private fun WarishCertificate.toEntity(isMaster: Boolean = true): WarishCertificateEntity {
        return WarishCertificateEntity(
            id = id,
            certificateNo = certificateNo,
            certificateDate = certificateDate,
            deceasedName = deceasedName,
            fatherOrHusbandName = fatherOrHusbandName,
            motherName = motherName,
            villageOrArea = villageOrArea,
            unionOrWard = unionOrWard,
            postOffice = postOffice,
            upazila = upazila,
            district = district,
            dateOfDeath = dateOfDeath,
            issuerDesignation = issuerDesignation,
            targetDagNumbersJson = listStringAdapter.toJson(targetDagNumbers),
            targetKhatianNumbersJson = listStringAdapter.toJson(targetKhatianNumbers),
            targetMouza = targetMouza,
            totalHeirsCount = heirs.size,
            totalShareSum = heirs.sumOf { it.shareHissa },
            isMasterDataset = isMaster,
            notes = notes,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun HeirRecord.toEntity(
        certId: String,
        certNo: String,
        isMaster: Boolean = true
    ): InheritorEntity {
        return InheritorEntity(
            id = id,
            certificateId = certId,
            certificateNo = certNo,
            serialNo = serialNo,
            name = name,
            relationship = relationship,
            shareHissa = shareHissa,
            shareFractionLabel = shareFractionLabel,
            nidOrBirthCert = nidOrBirthCert,
            dateOfBirth = dateOfBirth,
            remarks = remarks,
            isDeceased = isDeceased,
            isMasterRecord = isMaster,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}
