package com.example.ui.screens.main

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.model.BengaliNumberUtils
import com.example.domain.drive.GoogleDriveManager
import com.example.domain.export.ExportFormat
import com.example.domain.export.ReportExportManager
import com.example.domain.matcher.EvidenceTier
import com.example.domain.validation.ForensicValidationEngine
import com.example.domain.validation.IssueSeverity
import com.example.domain.validation.ValidationIssue
import com.example.ui.components.LegalDisclaimerBanner
import com.example.ui.screens.atlas.AtlasTimelineScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.home.components.CapturedRecordCard
import com.example.ui.screens.matcher.WarishMatcherScreen
import com.example.ui.screens.matcher.WarishMatcherViewModel
import com.example.ui.screens.queue.ExtractionQueueItem
import com.example.ui.screens.queue.ExtractionQueueViewModel
import com.example.ui.screens.queue.QueueItemStatus
import com.example.ui.screens.saf.SafImportViewModel
import com.example.ui.theme.StatusConflicted
import com.example.ui.theme.StatusProbable
import com.example.ui.theme.StatusRecordGap
import com.example.ui.theme.StatusUncertain
import com.example.ui.theme.StatusVerified
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffoldScreen(
    homeViewModel: HomeViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToTesseract: () -> Unit,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val queueViewModel: ExtractionQueueViewModel = viewModel()
    val queueUiState by queueViewModel.uiState.collectAsStateWithLifecycle()
    val safViewModel: SafImportViewModel = viewModel()
    val safUiState by safViewModel.uiState.collectAsStateWithLifecycle()
    val warishViewModel: WarishMatcherViewModel = viewModel()

    // SAF file picker for manual / mass file import (PDF, Images)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            homeViewModel.importFilesFromUris(uris) { count ->
                Toast.makeText(context, "$count টি নথি সফলভাবে আপলোড হয়েছে", Toast.LENGTH_SHORT).show()
                uris.forEach { uri ->
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "imported_document"
                    queueViewModel.addDocumentToQueue(title = fileName, category = "CS", uri = uri)
                }
            }
        }
    }

    val driveAccountUri = remember {
        GoogleDriveManager.buildDriveAccountRootUri(GoogleDriveManager.DEFAULT_DRIVE_ACCOUNT_EMAIL)
    }

    // SAF directory picker for bulk folder scanning
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            safViewModel.scanFolderTree(treeUri)
            Toast.makeText(context, "ফোল্ডার স্ক্যান শুরু হয়েছে...", Toast.LENGTH_SHORT).show()
        }
    }

    // Dedicated Google Drive SAF picker targeting tapos.cpl@gmail.com
    val drivePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            safViewModel.scanFolderTree(treeUri)
            Toast.makeText(
                context,
                "গুগল ড্রাইভ (${GoogleDriveManager.DEFAULT_DRIVE_ACCOUNT_EMAIL}) স্ক্যান শুরু হয়েছে...",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Newaz Land Extractor",
                            style = MaterialTheme.typography.titleMedium,
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
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("btn_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "সেটিংস",
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
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("main_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "হোম") },
                    label = { Text("হোম", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_home")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.UploadFile, contentDescription = "ইমপোর্ট") },
                    label = { Text("ইমপোর্ট", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_import")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Description, contentDescription = "রেকর্ড") },
                    label = { Text("রেকর্ড", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_records")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = "বিশ্লেষণ") },
                    label = { Text("বিশ্লেষণ", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_analysis")
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = "রিপোর্ট") },
                    label = { Text("রিপোর্ট", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_reports")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> HomeTabContent(
                    homeViewModel = homeViewModel,
                    onNavigateToDetail = onNavigateToDetail,
                    onGoToImport = { selectedTab = 1 },
                    onGoToWarish = { selectedTab = 3 }
                )
                1 -> ImportTabContent(
                    onNavigateToCamera = onNavigateToCamera,
                    onLaunchFilePicker = { filePickerLauncher.launch(arrayOf("application/pdf", "image/*")) },
                    onLaunchFolderPicker = { folderPickerLauncher.launch(null) },
                    onLaunchDrivePicker = {
                        Toast.makeText(
                            context,
                            "গুগল ড্রাইভ খুলছে: ${GoogleDriveManager.DEFAULT_DRIVE_ACCOUNT_EMAIL}",
                            Toast.LENGTH_SHORT
                        ).show()
                        drivePickerLauncher.launch(driveAccountUri)
                    },
                    queueViewModel = queueViewModel,
                    safViewModel = safViewModel
                )
                2 -> RecordsTabContent(
                    homeViewModel = homeViewModel,
                    onNavigateToDetail = onNavigateToDetail
                )
                3 -> AnalysisTabContent(
                    homeViewModel = homeViewModel,
                    warishViewModel = warishViewModel
                )
                4 -> ReportsTabContent(
                    homeViewModel = homeViewModel
                )
            }
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            onDismiss = { showSettingsDialog = false },
            onNavigateToTesseract = {
                showSettingsDialog = false
                onNavigateToTesseract()
            },
            onClearData = {
                coroutineScope.launch {
                    homeViewModel.repository.purgeLegacyStaleRecords()
                    val all = homeViewModel.repository.getAllDocumentsListDirect()
                    all.forEach { homeViewModel.deleteDocument(it.id) }
                    queueViewModel.clearQueue()
                    showSettingsDialog = false
                    Toast.makeText(context, "সকল ডেটা সফলভাবে মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// =========================================================================
// TAB 0: HOME
// =========================================================================

@Composable
fun HomeTabContent(
    homeViewModel: HomeViewModel,
    onNavigateToDetail: (String) -> Unit,
    onGoToImport: () -> Unit,
    onGoToWarish: () -> Unit
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    var docToDelete by remember { mutableStateOf<LandDocumentEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen_content"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Legal Disclaimer Banner
        item {
            LegalDisclaimerBanner(modifier = Modifier.fillMaxWidth())
        }

        // Real Summary Metrics (Total, Unverified, Confirmed, Conflicted, 0-Byte)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricStatCard(
                        title = "মোট নথি",
                        count = BengaliNumberUtils.toBengaliDigits(uiState.totalCount.toLong()),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        testTag = "stat_total_count"
                    )
                    MetricStatCard(
                        title = "অযাচাইকৃত",
                        count = BengaliNumberUtils.toBengaliDigits(uiState.unverifiedCount.toLong()),
                        color = StatusUncertain,
                        modifier = Modifier.weight(1f),
                        testTag = "stat_unverified_count"
                    )
                    MetricStatCard(
                        title = "নিশ্চিতকৃত",
                        count = BengaliNumberUtils.toBengaliDigits(uiState.confirmedCount.toLong()),
                        color = StatusVerified,
                        modifier = Modifier.weight(1f),
                        testTag = "stat_confirmed_count"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricStatCard(
                        title = "অসঙ্গতিপূর্ণ",
                        count = BengaliNumberUtils.toBengaliDigits(uiState.conflictedCount.toLong()),
                        color = StatusConflicted,
                        modifier = Modifier.weight(1f),
                        testTag = "stat_conflicted_count"
                    )
                    MetricStatCard(
                        title = "০-বাইট ত্রুটি",
                        count = BengaliNumberUtils.toBengaliDigits(uiState.zeroByteCount.toLong()),
                        color = if (uiState.zeroByteCount > 0) StatusRecordGap else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                        testTag = "stat_zero_byte_count"
                    )
                }
            }
        }

        // 2 Primary Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onGoToImport,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_home_import"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ইমপোর্ট", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                FilledTonalButton(
                    onClick = onGoToWarish,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_home_warish"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ওয়ারিশ মেলান", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Recent Documents Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "সাম্প্রতিক নথি (সর্বশেষ ৫টি)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "মোট: ${BengaliNumberUtils.toBengaliDigits(uiState.totalCount.toLong())} টি",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Recent Documents List or Clean Empty State
        if (uiState.recentDocuments.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .testTag("home_empty_state"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "কোনো ভূমি রেকর্ড নথি নেই",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "নতুন দলিল, খতিয়ান বা নকশা যোগ করতে 'ইমপোর্ট' বাটনে চাপুন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(items = uiState.recentDocuments, key = { it.id }) { doc ->
                CapturedRecordCard(
                    document = doc,
                    onClick = { onNavigateToDetail(doc.id) },
                    onDelete = { docToDelete = doc }
                )
            }
        }
    }

    docToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text("নথি মুছে ফেলার নিশ্চিতকরণ") },
            text = { Text("আপনি কি নিশ্চিতভাবে '${doc.title}' নথিটি মুছে ফেলতে চান?") },
            confirmButton = {
                Button(
                    onClick = {
                        homeViewModel.deleteDocument(doc.id)
                        docToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("মুছে ফেলুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

// =========================================================================
// TAB 1: IMPORT
// =========================================================================

@Composable
fun ImportTabContent(
    onNavigateToCamera: () -> Unit,
    onLaunchFilePicker: () -> Unit,
    onLaunchFolderPicker: () -> Unit,
    onLaunchDrivePicker: () -> Unit = onLaunchFolderPicker,
    queueViewModel: ExtractionQueueViewModel,
    safViewModel: SafImportViewModel
) {
    val queueState by queueViewModel.uiState.collectAsStateWithLifecycle()
    val safState by safViewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("import_tab_content"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "নথি ইনপুট উৎস নির্বাচন করুন",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 4 Source Cards in a 2x2 Grid Layout
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SourceActionCard(
                        title = "ক্যামেরা স্ক্যান",
                        subtitle = "দাগ ও খতিয়ান ছবি তোলা",
                        icon = Icons.Default.CameraAlt,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        testTag = "card_import_camera",
                        onClick = onNavigateToCamera
                    )
                    SourceActionCard(
                        title = "ফাইল নির্বাচন",
                        subtitle = "PDF, JPG, PNG, TIFF",
                        icon = Icons.Default.UploadFile,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                        testTag = "card_import_files",
                        onClick = onLaunchFilePicker
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SourceActionCard(
                        title = "ফোল্ডার (SAF)",
                        subtitle = "সাবফোল্ডারসহ বাল্ক স্ক্যান",
                        icon = Icons.Default.FolderOpen,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        testTag = "card_import_folder",
                        onClick = onLaunchFolderPicker
                    )
                    SourceActionCard(
                        title = "গুগল ড্রাইভ",
                        subtitle = GoogleDriveManager.DEFAULT_DRIVE_ACCOUNT_EMAIL,
                        icon = Icons.Default.CloudDownload,
                        color = Color(0xFF0F766E),
                        modifier = Modifier.weight(1f),
                        testTag = "card_import_drive",
                        onClick = onLaunchDrivePicker
                    )
                }
            }
        }

        // Live SAF Folder Scanning Status
        if (safState.isScanning || safState.scanSummary != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (safState.isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ফোল্ডার স্ক্যান হচ্ছে...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusVerified)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ফোল্ডার স্ক্যান সম্পন্ন", fontWeight = FontWeight.Bold)
                            }
                        }
                        safState.scanSummary?.let { summary ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "মোট ফাইল: ${summary.totalFilesFound} | সমর্থিত দলিল: ${summary.supportedFiles.size}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Sequential Extraction Queue Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ধারাবাহিক নিষ্কাশন কিউ (Extraction Queue)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "অপেক্ষমাণ: ${queueState.totalQueued} | সম্পন্ন: ${queueState.completedCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (queueState.isRunning) {
                        OutlinedButton(
                            onClick = { queueViewModel.pauseExtraction() },
                            modifier = Modifier.testTag("btn_pause_queue")
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("বিরতি")
                        }
                    } else if (queueState.items.isNotEmpty()) {
                        Button(
                            onClick = { queueViewModel.startSequentialExtraction() },
                            modifier = Modifier.testTag("btn_start_queue")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("শুরু করুন")
                        }
                    }
                }
            }
        }

        // Queue Items List or Clean Empty State
        if (queueState.items.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_queue_empty"),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "কিউ বর্তমানে খালি",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "উপরের কার্ড চেপে ক্যামেরা, ফাইল বা ফোল্ডার থেকে নথি যুক্ত করুন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(items = queueState.items, key = { it.id }) { queueItem ->
                QueueItemRow(
                    item = queueItem,
                    onRemove = { queueViewModel.removeDocument(queueItem.id) }
                )
            }
        }
    }
}

@Composable
fun SourceActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun QueueItemRow(
    item: ExtractionQueueItem,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("queue_item_${item.id}"),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.status.bengaliLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = when (item.status) {
                            QueueItemStatus.COMPLETED -> StatusVerified
                            QueueItemStatus.FAILED -> StatusConflicted
                            QueueItemStatus.QUEUED -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Clear, contentDescription = "Remove", tint = MaterialTheme.colorScheme.outline)
                }
            }

            if (item.progress > 0f && item.status != QueueItemStatus.COMPLETED) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item.extractedSummary?.let { summary ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// =========================================================================
// TAB 2: RECORDS (Consolidates CapturedRecords & Gallery)
// =========================================================================

@Composable
fun RecordsTabContent(
    homeViewModel: HomeViewModel,
    onNavigateToDetail: (String) -> Unit
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    var isGridView by remember { mutableStateOf(false) }
    var selectedStatusFilter by remember { mutableStateOf("ALL") }
    var docToDelete by remember { mutableStateOf<LandDocumentEntity?>(null) }

    val categories = listOf("ALL", "CS", "SA", "RS", "BRS", "NAMJARI", "DEED", "WARIS", "MAPS", "ZERO_BYTE")
    val statusFilters = listOf("ALL", "CONFIRMED", "PROBABLE", "CONFLICTED", "UNVERIFIED")

    val displayedDocs = remember(uiState.filteredDocuments, selectedStatusFilter) {
        if (selectedStatusFilter == "ALL") {
            uiState.filteredDocuments
        } else {
            uiState.filteredDocuments.filter { doc ->
                doc.status.equals(selectedStatusFilter, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("records_tab_content")
    ) {
        // Search & Layout Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { homeViewModel.onSearchQueryChanged(it) },
                placeholder = { Text("খতিয়ান, দাগ, মৌজা বা মালিক খুঁজুন...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { homeViewModel.onSearchQueryChanged("") }) {
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
                onClick = { isGridView = !isGridView },
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .testTag("btn_toggle_layout")
            ) {
                Icon(
                    imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = "Toggle Grid/List",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = uiState.selectedCategory == cat,
                    onClick = { homeViewModel.onCategorySelected(cat) },
                    label = { Text(cat, fontSize = 11.sp) },
                    modifier = Modifier.testTag("chip_cat_${cat.lowercase()}")
                )
            }
        }

        // Evidence Status Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            statusFilters.forEach { status ->
                FilterChip(
                    selected = selectedStatusFilter == status,
                    onClick = { selectedStatusFilter = status },
                    label = { Text(status, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (status) {
                            "CONFIRMED" -> StatusVerified.copy(alpha = 0.2f)
                            "PROBABLE" -> StatusProbable.copy(alpha = 0.2f)
                            "CONFLICTED" -> StatusConflicted.copy(alpha = 0.2f)
                            "UNVERIFIED" -> StatusUncertain.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                    modifier = Modifier.testTag("chip_status_${status.lowercase()}")
                )
            }
        }

        if (displayedDocs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .testTag("records_empty_state"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "কোনো রেকর্ড পাওয়া যায়নি",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ফিল্টার পরিবর্তন করুন অথবা নতুন নথি ইমপোর্ট করুন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else if (isGridView) {
            // Grid Mode (from RecordGalleryScreen)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("record_gallery_grid"),
                contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = displayedDocs, key = { it.id }) { doc ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToDetail(doc.id) }
                            .testTag("gallery_item_${doc.id}"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.2f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = doc.sourceFilePath,
                                    contentDescription = doc.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    shape = RoundedCornerShape(bottomStart = 8.dp),
                                    color = when (doc.status) {
                                        "CONFIRMED" -> StatusVerified
                                        "PROBABLE" -> StatusProbable
                                        "CONFLICTED" -> StatusConflicted
                                        else -> StatusUncertain
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        text = doc.status,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = doc.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "খতিয়ান: ${doc.primaryKhatianNo ?: "—"} | দাগ: ${doc.primaryDagNo ?: "—"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // List Mode (from CapturedRecordsScreen)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("captured_records_list"),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = displayedDocs, key = { it.id }) { doc ->
                    CapturedRecordCard(
                        document = doc,
                        onClick = { onNavigateToDetail(doc.id) },
                        onDelete = { docToDelete = doc }
                    )
                }
            }
        }
    }

    docToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text("নথি মুছে ফেলুন") },
            text = { Text("আপনি কি নিশ্চিতভাবে '${doc.title}' মুছে ফেলতে চান?") },
            confirmButton = {
                Button(
                    onClick = {
                        homeViewModel.deleteDocument(doc.id)
                        docToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("মুছে ফেলুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

// =========================================================================
// TAB 3: ANALYSIS (Warish Matcher, Historical Atlas, Forensic Audit)
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisTabContent(
    homeViewModel: HomeViewModel,
    warishViewModel: WarishMatcherViewModel
) {
    var subTab by remember { mutableIntStateOf(0) }
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("analysis_tab_content")
    ) {
        PrimaryTabRow(selectedTabIndex = subTab) {
            Tab(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                text = { Text("১. ওয়ারিশ মিলকরণ", fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("subtab_warish")
            )
            Tab(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                text = { Text("২. ঐতিহাসিক অ্যাটলাস", fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("subtab_atlas")
            )
            Tab(
                selected = subTab == 2,
                onClick = { subTab = 2 },
                text = { Text("৩. অসঙ্গতি ও আইনি অডিট", fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("subtab_discrepancy")
            )
        }

        when (subTab) {
            0 -> WarishMatcherScreen(
                viewModel = warishViewModel,
                onNavigateBack = {}
            )
            1 -> AtlasTimelineScreen(
                onNavigateBack = {},
                records = homeUiState.documents
            )
            2 -> ForensicDiscrepancyTab(
                documents = homeUiState.documents
            )
        }
    }
}

@Composable
fun ForensicDiscrepancyTab(documents: List<LandDocumentEntity>) {
    val validationEngine = remember { ForensicValidationEngine() }

    val discrepancies = remember(documents) {
        val list = mutableListOf<ValidationIssue>()
        for (doc in documents) {
            val owners = doc.ownersSummary ?: ""
            val area = doc.primaryAreaDecimals?.toDoubleOrNull() ?: 0.0

            if (area > 200.0) {
                list.add(
                    ValidationIssue(
                        category = com.example.domain.validation.ValidationIssueCategory.AREA_MISMATCH,
                        severity = IssueSeverity.WARNING,
                        descriptionBn = "${doc.title}: জমির পরিমাণ অস্বাভাবিক রকম বেশি ($area শতাংশ)",
                        descriptionEn = "Unusually high land area ($area decimals)",
                        fieldName = "area"
                    )
                )
            }
            if (doc.classifiedType.equals("CS", ignoreCase = true) && !owners.contains("পিতা")) {
                list.add(
                    ValidationIssue(
                        category = com.example.domain.validation.ValidationIssueCategory.FATHER_MISMATCH,
                        severity = IssueSeverity.INFO,
                        descriptionBn = "${doc.title}: সি এস রেকর্ডে পিতার নাম স্পষ্ট নয়",
                        descriptionEn = "Father name not distinguished in owner summary",
                        fieldName = "father_name"
                    )
                )
            }
        }
        list
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("discrepancy_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ফরেনসিক অসঙ্গতি ও স্বত্ব নিরীক্ষা (Forensic Audit Engine)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "দাগ বিভাজন, পিতার নামের অমিল, জমি ঘাটতি ও হিস্যা সমতা যাচাই।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (discrepancies.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusVerified, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("কোনো সক্রিয় অসঙ্গতি চিহ্নিত হয়নি", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (documents.isEmpty()) "কোনো নথি লোড করা হয়নি।" else "বর্তমান নথি সমূহে কোনো গুরুতর অসঙ্গতি বা আইনি ব্যবধান নেই।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(discrepancies) { issue ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (issue.severity) {
                            IssueSeverity.CRITICAL -> StatusConflicted.copy(alpha = 0.1f)
                            IssueSeverity.WARNING -> StatusRecordGap.copy(alpha = 0.1f)
                            IssueSeverity.INFO -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (issue.severity) {
                                IssueSeverity.CRITICAL -> Icons.Default.Warning
                                IssueSeverity.WARNING -> Icons.Default.Warning
                                IssueSeverity.INFO -> Icons.Default.Description
                            },
                            contentDescription = null,
                            tint = when (issue.severity) {
                                IssueSeverity.CRITICAL -> StatusConflicted
                                IssueSeverity.WARNING -> StatusRecordGap
                                IssueSeverity.INFO -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = issue.descriptionBn, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(text = issue.descriptionEn, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 4: REPORTS
// =========================================================================

@Composable
fun ReportsTabContent(homeViewModel: HomeViewModel) {
    val context = LocalContext.current
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }
    var isExporting by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("reports_tab_content"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Legal Disclaimer Banner
        item {
            LegalDisclaimerBanner(modifier = Modifier.fillMaxWidth())
        }

        // Summary Stats Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ফরেনসিক রিপোর্ট এক্সপোর্ট ড্যাশবোর্ড",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "মোট সংরক্ষিত নথি: ${BengaliNumberUtils.toBengaliDigits(uiState.totalCount.toLong())} টি\n" +
                                "যাচাইকৃত নথি: ${BengaliNumberUtils.toBengaliDigits(uiState.confirmedCount.toLong())} টি\n" +
                                "অযাচাইকৃত/ব্যবধানযুক্ত: ${BengaliNumberUtils.toBengaliDigits(uiState.unverifiedCount.toLong())} টি",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Export Format Selector
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "এক্সপোর্ট ফরম্যাট নির্বাচন করুন:", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    listOf(
                        ExportFormat.PDF to "PDF ডসিয়ার (বাংলা ফন্ট ও বুকমার্কসহ)",
                        ExportFormat.CSV to "CSV স্প্রেডশিট (এক্সেল বা গুগল শিটস)",
                        ExportFormat.JSON to "JSON স্ট্রাকচার্ড মেটাডেটা",
                        ExportFormat.ZIP to "ZIP ফরেনসিক এভিডেন্স বান্ডেল"
                    ).forEach { (format, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFormat = format }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedFormat == format,
                                onClick = { selectedFormat = format }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Run Export Button
        item {
            Button(
                onClick = {
                    isExporting = true
                    homeViewModel.exportRecords(
                        format = selectedFormat,
                        categoryFilter = uiState.selectedCategory
                    ) { file: File ->
                        isExporting = false
                        Toast.makeText(context, "রিপোর্ট এক্সপোর্ট সফল: ${file.name}", Toast.LENGTH_LONG).show()
                        ReportExportManager.shareFile(
                            context = context,
                            file = file,
                            mimeType = when (selectedFormat) {
                                ExportFormat.PDF -> "application/pdf"
                                ExportFormat.CSV -> "text/csv"
                                ExportFormat.JSON -> "application/json"
                                ExportFormat.ZIP -> "application/zip"
                                else -> "text/plain"
                            },
                            subject = "বাংলাদেশ ভূমি রেকর্ড ফরেনসিক রিপোর্ট (${selectedFormat.name})"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_run_export"),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("রিপোর্ট তৈরি হচ্ছে...")
                } else {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("রিপোর্ট তৈরি ও সংরক্ষণ করুন (SAF)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =========================================================================
// SETTINGS DIALOG
// =========================================================================

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onNavigateToTesseract: () -> Unit,
    onClearData: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("অ্যাপ সেটিংস ও কনফিগারেশন") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedButton(
                    onClick = onNavigateToTesseract,
                    modifier = Modifier.fillMaxWidth().testTag("tesseract_setup_button")
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tesseract OCR মডেল ও ভাষা প্যাক")
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Gemini AI অপটিক্যাল স্ক্যানার", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("মডেল: gemini-2.5-flash | Temperature: 0.0\nজিরো-হ্যালুসিনেশন ও হিস্যা অডিট সক্রিয়", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().testTag("settings_drive_account_card"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F766E).copy(alpha = 0.12f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "সংযুক্ত গুগল ড্রাইভ অ্যাকাউন্ট",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF0F766E)
                        )
                        Text(
                            text = GoogleDriveManager.DEFAULT_DRIVE_ACCOUNT_EMAIL,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Button(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().testTag("btn_clear_all_data")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("সকল ডেটা মুছুন (Clear All Records)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন")
            }
        }
    )

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("সকল ডেটা মুছে ফেলার নিশ্চিতকরণ") },
            text = { Text("আপনি কি নিশ্চিতভাবে অ্যাপের সংরক্ষিত সকল নথি, ছবি ও মেটাডেটা মুছে ফেলতে চান? এটি অপরিবর্তনযোগ্য।") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("হ্যাঁ, সম্পূর্ণ মুছে ফেলুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

@Composable
fun MetricStatCard(
    title: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Card(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = count, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
