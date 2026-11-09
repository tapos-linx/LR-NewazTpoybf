package com.example.ui.screens.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BengaliNumberUtils
import com.example.domain.saf.DiscoveredFile
import com.example.ui.screens.saf.SafImportViewModel
import com.example.ui.theme.StatusCorroborated
import com.example.ui.theme.StatusUncertain
import com.example.ui.theme.StatusVerified

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkUploadScreen(
    viewModel: SafImportViewModel,
    onNavigateBack: () -> Unit,
    onUploadComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // 1. Multi-File & ZIP Picker (works for PC Local Disk, Browser Emulator, Phone Downloads & SD Card)
    val multiFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.scanMultipleFiles(uris)
        }
    }

    // 2. SAF Folder Tree Picker for recursive directory upload
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.scanFolderTree(it) }
    }

    // 3. SAF Save ZIP to PC Local Disk / Custom Folder
    val safSaveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.saveAllToSafUri(uri, com.example.domain.export.ExportFormat.ZIP) { _, msg ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // 4. SAF Save CSV to PC Local Disk / Custom Folder
    val safSaveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.saveAllToSafUri(uri, com.example.domain.export.ExportFormat.CSV) { _, msg ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("bulk_upload_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "বাল্ক আপলোড ও মাস ডাউনলোড হাব",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "পিসি লোকাল ডিস্ক ও ফোন মেমোরি (Downloads) ইঞ্জিন",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_bulk_upload_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Mass Upload Card (Multi-file + ZIP + Folder)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bulk_upload_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Upload Cloud",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "১. বাল্ক আপলোড (পিসি লোকাল ডিস্ক ও ফোন মেমোরি)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "একাধিক PDF, ছবি, বা সম্পূর্ণ .ZIP ফাইল ও ফোল্ডার একসাথে আপলোড করুন",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Accepted File Format Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        listOf("PDF", "JPG", "PNG", "TIFF", "ZIP", "CSV", "DOCX", "XLSX").forEach { fmt ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = fmt,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { multiFilePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_select_multiple_files"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ফাইল / ZIP নির্বাচন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_select_upload_folder"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ফোল্ডার স্ক্যান", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mass Download Card (Phone Memory Downloads + PC Local Disk SAF)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mass_download_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "২. বাল্ক ডাউনলোড (ফোন মেমোরি Downloads ও পিসি লোকাল ডিস্ক)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.downloadAllToPhoneMemory(com.example.domain.export.ExportFormat.ZIP) { _, msg ->
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = !uiState.isExporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_mass_download_phone_zip"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                        ) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ফোনে ZIP ডাউনলোড", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                safSaveZipLauncher.launch(viewModel.suggestExportFileName(com.example.domain.export.ExportFormat.ZIP))
                            },
                            enabled = !uiState.isExporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_mass_download_pc_zip"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("পিসিতে ZIP সেভ", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.downloadAllToPhoneMemory(com.example.domain.export.ExportFormat.CSV) { _, msg ->
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = !uiState.isExporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_mass_download_phone_csv"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ফোনে CSV ডাউনলোড", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                safSaveCsvLauncher.launch(viewModel.suggestExportFileName(com.example.domain.export.ExportFormat.CSV))
                            },
                            enabled = !uiState.isExporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_mass_download_pc_csv"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("পিসিতে CSV সেভ", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Status / Error Banner
            uiState.statusMessage?.let { statusMsg ->
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = StatusVerified.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusMsg,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = StatusVerified,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            uiState.errorMessage?.let { errMsg ->
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errMsg,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Real-Time Queue & Scan Summary
            val summary = uiState.scanSummary
            if (summary != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan_summary_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "কিউ সারসংক্ষেপ",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "মোট ফাইল: ${BengaliNumberUtils.toBengaliDigits(summary.totalFilesFound.toLong())}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Supported
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusVerified.copy(alpha = 0.15f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusVerified, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${BengaliNumberUtils.toBengaliDigits(summary.supportedFiles.size.toLong())} সমর্থিত",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusVerified
                                    )
                                }
                            }

                            // Duplicate Warning Chip
                            if (summary.duplicateFiles.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = StatusCorroborated.copy(alpha = 0.2f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = StatusCorroborated, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${BengaliNumberUtils.toBengaliDigits(summary.duplicateFiles.size.toLong())} ডুপ্লিকেট",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusCorroborated
                                        )
                                    }
                                }
                            }

                            // Zero Byte Warning Chip
                            if (summary.zeroByteFiles.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = StatusUncertain.copy(alpha = 0.15f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusUncertain, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${BengaliNumberUtils.toBengaliDigits(summary.zeroByteFiles.size.toLong())} ০-বাইট",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusUncertain
                                        )
                                    }
                                }
                            }
                        }

                        // Duplicate rule forensic note
                        if (summary.duplicateFiles.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "নোট: ফরেনসিক নিয়মে ডুপ্লিকেট ফাইল হলেও মূল উৎস ও মেটাডাটা সংরক্ষণ করা হবে।",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Batch Processing Progress Indicator
            AnimatedVisibility(visible = uiState.isProcessingBatch) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("upload_processing_progress_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "প্রসেসিং হচ্ছে: ${uiState.processingFileName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            val percent = if (uiState.totalToProcess > 0) {
                                (uiState.currentProcessingIndex.toFloat() / uiState.totalToProcess * 100).toInt()
                            } else 0
                            Text(
                                text = "${BengaliNumberUtils.toBengaliDigits(percent.toLong())}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val progressFraction = if (uiState.totalToProcess > 0) {
                            uiState.currentProcessingIndex.toFloat() / uiState.totalToProcess
                        } else 0f

                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${BengaliNumberUtils.toBengaliDigits(uiState.currentProcessingIndex.toLong())} / ${BengaliNumberUtils.toBengaliDigits(uiState.totalToProcess.toLong())} নথি সম্পন্ন",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Upload Queue List
            Text(
                text = "আপলোড কিউ তালিকা",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            val files = summary?.supportedFiles ?: emptyList()
            if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("empty_queue_view"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Queue,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "কোন ফাইল কিউতে নেই",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "উপরে ফোল্ডার নির্বাচন করে ফাইল লোড করুন",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("upload_queue_list"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(files, key = { it.uri.toString() + it.name }) { file ->
                        UploadQueueItemCard(file = file)
                    }
                }
            }

            // Bottom Action: Start Batch Processing
            if (summary != null && summary.supportedFiles.isNotEmpty() && !uiState.isProcessingBatch) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.startBatchProcessing {
                            onUploadComplete()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_start_batch_processing"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "জিরো-ডাটা-লস নিষ্কাশন শুরু করুন (${BengaliNumberUtils.toBengaliDigits(summary.supportedFiles.size.toLong())} ফাইল)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun UploadQueueItemCard(file: DiscoveredFile) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("queue_item_${file.name}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (file.isDuplicate) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (file.isDuplicate) StatusCorroborated.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon according to format
            val icon = when (file.format) {
                "PDF" -> Icons.Default.PictureAsPdf
                "JPG", "PNG", "JPEG", "TIFF" -> Icons.Default.Image
                "ZIP" -> Icons.Default.FolderZip
                else -> Icons.Default.Description
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (file.isDuplicate) StatusCorroborated else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category pill
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = file.classifiedType.code,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    // Format
                    Text(
                        text = file.format,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    // Size
                    val sizeKb = (file.sizeBytes / 1024).coerceAtLeast(1)
                    Text(
                        text = "${BengaliNumberUtils.toBengaliDigits(sizeKb)} KB",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // SHA-256 Hash Preview & Duplicate warning
                if (file.sha256Hash.isNotBlank()) {
                    Text(
                        text = "SHA-256: ${file.sha256Hash.take(12)}...",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
                    )
                }

                if (file.isDuplicate) {
                    Text(
                        text = "সতর্কবার্তা: পূর্ববর্তী নথির একই হ্যাশ শনাক্ত (${file.duplicateOriginalSource ?: ""})। মূল ফাইল সংরক্ষিত থাকবে।",
                        fontSize = 9.sp,
                        color = StatusCorroborated,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Right Status Indicator
            if (file.isDuplicate) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = StatusCorroborated.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "ডুপ্লিকেট",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusCorroborated,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            } else if (file.isZeroByte) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = StatusUncertain.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "০-বাইট",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusUncertain,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = StatusVerified.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "কিউতে যুক্ত",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusVerified,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
