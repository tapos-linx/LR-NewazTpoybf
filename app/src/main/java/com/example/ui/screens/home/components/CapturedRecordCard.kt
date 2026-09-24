package com.example.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.model.BengaliNumberUtils
import com.example.data.model.ConfidenceLevel
import com.example.ui.components.FieldConfidenceChip
import com.example.ui.theme.ParchmentPaper
import com.example.ui.theme.StatusProbable
import com.example.ui.theme.StatusUncertain
import com.example.ui.theme.StatusVerified
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Elevated card component for displaying a captured land record from the Room database.
 * Displays page thumbnail, document type badge, owner name, date captured, parcel coordinates,
 * and extraction confidence status.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CapturedRecordCard(
    document: LandDocumentEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("document_card_${document.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (document.isZeroByte) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Main Top Section: Thumbnail on Left + Primary Details on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // 1. Thumbnail Image or Parchment Fallback
                DocumentThumbnail(
                    document = document,
                    modifier = Modifier.testTag("thumbnail_${document.id}")
                )

                Spacer(modifier = Modifier.width(12.dp))

                // 2. Primary Metadata Details Column
                Column(modifier = Modifier.weight(1f)) {
                    // Header Row: Type Badge + Verification Chip + Delete Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DocumentTypeBadge(classifiedType = document.classifiedType)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (document.isZeroByte) {
                                ZeroByteIndicatorChip()
                            } else {
                                val confidenceLevel = when (document.status) {
                                    "VERIFIED" -> ConfidenceLevel.VERIFIED
                                    "EXTRACTED" -> ConfidenceLevel.PROBABLE
                                    else -> ConfidenceLevel.UNCERTAIN
                                }
                                FieldConfidenceChip(confidence = confidenceLevel)
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_delete_${document.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "মুছে ফেলুন",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Title
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Owner Name (মালিক)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "মালিক",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (!document.ownersSummary.isNullOrBlank()) {
                                "মালিক: ${document.ownersSummary}"
                            } else {
                                "মালিক: [অচিহ্নিত / যাচাই প্রয়োজন]"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (!document.ownersSummary.isNullOrBlank()) FontWeight.Medium else FontWeight.Normal,
                            color = if (!document.ownersSummary.isNullOrBlank()) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Date Captured (সংগৃহীত তারিখ ও সময়)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "সংগ্রহের তারিখ",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "সংগৃহীত: ${formatCapturedDate(document.dateCaptured)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Land Parcel Coordinates Badges (Khatian, Dag, Mouza, JL, District)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!document.primaryKhatianNo.isNullOrBlank()) {
                    ParcelInfoPill(
                        label = "খতিয়ান",
                        value = document.primaryKhatianNo,
                        tint = MaterialTheme.colorScheme.primaryContainer,
                        textColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (!document.primaryDagNo.isNullOrBlank()) {
                    ParcelInfoPill(
                        label = "দাগ",
                        value = document.primaryDagNo,
                        tint = MaterialTheme.colorScheme.secondaryContainer,
                        textColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                if (!document.primaryMouza.isNullOrBlank()) {
                    ParcelInfoPill(
                        label = "মৌজা",
                        value = document.primaryMouza,
                        tint = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!document.primaryDistrict.isNullOrBlank()) {
                    ParcelInfoPill(
                        label = "জেলা",
                        value = if (!document.primaryUpazila.isNullOrBlank()) {
                            "${document.primaryDistrict}, ${document.primaryUpazila}"
                        } else {
                            document.primaryDistrict
                        },
                        tint = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!document.primaryAreaDecimals.isNullOrBlank()) {
                    ParcelInfoPill(
                        label = "পরিমাণ",
                        value = "${document.primaryAreaDecimals} শতক",
                        tint = StatusVerified.copy(alpha = 0.15f),
                        textColor = StatusVerified
                    )
                }

                if (!document.primaryLandClass.isNullOrBlank()) {
                    ParcelInfoPill(
                        label = "শ্রেণি",
                        value = document.primaryLandClass,
                        tint = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Footer: Source Channel, File Format & Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${document.sourceCategory} • ${document.fileFormat} • ${formatFileSize(document.fileSizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = if (document.overallConfidence > 0f) {
                        "OCR স্কোর: ${BengaliNumberUtils.toBengaliDigits(document.overallConfidence.toInt().toLong())}%"
                    } else {
                        "${BengaliNumberUtils.toBengaliDigits(document.pageCount.toLong())} পাতা"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Renders the document thumbnail with real preview or stylized parchment fallback badge.
 */
@Composable
fun DocumentThumbnail(
    document: LandDocumentEntity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Locate first page image in sandbox or source file
    val thumbnailFile = remember(document.id, document.sourceFilePath) {
        val pagesDir = File(context.filesDir, "records/${document.id}/pages")
        val candidateFiles = listOf(
            File(pagesDir, "page_1_raw.png"),
            File(pagesDir, "page_1_raw.jpg"),
            File(pagesDir, "page_1_processed.png"),
            File(pagesDir, "page_1.png"),
            File(pagesDir, "page_1.jpg"),
            File(pagesDir, "page_1_prep.png")
        )
        val foundCandidate = candidateFiles.firstOrNull { it.exists() && it.length() > 0L }
        if (foundCandidate != null) return@remember foundCandidate

        val dirImage = pagesDir.listFiles()?.firstOrNull { file ->
            file.isFile && file.length() > 0L &&
                    file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")
        }
        if (dirImage != null) return@remember dirImage

        val srcFile = File(document.sourceFilePath)
        if (srcFile.exists() && srcFile.length() > 0L && (
                    srcFile.extension.equals("jpg", true) ||
                    srcFile.extension.equals("png", true) ||
                    srcFile.extension.equals("jpeg", true) ||
                    srcFile.extension.equals("webp", true))) {
            return@remember srcFile
        }
        null
    }

    Box(
        modifier = modifier
            .size(width = 82.dp, height = 110.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .background(ParchmentPaper)
    ) {
        if (thumbnailFile != null) {
            AsyncImage(
                model = thumbnailFile,
                contentDescription = "${document.title} থাম্বনেইল",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Stylized Bengali Parchment Land Document Representation
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = getRecordTypeIcon(document.classifiedType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = getRecordTypeShortLabel(document.classifiedType),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
                Text(
                    text = "ভূমি রেকর্ড",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = 9.sp
                )
            }
        }

        // Page count overlay tag on bottom-right of thumbnail
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(topStart = 6.dp),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Text(
                text = "${BengaliNumberUtils.toBengaliDigits(document.pageCount.toLong())} পাতা",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun DocumentTypeBadge(classifiedType: String) {
    val (bengaliName, bgColor, fgColor) = getDocumentTypeVisuals(classifiedType)

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = bengaliName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fgColor,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ZeroByteIndicatorChip() {
    Surface(
        color = StatusUncertain.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = StatusUncertain,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "০-বাইট সতর্কতা",
                color = StatusUncertain,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ParcelInfoPill(
    label: String,
    value: String,
    tint: Color,
    textColor: Color
) {
    Surface(
        color = tint,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
        )
    }
}

private fun getRecordTypeIcon(type: String): ImageVector {
    val upper = type.uppercase()
    return when {
        upper.contains("MUTATION") || upper.contains("NAMJARI") -> Icons.Default.Assignment
        upper.contains("DEED") || upper.contains("DALIL") -> Icons.Default.Description
        upper.contains("TAX") || upper.contains("DAKHILA") -> Icons.Default.Receipt
        upper.contains("MAP") -> Icons.Default.Landscape
        else -> Icons.Default.MenuBook
    }
}

private fun getRecordTypeShortLabel(type: String): String {
    val upper = type.uppercase()
    return when {
        upper == "CS" -> "সি এস"
        upper == "SA" -> "এস এ"
        upper == "RS" -> "আর এস"
        upper.contains("BRS") || upper.contains("BS") -> "বি আর এস"
        upper.contains("MUTATION") || upper.contains("NAMJARI") -> "নামজারি"
        upper.contains("DEED") || upper.contains("DALIL") -> "দলিল"
        upper.contains("TAX") || upper.contains("DAKHILA") -> "দাখিলা"
        upper.contains("MAP") -> "ম্যাপ"
        else -> type
    }
}

private fun getDocumentTypeVisuals(type: String): Triple<String, Color, Color> {
    val upper = type.uppercase()
    return when {
        upper == "CS" -> Triple("সি এস খতিয়ান", Color(0xFFE8F5E9), Color(0xFF1B5E20))
        upper == "SA" -> Triple("এস এ খতিয়ান", Color(0xFFE0F7FA), Color(0xFF006064))
        upper == "RS" -> Triple("আর এস খতিয়ান", Color(0xFFE3F2FD), Color(0xFF0D47A1))
        upper.contains("BRS") || upper.contains("BS") -> Triple("বি আর এস / সিটি", Color(0xFFF3E5F5), Color(0xFF4A148C))
        upper.contains("MUTATION") || upper.contains("NAMJARI") -> Triple("নামজারি পরচা", Color(0xFFFFF3E0), Color(0xFFE65100))
        upper.contains("DEED") || upper.contains("DALIL") -> Triple("রেজিস্টার্ড দলিল", Color(0xFFFCE4EC), Color(0xFF880E4F))
        upper.contains("TAX") || upper.contains("DAKHILA") -> Triple("দাখিলা রশিদ", Color(0xFFF1F8E9), Color(0xFF33691E))
        upper.contains("MAP") -> Triple("মৌজা নকশা", Color(0xFFEFEBE9), Color(0xFF3E2723))
        else -> Triple(type, Color(0xFFF5F5F5), Color(0xFF212121))
    }
}

private fun formatCapturedDate(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    return try {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val formatted = sdf.format(Date(timestamp))
        BengaliNumberUtils.toBengaliDigits(formatted)
    } catch (_: Exception) {
        BengaliNumberUtils.toBengaliDigits(timestamp.toString())
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "০ বাইট"
    if (bytes < 1024) return "${BengaliNumberUtils.toBengaliDigits(bytes)} B"
    val kb = bytes / 1024.0
    if (kb < 1024) {
        val s = String.format(Locale.US, "%.1f", kb)
        return "${BengaliNumberUtils.toBengaliDigits(s)} KB"
    }
    val mb = kb / 1024.0
    val s = String.format(Locale.US, "%.1f", mb)
    return "${BengaliNumberUtils.toBengaliDigits(s)} MB"
}
