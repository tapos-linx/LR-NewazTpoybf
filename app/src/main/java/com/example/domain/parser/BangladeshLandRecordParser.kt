package com.example.domain.parser

import com.example.data.model.BengaliNumberUtils
import com.example.data.model.ConfidenceLevel
import com.example.data.model.FieldWithConfidence
import com.example.data.model.LandRecordData
import com.example.data.model.LandRecordType
import com.example.data.model.OwnerRecord

/**
 * Deterministic Bengali + English parser for Bangladesh land records
 * (CS, SA, RS, BS/BRS, Namjari/Mutation, Deeds, Dakhila, and Warish certificates).
 *
 * STRICT FORENSIC RULE:
 * Never invents default districts, Mouzas, JL numbers, Khatian numbers, Dag numbers,
 * land areas, or fallback owners when fields are absent from `rawOcrText`.
 * Missing fields return `value = ""` with `ConfidenceLevel.UNCERTAIN`.
 */
object BangladeshLandRecordParser {

    fun parse(rawOcrText: String, filenameHint: String = "", pageIndex: Int = 1): LandRecordData {
        val combinedText = "$filenameHint\n$rawOcrText"

        // 1. Classify Record Type
        val recordType = detectRecordType(combinedText, filenameHint)

        // 2. Extract District (জেলা)
        val district = extractField(
            text = rawOcrText,
            patterns = listOf(
                Regex("জেলা[:\\s]+([\\u0980-\\u09FFA-Za-z\\-]+)", RegexOption.IGNORE_CASE),
                Regex("district[:\\s]+([A-Za-z\\-]+)", RegexOption.IGNORE_CASE)
            ),
            defaultValue = "",
            pageIndex = pageIndex
        )

        // 3. Extract Upazila / Thana (উপজেলা/থানা)
        val upazila = extractField(
            text = rawOcrText,
            patterns = listOf(
                Regex("(?:উপজেলা|থানা)[:\\s]+([\\u0980-\\u09FFA-Za-z\\-]+)", RegexOption.IGNORE_CASE),
                Regex("(?:upazila|thana)[:\\s]+([A-Za-z\\-]+)", RegexOption.IGNORE_CASE)
            ),
            defaultValue = "",
            pageIndex = pageIndex
        )

        // 4. Extract Mouza (মৌজা)
        val mouza = extractField(
            text = rawOcrText,
            patterns = listOf(
                Regex("মৌজা[:\\s]+([\\u0980-\\u09FFA-Za-z\\-]+)", RegexOption.IGNORE_CASE),
                Regex("mouza[:\\s]+([A-Za-z\\-]+)", RegexOption.IGNORE_CASE)
            ),
            defaultValue = "",
            pageIndex = pageIndex
        )

        // 5. Extract JL No (জে. এল. নং)
        val jlNo = extractNumberField(
            text = rawOcrText,
            patterns = listOf(
                Regex("(?:জে[.\\s]*এল|জে\\s*এল|jl|j\\.l)[.\\s]*(?:নং|no)?[:\\s]*([০-৯0-9]+)", RegexOption.IGNORE_CASE),
                Regex("জে\\.?\\s*এল\\.?\\s*নম্বর[:\\s]*([০-৯0-9]+)", RegexOption.IGNORE_CASE)
            ),
            defaultValue = "",
            pageIndex = pageIndex
        )

        // 6. Extract Touzi No (তৌজি নং)
        val touziNo = extractNumberField(
            text = rawOcrText,
            patterns = listOf(
                Regex("(?:তৌজি|touzi)[.\\s]*(?:নং|no)?[:\\s]*([০-৯0-9]+)", RegexOption.IGNORE_CASE)
            ),
            defaultValue = "",
            pageIndex = pageIndex
        )

        // 7. Extract Khatian No (খতিয়ান নং)
        val khatianNo = extractNumberField(
            text = combinedText,
            patterns = listOf(
                Regex("(?:খতিয়ান|খতিয়ান|khatian)[.\\s]*(?:নং|no)?[:\\s]*([০-৯0-9/]+)", RegexOption.IGNORE_CASE),
                Regex("([০-৯0-9]+)\\s*নং\\s*(?:সি\\s*এস|এস\\s*এ|আর\\s*এস|বি\\s*আর\\s*এস)?\\s*খতিয়ান", RegexOption.IGNORE_CASE)
            ),
            defaultValue = extractNumberFromFilename(filenameHint) ?: "",
            pageIndex = pageIndex
        )

        // 8. Former & Hal Khatian
        val formerKhatian = extractNumberField(
            text = rawOcrText,
            patterns = listOf(
                Regex("(?:সাবেক|পূর্বের)\\s*খতিয়ান[.\\s]*(?:নং|no)?[:\\s]*([০-৯0-9/]+)", RegexOption.IGNORE_CASE)
            ),
            defaultValue = "",
            pageIndex = pageIndex
        )

        val halKhatian = extractNumberField(
            text = rawOcrText,
            patterns = listOf(
                Regex("(?:হাল|বর্তমান)\\s*খতিয়ান[.\\s]*(?:নং|no)?[:\\s]*([০-৯0-9/]+)", RegexOption.IGNORE_CASE)
            ),
            defaultValue = "",
            pageIndex = pageIndex
        )

        // 9. Dag Numbers (দাগ নং, সাবেক দাগ, হাল দাগ)
        val formerDagNo = extractNumberField(
            text = rawOcrText,
            patterns = listOf(
                Regex("(?:সাবেক|পূর্বে)\\s*দাগ[.\\s]*(?:নং|no)?[:\\s]*([০-৯0-9/]+)", RegexOption.IGNORE_CASE)
            ),
            defaultValue = "",
            pageIndex = pageIndex
        )

        val halDagNo = extractNumberField(
            text = rawOcrText,
            patterns = listOf(
                Regex("(?:হাল|বর্তমান)\\s*দাগ[.\\s]*(?:নং|no)?[:\\s]*([০-৯0-9/]+)", RegexOption.IGNORE_CASE)
            ),
            defaultValue = "",
            pageIndex = pageIndex
        )

        val primaryDagMatch = extractNumberField(
            text = rawOcrText,
            patterns = listOf(
                Regex("(?:দাগ|প্লট|plot|dag)[.\\s]*(?:নং|no)?[:\\s]*([০-৯0-9/]+)", RegexOption.IGNORE_CASE)
            ),
            defaultValue = "",
            pageIndex = pageIndex
        )
        val dagNo = when {
            primaryDagMatch.value.isNotBlank() -> primaryDagMatch
            halDagNo.value.isNotBlank() -> halDagNo
            formerDagNo.value.isNotBlank() -> formerDagNo
            else -> FieldWithConfidence("", ConfidenceLevel.UNCERTAIN, evidencePage = pageIndex)
        }

        // 10. Land Classification (জমির শ্রেণি)
        val landClass = extractLandClassification(rawOcrText, pageIndex)

        // 11. Land Share / Hissa (অংশ)
        val landShare = extractField(
            text = rawOcrText,
            patterns = listOf(
                Regex("(?:অংশ|হিস্যা|share)[:\\s]*([০-৯0-9./]+|[১-৯\\s]+আনা)", RegexOption.IGNORE_CASE)
            ),
            defaultValue = "",
            pageIndex = pageIndex
        )

        // 12. Area (একর ও শতাংশ)
        val areaAcres = extractNumberField(
            text = rawOcrText,
            patterns = listOf(
                Regex("([০-৯0-9.]+)\\s*(?:একর|acre)", RegexOption.IGNORE_CASE)
            ),
            defaultValue = "",
            pageIndex = pageIndex
        )

        val areaDecimals = extractNumberField(
            text = rawOcrText,
            patterns = listOf(
                Regex("([০-৯0-9.]+)\\s*(?:শতাংশ|শতক|ডেসিমাল|decimal)", RegexOption.IGNORE_CASE)
            ),
            defaultValue = "",
            pageIndex = pageIndex
        )

        // 13. Annual Rent (খাজনা)
        val annualRent = extractField(
            text = rawOcrText,
            patterns = listOf(
                Regex("(?:খাজনা|দাবি|rent)[:\\s]*([০-৯0-9.]+\\s*(?:টাকা)?)", RegexOption.IGNORE_CASE)
            ),
            defaultValue = "",
            pageIndex = pageIndex
        )

        // 14. Owners / Khatianidar list (strictly from OCR text, never fabricated)
        val owners = extractOwners(rawOcrText)

        // 15. Validation & Consistency Check
        val validationWarnings = mutableListOf<String>()
        var hissaTotal = 0.0
        for (owner in owners) {
            hissaTotal += BengaliNumberUtils.parseShareValue(owner.shareHissa)
        }

        val isHissaValid = if (owners.isNotEmpty() && hissaTotal > 0.0) {
            hissaTotal in 0.98..1.02
        } else {
            true
        }

        if (!isHissaValid && owners.isNotEmpty()) {
            validationWarnings.add(
                "সতর্কতা: মালিকদের মোট অংশ (হিস্যা) সমষ্টি ১.০০০ এর সমান নয় (বর্তমান সমষ্টি: ${
                    BengaliNumberUtils.toBengaliDigits(hissaTotal, 3)
                })"
            )
        }

        if (owners.isEmpty()) {
            validationWarnings.add("মালিক/রায়তের নাম স্বয়ংক্রিয়ভাবে শনাক্ত করা যায়নি — ম্যানুয়াল যাচাই প্রয়োজন")
        }

        if (khatianNo.value.isBlank()) {
            validationWarnings.add("খতিয়ান নম্বর অস্পষ্ট বা সনাক্ত করা যায়নি; মূল দলিলের সাথে মিলিয়ে নিন")
        }

        if (mouza.value.isBlank() || jlNo.value.isBlank()) {
            validationWarnings.add("মৌজা অথবা জে. এল. নম্বর অনুপস্থিত বা যাচাই প্রয়োজন")
        }

        return LandRecordData(
            recordType = recordType,
            district = district,
            upazilaThana = upazila,
            mouza = mouza,
            jlNo = jlNo,
            touziNo = touziNo,
            sheetNo = FieldWithConfidence("", ConfidenceLevel.UNCERTAIN, evidencePage = pageIndex),
            khatianNo = khatianNo,
            formerKhatianNo = formerKhatian,
            halKhatianNo = halKhatian,
            dagNo = dagNo,
            formerDagNo = formerDagNo,
            halDagNo = halDagNo,
            landClass = landClass,
            landShareHissa = landShare,
            areaAcres = areaAcres,
            areaDecimals = areaDecimals,
            annualRent = annualRent,
            owners = owners,
            validationWarnings = validationWarnings,
            isHissaValid = isHissaValid,
            hissaTotalCalculated = hissaTotal,
            legalDisclaimerAcknowledged = false
        )
    }

    private fun detectRecordType(text: String, filename: String): LandRecordType {
        val check = "$filename $text".uppercase()
        return when {
            check.contains("সি এস") || check.contains("সি.এস") || check.contains("CS") -> LandRecordType.CS
            check.contains("এস এ") || check.contains("এস.এ") || check.contains("SA") -> LandRecordType.SA
            check.contains("বি আর এস") || check.contains("বি.আর.এস") || check.contains("BRS") ||
                check.contains("সিটি জরিপ") || check.contains("বি এস") -> LandRecordType.BRS_BS
            check.contains("আর এস") || check.contains("আর.এস") || check.contains("RS") -> LandRecordType.RS
            check.contains("নামজারি") || check.contains("খারিজ") || check.contains("জমাভাগ") -> LandRecordType.NAMJARI_MUTATION
            check.contains("দলিল") || check.contains("কবলা") || check.contains("হেবা") || check.contains("DEED") -> LandRecordType.DEED_DALIL
            check.contains("দাখিলা") || check.contains("কর রশিদ") || check.contains("খাজনা") -> LandRecordType.DAKHILA_TAX
            check.contains("নকশা") || check.contains("ম্যাপ") || check.contains("MAP") -> LandRecordType.MOUZA_MAP
            check.contains("ওয়ারিশ") || check.contains("উত্তরাধিকার") -> LandRecordType.WARIS_INHERITANCE
            else -> LandRecordType.OTHER
        }
    }

    private fun extractField(
        text: String,
        patterns: List<Regex>,
        defaultValue: String,
        pageIndex: Int
    ): FieldWithConfidence {
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size > 1) {
                val found = match.groupValues[1].trim()
                if (found.isNotBlank()) {
                    return FieldWithConfidence(
                        value = found,
                        confidence = ConfidenceLevel.PROBABLE,
                        rawOcrMatch = match.value,
                        evidencePage = pageIndex
                    )
                }
            }
        }
        return FieldWithConfidence(
            value = defaultValue,
            confidence = if (defaultValue.isNotBlank()) ConfidenceLevel.PROBABLE else ConfidenceLevel.UNCERTAIN,
            evidencePage = pageIndex
        )
    }

    private fun extractNumberField(
        text: String,
        patterns: List<Regex>,
        defaultValue: String,
        pageIndex: Int
    ): FieldWithConfidence {
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size > 1) {
                val num = match.groupValues[1].trim()
                if (num.isNotBlank()) {
                    return FieldWithConfidence(
                        value = BengaliNumberUtils.toBengaliDigits(num),
                        confidence = ConfidenceLevel.PROBABLE,
                        rawOcrMatch = match.value,
                        evidencePage = pageIndex
                    )
                }
            }
        }
        return FieldWithConfidence(
            value = defaultValue,
            confidence = if (defaultValue.isNotBlank()) ConfidenceLevel.PROBABLE else ConfidenceLevel.UNCERTAIN,
            evidencePage = pageIndex
        )
    }

    private fun extractLandClassification(text: String, pageIndex: Int): FieldWithConfidence {
        val landClasses = listOf(
            "বাড়ি ও নাল", "ধানী ও নাল", "বাগান ও নাল", "নাল জমি",
            "নাল", "ভিটি", "ধানী", "পুকুর", "বাড়ি", "বাগান", "পতিত", "ডাঙ্গা",
            "দোকান", "চারা", "জলাশয়", "ভিটা"
        )
        for (lc in landClasses) {
            if (text.contains(lc)) {
                return FieldWithConfidence(
                    value = lc,
                    confidence = ConfidenceLevel.PROBABLE,
                    rawOcrMatch = "জমির শ্রেণি: $lc",
                    evidencePage = pageIndex
                )
            }
        }
        return FieldWithConfidence(
            value = "",
            confidence = ConfidenceLevel.UNCERTAIN,
            evidencePage = pageIndex
        )
    }

    private fun extractOwners(text: String): List<OwnerRecord> {
        val owners = mutableListOf<OwnerRecord>()

        val ownerPattern = Regex(
            "(?:মালিক|রায়ত|নাম)[:\\s]+([^,\n]+)(?:,\\s*(?:পিতা|স্বামী)[:\\s]+([^,\n()]+))?(?:[,\\s(]*(?:অংশ|হিস্যা)[:\\s]+([০-৯0-9./]+|[\\u0980-\\u09FF\\s]+))?",
            RegexOption.IGNORE_CASE
        )

        val matches = ownerPattern.findAll(text).toList()
        var serial = 1
        for (m in matches) {
            val name = m.groupValues[1].trim()
            if (name.length > 2 && !name.contains("গণপ্রজাতন্ত্রী") && !name.contains("মন্ত্রণালয়") && !name.contains("খতিয়ান")) {
                val father = if (m.groupValues.size > 2) m.groupValues[2].trim() else ""
                val share = if (m.groupValues.size > 3 && m.groupValues[3].isNotBlank()) {
                    BengaliNumberUtils.toBengaliDigits(m.groupValues[3].trim().trimEnd(')'))
                } else {
                    ""
                }

                owners.add(
                    OwnerRecord(
                        serial = serial++,
                        name = name,
                        fatherOrHusbandName = father,
                        shareHissa = share,
                        address = "",
                        confidence = ConfidenceLevel.PROBABLE
                    )
                )
            }
        }

        // Also parse numbered owner lines under 'মালিকগণের বিবরণ:' if present
        if (owners.isEmpty() && text.contains("মালিকগণের বিবরণ:")) {
            val section = text.substringAfter("মালিকগণের বিবরণ:").substringBefore("বার্ষিক খাজনা:")
            for (line in section.lines()) {
                val trimmed = line.trim()
                if (trimmed.isNotBlank()) {
                    val fatherMatch = Regex("পিতা[:\\s]+([^,()]+)").find(trimmed)
                    val shareMatch = Regex("অংশ[:\\s]+([০-৯0-9.]+)").find(trimmed)
                    val cleanName = trimmed
                        .substringBefore(",")
                        .substringBefore("(")
                        .replace(Regex("^[০-৯0-9]+\\.\\s*"), "")
                        .trim()
                    if (cleanName.isNotBlank()) {
                        owners.add(
                            OwnerRecord(
                                serial = serial++,
                                name = cleanName,
                                fatherOrHusbandName = fatherMatch?.groupValues?.get(1)?.trim().orEmpty(),
                                shareHissa = shareMatch?.groupValues?.get(1)?.trim().orEmpty(),
                                address = "",
                                confidence = ConfidenceLevel.PROBABLE
                            )
                        )
                    }
                }
            }
        }

        return owners
    }

    private fun extractNumberFromFilename(filename: String): String? {
        val regex = Regex("([০-৯0-9]+)\\s*নং")
        val match = regex.find(filename)
        return match?.groupValues?.get(1)?.let { BengaliNumberUtils.toBengaliDigits(it) }
    }
}
