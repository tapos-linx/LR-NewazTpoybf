package com.example.ui.screens.records

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.model.BengaliNumberUtils
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.home.components.CapturedRecordCard

enum class RecordSortOrder(val label: String) {
    NEWEST_CAPTURED("নতুন সংগৃহীত প্রথমে"),
    OLDEST_CAPTURED("পুরনো সংগৃহীত প্রথমে"),
    KHATIAN_NO("খতিয়ান নম্বর অনুসারে"),
    DAG_NO("দাগ নম্বর অনুসারে"),
    CONFIDENCE_HIGH("উচ্চ OCR স্কোর প্রথমে")
}

/**
 * Jetpack Compose screen that displays a list of captured land records from the Room database.
 * Uses a LazyColumn with cards that show a thumbnail preview of the captured document
 * and summary metadata details (title, type, owner, parcel coords, date captured, confidence).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturedRecordsScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCamera: () -> Unit = {},
    onNavigateToSafImport: () -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var sortOrder by remember { mutableStateOf(RecordSortOrder.NEWEST_CAPTURED) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf<LandDocumentEntity?>(null) }

    // Sorted and filtered documents
    val sortedDocuments by remember(uiState.filteredDocuments, sortOrder) {
        derivedStateOf {
            when (sortOrder) {
                RecordSortOrder.NEWEST_CAPTURED -> uiState.filteredDocuments.sortedByDescending { it.dateCaptured }
                RecordSortOrder.OLDEST_CAPTURED -> uiState.filteredDocuments.sortedBy { it.dateCaptured }
                RecordSortOrder.KHATIAN_NO -> uiState.filteredDocuments.sortedBy { it.primaryKhatianNo ?: "" }
                RecordSortOrder.DAG_NO -> uiState.filteredDocuments.sortedBy { it.primaryDagNo ?: "" }
                RecordSortOrder.CONFIDENCE_HIGH -> uiState.filteredDocuments.sortedByDescending { it.overallConfidence }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("captured_records_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "সংগৃহীত ভূমি রেকর্ডসমূহ",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Room ডেটাবেজ সংরক্ষিত (${BengaliNumberUtils.toBengaliDigits(sortedDocuments.size.toLong())} টি নথি)",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("btn_back_captured_records")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "ফিরে যান",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("btn_sort_records")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "সাজান",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            RecordSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    onClick = {
                                        sortOrder = order
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
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
                ExtendedFloatingActionButton(
                    onClick = onNavigateToCamera,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "ক্যামেরা স্ক্যান") },
                    text = { Text("নতুন দলিল স্ক্যান", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("fab_camera_captured_screen")
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
            // Search Input Field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
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
                        .fillMaxWidth()
                        .testTag("captured_records_search_input")
                )
            }

            // Classification Filter Chips Row
            RecordTypeFilterRow(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = { viewModel.onCategorySelected(it) }
            )

            // Results count status summary
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "মোট: ${BengaliNumberUtils.toBengaliDigits(sortedDocuments.size.toLong())} টি রেকর্ড পাওয়া গেছে",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "ক্রম: ${sortOrder.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                }
            }

            // The Core LazyColumn displaying captured records
            CapturedRecordsList(
                documents = sortedDocuments,
                onDocumentClick = onNavigateToDetail,
                onDeleteDocument = { showDeleteConfirmDialog = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }

    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { doc ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("নথি মুছে ফেলার নিশ্চিতকরণ") },
            text = { Text("আপনি কি নিশ্চিতভাবে '${doc.title}' নথিটি স্থানীয় Room ডেটাবেজ থেকে মুছে ফেলতে চান?") },
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
}

/**
 * Reusable, isolated Composable that renders the LazyColumn with cards showing
 * thumbnail previews and summary metadata details for a list of land documents.
 */
@Composable
fun CapturedRecordsList(
    documents: List<LandDocumentEntity>,
    onDocumentClick: (String) -> Unit,
    onDeleteDocument: (LandDocumentEntity) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "কোনো সংগৃহীত ভূমি রেকর্ড পাওয়া যায়নি"
) {
    if (documents.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("empty_captured_records_view"),
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
                    text = emptyMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "ক্যামেরা স্ক্যান করে বা ফোল্ডার থেকে ফাইল যুক্ত করে রেকর্ড তৈরি করুন",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("captured_records_lazy_column")
                .testTag("captured_records_list"),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = documents,
                key = { it.id }
            ) { document ->
                CapturedRecordCard(
                    document = document,
                    onClick = { onDocumentClick(document.id) },
                    onDelete = { onDeleteDocument(document) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordTypeFilterRow(
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
        "DAKHILA_TAX" to "দাখিলা/কর"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { (code, label) ->
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
