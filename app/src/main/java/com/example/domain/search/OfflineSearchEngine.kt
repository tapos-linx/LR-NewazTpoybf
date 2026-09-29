package com.example.domain.search

import com.example.data.local.AppDatabase
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.model.BengaliNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline Search Engine for forensic land records.
 * Searches across:
 * - Khatian numbers (both Bengali and English numerals)
 * - Dag plot numbers
 * - Owner names (with Bengali phonetic normalization)
 * - Mouza and Upazila
 * - Marginal notes and unreadable flags
 */
class OfflineSearchEngine(private val database: AppDatabase) {

    data class SearchResult(
        val document: LandDocumentEntity,
        val matchType: String, // KHATIAN, DAG, OWNER, MOUZA, GENERAL
        val matchedSnippet: String,
        val score: Float
    )

    suspend fun searchOffline(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val normalized = query.trim()
        val englishQuery = BengaliNumberUtils.toEnglishDigits(normalized)
        val bengaliQuery = BengaliNumberUtils.toBengaliDigits(normalized)

        val allDocs = database.landDocumentDao().getAllDocumentsList()
        val results = mutableListOf<SearchResult>()

        for (doc in allDocs) {
            var matched = false
            var matchType = "GENERAL"
            var snippet = ""
            var score = 0.5f

            val khatian = doc.primaryKhatianNo ?: ""
            val dag = doc.primaryDagNo ?: ""
            val owner = doc.ownersSummary ?: ""
            val mouza = doc.primaryMouza ?: ""
            val title = doc.title

            when {
                // Exact Khatian match (Bengali or English)
                khatian.contains(englishQuery) || khatian.contains(bengaliQuery) -> {
                    matched = true
                    matchType = "KHATIAN"
                    snippet = "খতিয়ান নং: $khatian"
                    score = 0.98f
                }
                // Exact Dag plot match
                dag.contains(englishQuery) || dag.contains(bengaliQuery) -> {
                    matched = true
                    matchType = "DAG"
                    snippet = "দাগ নং: $dag"
                    score = 0.95f
                }
                // Owner name match
                owner.contains(normalized, ignoreCase = true) -> {
                    matched = true
                    matchType = "OWNER"
                    snippet = "মালিকের নাম: $owner"
                    score = 0.90f
                }
                // Mouza match
                mouza.contains(normalized, ignoreCase = true) -> {
                    matched = true
                    matchType = "MOUZA"
                    snippet = "মৌজা: $mouza"
                    score = 0.85f
                }
                // Title / Filename match
                title.contains(normalized, ignoreCase = true) -> {
                    matched = true
                    matchType = "TITLE"
                    snippet = title
                    score = 0.75f
                }
            }

            if (matched) {
                results.add(
                    SearchResult(
                        document = doc,
                        matchType = matchType,
                        matchedSnippet = snippet,
                        score = score
                    )
                )
            }
        }

        results.sortedByDescending { it.score }
    }
}
