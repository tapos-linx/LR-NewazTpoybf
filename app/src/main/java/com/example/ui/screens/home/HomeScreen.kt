package com.example.ui.screens.home

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.model.ConfidenceLevel
import com.example.domain.export.ExportFormat
import com.example.domain.export.ReportExportManager
import com.example.ui.components.FieldConfidenceChip
import com.example.ui.components.LegalDisclaimerBanner
import com.example.ui.components.ReportExportDialog
import com.example.ui.screens.home.components.CapturedRecordCard
import com.example.ui.theme.ParchmentPaper
import com.example.ui.theme.StatusProbable
import com.example.ui.theme.StatusUncertain
import com.example.ui.theme.StatusVerified

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToSafImport: () -> Unit,
    onNavigateToTesseract: () -> Unit,
    onNavigateToCapturedRecords: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // SAF file picker for manual file import
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importFilesFromUris(uris)
        }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf<LandDocumentEntity?>(null) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Newaz Land Extractor",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "বাংলাদেশ ভূমি রেকর্ড নিষ্কাশন ও প্রমাণ সংরক্ষণ",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                actions = {
                    if (onNavigateToCapturedRecords != null) {
                        IconButton(
                            onClick = onNavigateToCapturedRecords,
                            modifier = Modifier.testTag("btn_all_captured_records")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "সকল সংগৃহীত রেকর্ড",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    IconButton(
                        onClick = onNavigateToTesseract,
                        modifier = Modifier.testTag("tesseract_setup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "OCR মডেল ও ভাষা",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setShowExportBatchDialog(true) },
                        modifier = Modifier.testTag("export_csv_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "রিপোর্ট সংরক্ষণ (SAF)",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                // Camera Capture FAB
                ExtendedFloatingActionButton(
                    onClick = onNavigateToCamera,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "ক্যামেরা স্ক্যান") },
                    text = { Text("দলিল ক্যামেরা স্ক্যান", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("fab_camera_capture")
                )

                // SAF Import FAB
                ExtendedFloatingActionButton(
                    onClick = onNavigateToSafImport,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = "ফোল্ডার স্ক্যান") },
                    text = { Text("ইনপুট ফোল্ডার (SAF)") },
                    modifier = Modifier.testTag("fab_saf_folder")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Legal Disclaimer Banner (Prominent & offline notice)
            LegalDisclaimerBanner(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )

            // Search Bar & Import Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("খতিয়ান, দাগ, মৌজা বা মালিক খুঁজুন...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { filePickerLauncher.launch(arrayOf("application/pdf", "image/*")) },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .testTag("btn_import_files")
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = "নথি ফাইল নির্বাচন",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Metric Summary Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "মোট নথি",
                    count = uiState.totalCount.toString(),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "যাচাইকৃত",
                    count = uiState.verifiedCount.toString(),
                    color = StatusVerified,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "০-বাইট ত্রুটি",
                    count = uiState.zeroByteCount.toString(),
                    color = if (uiState.zeroByteCount > 0) StatusUncertain else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )
            }

            // Quick CSV Export Bar for External Reporting
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "এক্সটার্নাল রিপোর্টিং (CSV ফাইল)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "সকল নথির মেটাডাটা এক্সেল বা গুগল শিটসে ডাউনলোড করুন",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { viewModel.setShowExportBatchDialog(true) },
                        modifier = Modifier.testTag("btn_export_csv_quick"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV এক্সপোর্ট", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Category Filter Chips
            CategoryChipsRow(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = { viewModel.onCategorySelected(it) }
            )

            // Progress Bar if batch processing
            AnimatedVisibility(visible = uiState.isBatchProcessing) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = uiState.processingMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Document List
            if (uiState.filteredDocuments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "কোনো নথি খুঁজে পাওয়া যায়নি" else "কোনো ভূমি রেকর্ড নথি নেই",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "উপরের বাটন চেপে ক্যামেরা দিয়ে পাতা স্ক্যান করুন অথবা SAF ফোল্ডার যুক্ত করুন",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("captured_records_lazy_column")
                        .testTag("captured_records_list"),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.filteredDocuments,
                        key = { it.id }
                    ) { doc ->
                        CapturedRecordCard(
                            document = doc,
                            onClick = { onNavigateToDetail(doc.id) },
                            onDelete = { showDeleteConfirmDialog = doc }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { doc ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("নথি মুছে ফেলার নিশ্চিতকরণ") },
            text = { Text("আপনি কি নিশ্চিতভাবে '${doc.title}' নথিটি স্থানীয় ডেটাবেজ থেকে মুছে ফেলতে চান? মূল ইনপুট ফাইল অক্ষত থাকবে।") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(doc.id)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("মুছে ফেলুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("বাতিল")
                }
            }
        )
    }

    if (uiState.showExportBatchDialog) {
        ReportExportDialog(
            documentTitle = "সকল ভূমি রেকর্ড (${uiState.totalCount} টি নথি)",
            suggestedFileNameJson = viewModel.suggestBatchFileName(ExportFormat.JSON),
            suggestedFileNameText = viewModel.suggestBatchFileName(ExportFormat.TEXT),
            suggestedFileNameCsv = viewModel.suggestBatchFileName(ExportFormat.CSV),
            jsonPreview = viewModel.getBatchPreviewContent(ExportFormat.JSON),
            textPreview = viewModel.getBatchPreviewContent(ExportFormat.TEXT),
            csvPreview = viewModel.getBatchPreviewContent(ExportFormat.CSV),
            initialFormat = ExportFormat.CSV,
            onSaveToSafUri = { uri, format ->
                viewModel.saveBatchReportToSafUri(uri, format) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            },
            onShare = { format ->
                viewModel.setShowExportBatchDialog(false)
                viewModel.exportAllToCsv { file ->
                    ReportExportManager.shareFile(
                        context = context,
                        file = file,
                        mimeType = if (format == ExportFormat.CSV) "text/csv" else format.mimeType,
                        subject = "বাংলাদেশ ভূমি রেকর্ডসমূহ সামগ্রিক CSV মেটাডাটা রিপোর্ট"
                    )
                }
            },
            onDismiss = { viewModel.setShowExportBatchDialog(false) }
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = count,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChipsRow(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val categories = listOf(
        "ALL" to "সব নথি",
        "CS" to "সি এস (CS)",
        "SA" to "এস এ (SA)",
        "RS" to "আর এস (RS)",
        "BRS_BS" to "বি আর এস (BRS)",
        "NAMJARI_MUTATION" to "নামজারি",
        "DEED_DALIL" to "দলিল",
        "DAKHILA_TAX" to "দাখিলা/কর",
        "ZERO_BYTE" to "০-বাইট সতর্কতা"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for ((code, label) in categories) {
            val isSelected = selectedCategory == code
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(code) },
                label = { Text(label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

