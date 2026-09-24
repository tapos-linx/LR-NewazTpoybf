package com.example.ui.screens.detail

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ConfidenceLevel
import com.example.domain.export.ExportFormat
import com.example.domain.export.ReportExportManager
import com.example.ui.components.FieldConfidenceChip
import com.example.ui.components.LegalDisclaimerBanner
import com.example.ui.components.ReportExportDialog
import com.example.ui.components.ZoomableEvidenceViewer
import com.example.ui.theme.StatusProbable
import com.example.ui.theme.StatusUncertain
import com.example.ui.theme.StatusVerified

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    viewModel: DocumentDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }

    // Dialog state for editing a field
    var editingKey by remember { mutableStateOf<String?>(null) }
    var editingLabel by remember { mutableStateOf("") }
    var editingValue by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("document_detail_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.document?.title ?: "নথি বিবরণ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${uiState.recordData.recordType.bengaliName} • ${uiState.document?.sourceCategory ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.setShowExportDialog(true) },
                        modifier = Modifier.testTag("btn_export_saf_topbar")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "রিপোর্ট সংরক্ষণ (SAF)",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "মেনু",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("রিপোর্ট সেভ করুন (SAF)") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.setShowExportDialog(true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("JSON রিপোর্ট শেয়ার করুন") },
                            onClick = {
                                showMenu = false
                                viewModel.exportJson { file ->
                                    ReportExportManager.shareFile(
                                        context = context,
                                        file = file,
                                        mimeType = "application/json",
                                        subject = "নথি JSON রিপোর্ট"
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("টেক্সট সারসংক্ষেপ শেয়ার করুন") },
                            onClick = {
                                showMenu = false
                                viewModel.exportTextSummary { file ->
                                    ReportExportManager.shareFile(
                                        context = context,
                                        file = file,
                                        mimeType = "text/plain",
                                        subject = "নথি সারসংক্ষেপ"
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("যাচাইকৃত হিসেবে চিহ্নিত করুন") },
                            onClick = {
                                showMenu = false
                                viewModel.markDocumentVerified()
                                Toast.makeText(context, "নথিটি যাচাইকৃত হিসেবে চিহ্নিত হয়েছে", Toast.LENGTH_SHORT).show()
                            }
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.setShowExportDialog(true) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_export_saf_bottom")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("রিপোর্ট সেভ (SAF)", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.markDocumentVerified()
                            Toast.makeText(context, "নথিটি সফলভাবে যাচাইকৃত ও নিশ্চিত করা হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusVerified),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("btn_confirm_verified")
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("যাচাই নিশ্চিত করুন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("১. দলিল প্রমাণ ছবি", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("২. নিষ্কাশিত তথ্য", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("৩. কাঁচা OCR", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                when (selectedTab) {
                    0 -> EvidenceTab(
                        uiState = uiState,
                        onSelectPage = { viewModel.selectPage(it) },
                        onToggleProcessed = { viewModel.toggleProcessedImage(it) }
                    )
                    1 -> ExtractedFieldsTab(
                        uiState = uiState,
                        onEditField = { key, label, currentVal ->
                            editingKey = key
                            editingLabel = label
                            editingValue = currentVal
                        }
                    )
                    2 -> RawOcrTab(
                        uiState = uiState,
                        onCopyText = { text ->
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "OCR লেখা কপি হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // Edit Field Dialog
    editingKey?.let { key ->
        AlertDialog(
            onDismissRequest = { editingKey = null },
            title = { Text(editingLabel) },
            text = {
                Column {
                    Text(
                        text = "মূল দলিলের ছবির সাথে মিলিয়ে সঠিক তথ্য লিখুন:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingValue,
                        onValueChange = { editingValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateField(key, editingValue)
                        editingKey = null
                    }
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingKey = null }) {
                    Text("বাতিল")
                }
            }
        )
    }

    if (uiState.showExportDialog) {
        ReportExportDialog(
            documentTitle = uiState.document?.title ?: "ভূমি রেকর্ড সারসংক্ষেপ",
            suggestedFileNameJson = viewModel.suggestFileName(ExportFormat.JSON),
            suggestedFileNameText = viewModel.suggestFileName(ExportFormat.TEXT),
            suggestedFileNameCsv = viewModel.suggestFileName(ExportFormat.CSV),
            jsonPreview = viewModel.getReportContentPreview(ExportFormat.JSON),
            textPreview = viewModel.getReportContentPreview(ExportFormat.TEXT),
            csvPreview = viewModel.getReportContentPreview(ExportFormat.CSV),
            initialFormat = ExportFormat.CSV,
            onSaveToSafUri = { uri, format ->
                viewModel.saveReportToSafUri(uri, format) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            },
            onShare = { format ->
                viewModel.setShowExportDialog(false)
                when (format) {
                    ExportFormat.CSV -> {
                        viewModel.exportCsv { file ->
                            ReportExportManager.shareFile(
                                context = context,
                                file = file,
                                mimeType = "text/csv",
                                subject = "বাংলাদেশ ভূমি রেকর্ড মেটাডাটা CSV"
                            )
                        }
                    }
                    ExportFormat.JSON -> {
                        viewModel.exportJson { file ->
                            ReportExportManager.shareFile(
                                context = context,
                                file = file,
                                mimeType = "application/json",
                                subject = "বাংলাদেশ ভূমি রেকর্ড JSON রিপোর্ট"
                            )
                        }
                    }
                    ExportFormat.TEXT -> {
                        viewModel.exportTextSummary { file ->
                            ReportExportManager.shareFile(
                                context = context,
                                file = file,
                                mimeType = "text/plain",
                                subject = "বাংলাদেশ ভূমি রেকর্ড সারসংক্ষেপ"
                            )
                        }
                    }
                }
            },
            onDismiss = { viewModel.setShowExportDialog(false) }
        )
    }
}

@Composable
private fun EvidenceTab(
    uiState: DocumentDetailUiState,
    onSelectPage: (Int) -> Unit,
    onToggleProcessed: (Boolean) -> Unit
) {
    val pages = uiState.pages
    val selectedPage = pages.getOrNull(uiState.selectedPageIndex) ?: pages.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Multi-page selector row (if multiple pages)
        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pages.forEachIndexed { index, page ->
                    val isSelected = index == uiState.selectedPageIndex
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectPage(index) }
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = "পাতা ${page.pageIndex} (${page.ocrConfidence.toInt()}%)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Toggle between Pristine Raw and Binarized Enhanced Image
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onToggleProcessed(false) },
                    color = if (!uiState.showProcessedImage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "মূল প্রমাণ ছবি (Raw)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (!uiState.showProcessedImage) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onToggleProcessed(true) },
                    color = if (uiState.showProcessedImage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "বাইনারাইজড OCR কপি",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (uiState.showProcessedImage) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "${uiState.selectedPageIndex + 1}/${pages.size.coerceAtLeast(1)} পাতা",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // Zoomable Canvas / Image Viewer
        if (selectedPage != null) {
            val imgPath = if (uiState.showProcessedImage && selectedPage.processedImagePath != null) {
                selectedPage.processedImagePath
            } else {
                selectedPage.rawImagePath
            }

            ZoomableEvidenceViewer(
                imagePath = imgPath,
                modifier = Modifier.weight(1f),
                initialRotation = selectedPage.rotationDegrees
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.LightGray, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("নথির কোনো প্রমাণ ছবি উপলব্ধ নেই")
            }
        }
    }
}

@Composable
private fun ExtractedFieldsTab(
    uiState: DocumentDetailUiState,
    onEditField: (String, String, String) -> Unit
) {
    val data = uiState.recordData

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Legal Disclaimer Banner
        item {
            LegalDisclaimerBanner(initiallyExpanded = false)
        }

        // Validation Warnings Alert (if any)
        if (data.validationWarnings.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StatusProbable.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = StatusProbable)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "যাচাইকরণ পর্যবেক্ষণ ও সতর্কতা",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusProbable
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        for (warning in data.validationWarnings) {
                            Text(
                                text = "• $warning",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 1. Geography Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "১. ভৌগোলিক অবস্থান ও রেকর্ড পরিচিতি",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    EditableFieldRow("জেলা (District)", data.district.value, data.district.confidence) {
                        onEditField("district", "জেলা পরিবর্তন করুন", data.district.value)
                    }
                    EditableFieldRow("উপজেলা / থানা", data.upazilaThana.value, data.upazilaThana.confidence) {
                        onEditField("upazilaThana", "উপজেলা/থানা পরিবর্তন করুন", data.upazilaThana.value)
                    }
                    EditableFieldRow("মৌজা (Mouza)", data.mouza.value, data.mouza.confidence) {
                        onEditField("mouza", "মৌজা পরিবর্তন করুন", data.mouza.value)
                    }
                    EditableFieldRow("জে. এল. নং (JL No)", data.jlNo.value, data.jlNo.confidence) {
                        onEditField("jlNo", "জে. এল. নং পরিবর্তন করুন", data.jlNo.value)
                    }
                }
            }
        }

        // 2. Khatian & Dag Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "২. খতিয়ান ও দাগের বিস্তারিত",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    EditableFieldRow("খতিয়ান নং (Khatian)", data.khatianNo.value, data.khatianNo.confidence) {
                        onEditField("khatianNo", "খতিয়ান নং পরিবর্তন করুন", data.khatianNo.value)
                    }
                    EditableFieldRow("দাগ নং (Dag/Plot)", data.dagNo.value, data.dagNo.confidence) {
                        onEditField("dagNo", "দাগ নং পরিবর্তন করুন", data.dagNo.value)
                    }
                    EditableFieldRow("সাবেক দাগ নং", data.formerDagNo.value, data.formerDagNo.confidence) {
                        onEditField("formerDagNo", "সাবেক দাগ নং পরিবর্তন করুন", data.formerDagNo.value)
                    }
                    EditableFieldRow("হাল দাগ নং", data.halDagNo.value, data.halDagNo.confidence) {
                        onEditField("halDagNo", "হাল দাগ নং পরিবর্তন করুন", data.halDagNo.value)
                    }
                }
            }
        }

        // 3. Land Class, Area & Rent Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "৩. জমির শ্রেণি ও পরিমাণ",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    EditableFieldRow("জমির শ্রেণি", data.landClass.value, data.landClass.confidence) {
                        onEditField("landClass", "জমির শ্রেণি পরিবর্তন করুন", data.landClass.value)
                    }
                    EditableFieldRow("জমির পরিমাণ (শতক/শতাংশ)", "${data.areaDecimals.value} শতাংশ", data.areaDecimals.confidence) {
                        onEditField("areaDecimals", "শতাংশ পরিবর্তন করুন", data.areaDecimals.value)
                    }
                    EditableFieldRow("জমির পরিমাণ (একর)", "${data.areaAcres.value} একর", data.areaAcres.confidence) {
                        onEditField("areaAcres", "একর পরিবর্তন করুন", data.areaAcres.value)
                    }
                    EditableFieldRow("বার্ষিক দাবি / খাজনা", data.annualRent.value, data.annualRent.confidence) {
                        onEditField("annualRent", "বার্ষিক খাজনা পরিবর্তন করুন", data.annualRent.value)
                    }
                }
            }
        }

        // 4. Owners & Khatianidars
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "৪. মালিক ও রায়তের বিবরণ",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "মোট অংশ: ${data.hissaTotalCalculated}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (data.isHissaValid) StatusVerified else StatusUncertain
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    for ((idx, owner) in data.owners.withIndex()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${idx + 1}. ${owner.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (owner.fatherOrHusbandName.isNotBlank()) {
                                    Text(
                                        text = "পিতা/স্বামী: ${owner.fatherOrHusbandName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "অংশ: ${owner.shareHissa}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Spacing at bottom
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun EditableFieldRow(
    label: String,
    value: String,
    confidence: ConfidenceLevel,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = if (value.isBlank()) "—" else value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldConfidenceChip(confidence = confidence)
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "সম্পাদনা",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun RawOcrTab(
    uiState: DocumentDetailUiState,
    onCopyText: (String) -> Unit
) {
    val pages = uiState.pages
    val selectedPage = pages.getOrNull(uiState.selectedPageIndex) ?: pages.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (selectedPage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "OCR নির্ভুলতার মান: ${selectedPage.ocrConfidence.toInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ভাষা: ${selectedPage.ocrLanguage} • সময়কাল: ${selectedPage.ocrDurationMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { onCopyText(selectedPage.rawOcrText) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("কপি করুন", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Text(
                    text = selectedPage.rawOcrText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(14.dp),
                    lineHeight = 22.sp
                )
            }
        } else {
            Text("কোনো OCR পাঠ পাওয়া যায়নি")
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
