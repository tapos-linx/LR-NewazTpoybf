package com.example.data.model

import java.text.DecimalFormat
import java.util.Locale

/**
 * Utility for converting between Bengali numerals (০-৯) and Arabic numerals (0-9),
 * and parsing traditional land fractions (আনা-গণ্ডা-কড়া-ক্রান্তি).
 */
object BengaliNumberUtils {

    private val BENGALI_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    private val ENGLISH_DIGITS = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')

    fun toBengaliDigits(number: Long): String {
        return toBengaliDigits(number.toString())
    }

    fun toBengaliDigits(number: Double, decimalPlaces: Int = 2): String {
        val pattern = if (decimalPlaces <= 0) "#" else "#." + "#".repeat(decimalPlaces)
        val formatted = DecimalFormat(pattern, java.text.DecimalFormatSymbols(Locale.US)).format(number)
        return toBengaliDigits(formatted)
    }

    fun toBengaliDigits(text: String): String {
        val sb = java.lang.StringBuilder(text.length)
        for (ch in text) {
            val idx = ch - '0'
            if (idx in 0..9) {
                sb.append(BENGALI_DIGITS[idx])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun toEnglishDigits(text: String): String {
        val sb = java.lang.StringBuilder(text.length)
        for (ch in text) {
            val idx = ch - '০'
            if (idx in 0..9) {
                sb.append(ENGLISH_DIGITS[idx])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parseNumber(text: String): Double? {
        val eng = toEnglishDigits(text).trim()
        val cleaned = eng.replace(Regex("[^0-9.]"), "")
        return cleaned.toDoubleOrNull()
    }

    fun parseInt(text: String): Int? {
        val eng = toEnglishDigits(text).trim()
        val cleaned = eng.replace(Regex("[^0-9]"), "")
        return cleaned.toIntOrNull()
    }

    /**
     * Converts a share string (decimal e.g. 0.500 or Anna e.g. "৮ আনা" or "1.000") to double.
     * Full share in modern Porcha is 1.0000. In old porchas it was 16 Ana (১.০০০০).
     */
    fun parseShareValue(shareStr: String): Double {
        val clean = shareStr.trim()
        val eng = toEnglishDigits(clean)

        // Check traditional Bengali Anna notation first
        // 16 Ana = 1.0 (Full share), 1 Ana = 1/16 = 0.0625
        val annaRegex = Regex("([0-9.]+)\\s*(?:আনা|ana|আ)")
        val match = annaRegex.find(eng)
        if (match != null) {
            val anaVal = match.groupValues[1].toDoubleOrNull() ?: 0.0
            return (anaVal / 16.0)
        }

        // Try direct decimal parse
        val direct = parseNumber(clean)
        if (direct != null) {
            return direct
        }

        return 0.0
    }
}
