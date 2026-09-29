package com.example.ui.screens.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.model.BengaliNumberUtils
import com.example.ui.screens.home.HomeViewModel
import java.io.File

/**
 * Gallery View using LazyVerticalGrid to display captured land record images,
 * allowing users to browse, select, and delete individual records stored in local storage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordGalleryScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToDetail: (String) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToCamera: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var previewDocument by remember { mutableStateOf<LandDocumentEntity?>(null) }
    var documentToDelete by remember { mutableStateOf<LandDocumentEntity?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var gridColumnCount by remember { mutableIntStateOf(2) }

    // Exit selection mode if selected list becomes empty while in selection mode
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    fun toggleSelection(docId: String) {
        selectedIds = if (selectedIds.contains(docId)) {
            val newSet = selectedIds - docId
            if (newSet.isEmpty()) isSelectionMode = false
            newSet
        } else {
            selectedIds + docId
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("record_gallery_screen"),
        topBar = {
            if (isSelectionMode) {
                // Selection Contextual Top App Bar
                TopAppBar(
                    title = {
                        Text(
                            text = "${BengaliNumberUtils.toBengaliDigits(selectedIds.size.toLong())} টি নির্বাচিত",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.testTag("gallery_selected_count_text")
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { exitSelectionMode() },
                            modifier = Modifier.testTag("btn_exit_selection_mode")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "নির্বাচন বাতিল",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                selectedIds = if (selectedIds.size == uiState.filteredDocuments.size) {
                                    emptySet()
                                } else {
                                    uiState.filteredDocuments.map { it.id }.toSet()
                                }
                            },
                            modifier = Modifier.testTag("btn_select_all_gallery")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "সব নির্বাচন করুন",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    showBatchDeleteConfirm = true
                                }
                            },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.testTag("btn_delete_selected_gallery")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "নির্বাচিত রেকর্ড মুছে ফেলুন",
                                tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        titleContentColor = MaterialTheme.colorScheme.onSecondary,
                        actionIconContentColor = MaterialTheme.colorScheme.onSecondary
                    )
                )
            } else {
                // Standard Top App Bar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "ভূমি রেকর্ড গ্যালারি",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "সংরক্ষিত ইমেজ (${BengaliNumberUtils.toBengaliDigits(uiState.filteredDocuments.size.toLong())} টি নথি)",
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
                                modifier = Modifier.testTag("btn_gallery_back")
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
                        // Toggle 2 or 3 columns
                        IconButton(
                            onClick = { gridColumnCount = if (gridColumnCount == 2) 3 else 2 },
                            modifier = Modifier.testTag("btn_toggle_columns")
                        ) {
                            Icon(
                                imageVector = if (gridColumnCount == 2) Icons.Default.ViewModule else Icons.Default.GridView,
                                contentDescription = "গ্রিড লেআউট পরিবর্তন",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        // Enter multi-select mode
                        IconButton(
                            onClick = {
                                isSelectionMode = true
                                if (uiState.filteredDocuments.isNotEmpty()) {
                                    selectedIds = emptySet()
                                }
                            },
                            modifier = Modifier.testTag("btn_enter_selection_mode")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "নির্বাচন মোড",
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
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = onNavigateToCamera,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_camera_gallery")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "নতুন দলিল স্ক্যান করুন"
                    )
                }
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
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("খতিয়ান, দাগ, মৌজা বা জেলা খুঁজুন...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "অনুসন্ধান") },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "মুছুন")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gallery_search_input")
                )
            }

            // Category Filter Chips
            GalleryCategoryFilterRow(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = { viewModel.onCategorySelected(it) }
            )

            // Results count banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
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
                        text = "মোট: ${BengaliNumberUtils.toBengaliDigits(uiState.filteredDocuments.size.toLong())} টি ইমেজ রেকর্ড",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isSelectionMode) {
                        Text(
                            text = "ট্যাপ করে নির্বাচন করুন",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "ট্যাপ করে প্রিভিউ দেখুন",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Core LazyVerticalGrid
            RecordGalleryGrid(
                documents = uiState.filteredDocuments,
                selectedIds = selectedIds,
                isSelectionMode = isSelectionMode,
                columnCount = gridColumnCount,
                onToggleSelect = { docId -> toggleSelection(docId) },
                onDocumentClick = { doc ->
                    if (isSelectionMode) {
                        toggleSelection(doc.id)
                    } else {
                        previewDocument = doc
                    }
                },
                onDocumentLongClick = { doc ->
                    if (!isSelectionMode) {
                        isSelectionMode = true
                        selectedIds = setOf(doc.id)
                    } else {
                        toggleSelection(doc.id)
                    }
                },
                onDeleteDocument = { doc ->
                    documentToDelete = doc
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }

    // Full Image Preview Dialog
    previewDocument?.let { doc ->
        RecordImagePreviewDialog(
            document = doc,
            onDismiss = { previewDocument = null },
            onNavigateToDetail = {
                previewDocument = null
                onNavigateToDetail(doc.id)
            },
            onDelete = {
                previewDocument = null
                documentToDelete = doc
            }
        )
    }

    // Delete Single Record Confirmation Dialog
    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("রেকর্ড ও ইমেজ মুছে ফেলুন?") },
            text = {
                Text("আপনি কি নিশ্চিতভাবে '${doc.title}' রেকর্ডটি এবং স্থানীয় মেমরিতে সংরক্ষিত এর স্ক্যানকৃত ইমেজসমূহ মুছে ফেলতে চান?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(doc.id)
                        selectedIds = selectedIds - doc.id
                        documentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_delete_single")
                ) {
                    Text("মুছে ফেলুন")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { documentToDelete = null },
                    modifier = Modifier.testTag("btn_cancel_delete_single")
                ) {
                    Text("বাতিল")
                }
            }
        )
    }

    // Delete Multiple Selected Records Confirmation Dialog
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("নির্বাচিত রেকর্ড মুছে ফেলুন?") },
            text = {
                Text("আপনি কি নিশ্চিতভাবে নির্বাচিত ${BengaliNumberUtils.toBengaliDigits(selectedIds.size.toLong())} টি রেকর্ড এবং এদের সকল স্থানীয় ইমেজ ফাইল ডিভাইস থেকে মুছে ফেলতে চান? এটি অপ্রত্যাবর্তনযোগ্য।")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocuments(selectedIds)
                        exitSelectionMode()
                        showBatchDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_delete_batch")
                ) {
                    Text("মুছে ফেলুন (${BengaliNumberUtils.toBengaliDigits(selectedIds.size.toLong())})")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchDeleteConfirm = false },
                    modifier = Modifier.testTag("btn_cancel_delete_batch")
                ) {
                    Text("বাতিল")
                }
            }
        )
    }
}

/**
 * Reusable LazyVerticalGrid displaying land record image cards.
 */
@Composable
fun RecordGalleryGrid(
    documents: List<LandDocumentEntity>,
    selectedIds: Set<String>,
    isSelectionMode: Boolean,
    columnCount: Int = 2,
    onToggleSelect: (String) -> Unit,
    onDocumentClick: (LandDocumentEntity) -> Unit,
    onDocumentLongClick: (LandDocumentEntity) -> Unit = {},
    onDeleteDocument: (LandDocumentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (documents.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp)
                .testTag("empty_gallery_view"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "গ্যালারিতে কোনো ইমেজ রেকর্ড পাওয়া যায়নি",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ক্যামেরা দিয়ে নতুন দলিল স্ক্যান করে বা ফোল্ডার থেকে ফাইল যুক্ত করে ইমেজ সংরক্ষণ করুন।",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier
                .fillMaxSize()
                .testTag("record_gallery_grid")
        ) {
            items(
                items = documents,
                key = { it.id }
            ) { document ->
                val isSelected = selectedIds.contains(document.id)
                RecordGalleryCard(
                    document = document,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    onToggleSelect = { onToggleSelect(document.id) },
                    onClick = { onDocumentClick(document) },
                    onLongClick = { onDocumentLongClick(document) },
                    onDelete = { onDeleteDocument(document) }
                )
            }
        }
    }
}

/**
 * Individual Card in the LazyVerticalGrid showing document image thumbnail and summary metadata.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordGalleryCard(
    document: LandDocumentEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Locate local storage image file
    val imageFile = remember(document.id, document.sourceFilePath) {
        findDocumentImageFile(context, document)
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    val borderWidth = if (isSelected) 2.5.dp else 1.dp

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("record_gallery_item_${document.id}")
    ) {
        Column {
            // Image Preview Container with Overlays
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
                    .background(Color(0xFFF4F0E8)) // Bengali parchment document tone
            ) {
                if (imageFile != null && imageFile.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = document.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("gallery_thumbnail_${document.id}")
                    )
                } else {
                    // Stylized vector placeholder for document image
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = document.classifiedType,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "স্ক্যানকৃত নথি",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Top-Left: Survey Type Badge
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    color = getCategoryBadgeColor(document.classifiedType),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = document.classifiedType,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                // Top-Right: Selection Indicator OR Quick Delete Button
                if (isSelectionMode) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(28.dp)
                    ) {
                        IconButton(
                            onClick = onToggleSelect,
                            modifier = Modifier.testTag("select_checkbox_${document.id}")
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = if (isSelected) "নির্বাচিত" else "অনির্বাচিত",
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(28.dp)
                    ) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.testTag("btn_delete_gallery_${document.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "মুছে ফেলুন",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Bottom-Right: Page count badge
                if (document.pageCount > 1) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 6.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = "${BengaliNumberUtils.toBengaliDigits(document.pageCount.toLong())} পাতা",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Metadata Summary Details Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Parcel Identifiers (Khatian & Dag)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val khatian = document.primaryKhatianNo ?: "—"
                    val dag = document.primaryDagNo ?: "—"
                    Text(
                        text = "খতিয়ান: $khatian",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "দাগ: $dag",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Mouza & District
                val location = listOfNotNull(
                    document.primaryMouza?.takeIf { it.isNotBlank() }?.let { "মৌজা: $it" },
                    document.primaryDistrict?.takeIf { it.isNotBlank() }
                ).joinToString(", ")
                if (location.isNotBlank()) {
                    Text(
                        text = location,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Owner
                document.ownersSummary?.takeIf { it.isNotBlank() }?.let { owner ->
                    Text(
                        text = "মালিক: $owner",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * High-Resolution Image Preview Dialog with zoom and metadata details.
 */
@Composable
fun RecordImagePreviewDialog(
    document: LandDocumentEntity,
    onDismiss: () -> Unit,
    onNavigateToDetail: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val imageFile = remember(document.id) {
        findDocumentImageFile(context, document)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp)
                .testTag("gallery_preview_dialog_${document.id}")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = document.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${document.classifiedType} • ${BengaliNumberUtils.formatTimestampToBengaliDate(document.dateCaptured)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "বন্ধ করুন")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Image Preview Frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.85f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF7F5EE)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageFile != null && imageFile.exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageFile)
                                .crossfade(true)
                                .build(),
                            contentDescription = document.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "মূল স্ক্যান কপি প্রস্তুত আছে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Metadata Details Box
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("খতিয়ান নং: ${document.primaryKhatianNo ?: "—"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("দাগ নং: ${document.primaryDagNo ?: "—"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("মৌজা: ${document.primaryMouza ?: "—"}", style = MaterialTheme.typography.bodySmall)
                            Text("জে. এল.: ${document.primaryJlNo ?: "—"}", style = MaterialTheme.typography.bodySmall)
                        }
                        document.ownersSummary?.let { owners ->
                            Spacer(modifier = Modifier.height(3.dp))
                            Text("মালিক: $owners", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions: Full Detail vs Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_preview_delete")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("মুছে ফেলুন")
                    }

                    Button(
                        onClick = onNavigateToDetail,
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("btn_preview_view_detail")
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সম্পূর্ণ বিবরণ")
                    }
                }
            }
        }
    }
}

/**
 * Filter Chips Row for the Gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryCategoryFilterRow(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val categories = listOf(
        "ALL" to "সব ইমেজ",
        "CS" to "সি এস (CS)",
        "SA" to "এস এ (SA)",
        "RS" to "আর এস (RS)",
        "BRS_BS" to "বি আর এস",
        "NAMJARI_MUTATION" to "নামজারি",
        "DEED_DALIL" to "দলিল",
        "DAKHILA_TAX" to "দাখিলা"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 2.dp),
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
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}

/**
 * Helper to locate document image file across candidate sandbox files and source paths.
 */
fun findDocumentImageFile(context: android.content.Context, document: LandDocumentEntity): File? {
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
    if (foundCandidate != null) return foundCandidate

    val dirImage = pagesDir.listFiles()?.firstOrNull { file ->
        file.isFile && file.length() > 0L &&
                file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")
    }
    if (dirImage != null) return dirImage

    val srcFile = File(document.sourceFilePath)
    if (srcFile.exists() && srcFile.length() > 0L && (
                srcFile.extension.equals("jpg", true) ||
                srcFile.extension.equals("png", true) ||
                srcFile.extension.equals("jpeg", true) ||
                srcFile.extension.equals("webp", true))) {
        return srcFile
    }
    return null
}

private fun getCategoryBadgeColor(type: String): Color {
    return when (type.uppercase()) {
        "CS" -> Color(0xFF1E88E5)
        "SA" -> Color(0xFF43A047)
        "RS" -> Color(0xFFFB8C00)
        "BRS", "BRS_BS" -> Color(0xFF8E24AA)
        "NAMJARI_MUTATION" -> Color(0xFF00897B)
        "DEED_DALIL" -> Color(0xFFD81B60)
        else -> Color(0xFF546E7A)
    }
}
