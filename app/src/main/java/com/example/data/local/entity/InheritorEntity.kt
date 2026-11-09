package com.example.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Room Entity representing a Warish (Inheritance) Certificate.
 * Stores master metadata for Bangladeshi Warish certificates including deceased details,
 * official issuance metadata, geographical location, and target land parcel linkages.
 */
@Entity(
    tableName = "warish_certificates",
    indices = [
        Index(value = ["certificateNo"], unique = true),
        Index(value = ["deceasedName"]),
        Index(value = ["villageOrArea"]),
        Index(value = ["unionOrWard"]),
        Index(value = ["upazila"]),
        Index(value = ["district"]),
        Index(value = ["isMasterDataset"])
    ]
)
data class WarishCertificateEntity(
    @PrimaryKey
    val id: String, // e.g. "major-warish-1"
    val certificateNo: String, // e.g. "২০২৬৫৪১১৮১২০০০০০২"
    val certificateDate: String = "১৬-০৯-২০২৬",
    val deceasedName: String, // e.g. "ফিরুজ মিয়া"
    val fatherOrHusbandName: String = "",
    val motherName: String = "",
    val villageOrArea: String = "গোয়ালী",
    val unionOrWard: String = "কাইতলা (দক্ষিণ), ওয়ার্ড: ০৮",
    val postOffice: String = "কাইতলা - ৩৪৭৭",
    val upazila: String = "নবীনগর",
    val district: String = "ব্রাহ্মণবাড়িয়া",
    val dateOfDeath: String = "",
    val issuerDesignation: String = "চেয়ারম্যান, কাইতলা (দক্ষিণ) ইউনিয়ন পরিষদ",
    val targetDagNumbersJson: String = "[]", // Serialized JSON array of target dag numbers
    val targetKhatianNumbersJson: String = "[]", // Serialized JSON array of target khatian numbers
    val targetMouza: String = "গোয়ালী",
    val totalHeirsCount: Int = 0,
    val totalShareSum: Double = 1.0,
    val isMasterDataset: Boolean = true,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Room Entity representing an individual Inheritor (Warishan).
 * Implements 1-to-many relationship with WarishCertificateEntity via certificateId,
 * cascading deletion on parent certificate deletion.
 */
@Entity(
    tableName = "inheritors",
    foreignKeys = [
        ForeignKey(
            entity = WarishCertificateEntity::class,
            parentColumns = ["id"],
            childColumns = ["certificateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["certificateId"]),
        Index(value = ["certificateNo"]),
        Index(value = ["name"]),
        Index(value = ["nidOrBirthCert"]),
        Index(value = ["relationship"]),
        Index(value = ["isMasterRecord"])
    ]
)
data class InheritorEntity(
    @PrimaryKey
    val id: String, // e.g. "w1-h1"
    val certificateId: String, // foreign key to warish_certificates.id
    val certificateNo: String, // certificate number for fast querying
    val serialNo: String, // e.g. "১", "২"
    val name: String, // e.g. "রফিকুল ইসলাম"
    val relationship: String, // "পুত্র", "কন্যা", "১ম স্ত্রী", "২য় স্ত্রী", etc.
    val shareHissa: Double, // Faraiz decimal share e.g. 0.1750
    val shareFractionLabel: String, // e.g. "২ আনা ১৬ গণ্ডা"
    val nidOrBirthCert: String = "-",
    val dateOfBirth: String = "-",
    val remarks: String = "জীবিত", // "জীবিত", "মৃত"
    val isDeceased: Boolean = false,
    val isMasterRecord: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Composite relational POJO mapping a Warish Certificate with all its Inheritors.
 */
data class WarishCertificateWithInheritors(
    @Embedded
    val certificate: WarishCertificateEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "certificateId"
    )
    val inheritors: List<InheritorEntity>
)
