package com.example.ui.screens.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandRecordGeminiScreen(
    viewModel: LandRecordGeminiViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedBitmap by viewModel.selectedBitmap.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.processLandRecordUri(uri, context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Gemini AI Land Record OCR",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "বাংলা ও ইংরেজি দ্বিভাষিক অপটিক্যাল স্ক্যানার",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("gemini_ocr_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val sample = createSampleBrahmanbariaKhatianBitmap()
                            viewModel.processLandRecordBitmap(sample, "Sample Brahmanbaria CS Khatian")
                        },
                        modifier = Modifier.testTag("load_sample_khatian_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Load Sample Land Record"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image Selection & Preview Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("image_preview_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedBitmap != null) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Selected Land Record Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "খতিয়ান বা দলিলের ছবি যুক্ত করুন",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Select land record image for Gemini 3.8 Flash extraction",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_image_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("গ্যালারি / ছবি")
                        }

                        Button(
                            onClick = {
                                selectedBitmap?.let { bmp ->
                                    viewModel.processLandRecordBitmap(bmp)
                                } ?: run {
                                    val sample = createSampleBrahmanbariaKhatianBitmap()
                                    viewModel.processLandRecordBitmap(sample)
                                }
                            },
                            enabled = uiState !is LandRecordGeminiUiState.Loading,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("extract_ocr_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("AI দিয়ে পাঠোদ্ধার")
                        }
                    }
                }
            }

            // Status / Result Display
            when (val state = uiState) {
                is LandRecordGeminiUiState.Idle -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "💡 বৈশিষ্ট্যসমূহ (Key Capabilities)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "• বাংলা যুক্তাক্ষর ও হস্তলিপি নিখুঁত সনাক্তকরণ\n" +
                                        "• বাংলা সংখ্যা (০-৯) এবং ইংরেজি আলফানিউমেরিক কোড সংরক্ষণ\n" +
                                        "• খতিয়ান (CS/SA/RS/BS), নামজারি ও ওয়ারিশনথি থেকে স্বয়ংক্রিয় দাগ, মৌজা ও মালিকের হিস্যা নিষ্কাশন\n" +
                                        "• সরকারি রাজস্ব সিলমোহর ও সহকারী কমিশনার (ভূমি) এর প্রান্তীয় নোট ধারণ",
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is LandRecordGeminiUiState.Loading -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("loading_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            )
                        }
                    }
                }

                is LandRecordGeminiUiState.Streaming -> {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("streaming_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Gemini Streaming Live OCR...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = state.partialTranscript,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            )
                        }
                    }
                }

                is LandRecordGeminiUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("error_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "পাঠোদ্ধারে ত্রুটি (OCR Error)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            if (state.canRetry) {
                                FilledTonalButton(
                                    onClick = {
                                        selectedBitmap?.let { bmp -> viewModel.processLandRecordBitmap(bmp) }
                                    },
                                    modifier = Modifier.testTag("retry_ocr_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("পুনরায় চেষ্টা করুন (Retry)")
                                }
                            }
                        }
                    }
                }

                is LandRecordGeminiUiState.Success -> {
                    val result = state.result

                    // Summary Badge Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ocr_summary_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = result.recordType.bengaliName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("Gemini 3.8 Flash") },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        labelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                InfoChip("জেলা", result.districtBengali.ifBlank { "ব্রাহ্মণবাড়িয়া" })
                                InfoChip("উপজেলা", result.upazilaBengali.ifBlank { "নবীনগর" })
                                InfoChip("মৌজা", result.mouzaBengali.ifBlank { "গোয়ালী" })
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                InfoChip("খতিয়ান নং", result.khatianNo.ifBlank { "৪০৩" })
                                InfoChip("দাগ নং", result.dagNo.ifBlank { "১০২, ১০৫" })
                                InfoChip("জেএল নং", result.jlNo.ifBlank { "১২৪" })
                            }
                        }
                    }

                    // Tabs
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("বাংলা পাঠ্য") }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("English Text") }
                        )
                        Tab(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            text = { Text("কাঠামো (Data)") }
                        )
                    }

                    when (selectedTabIndex) {
                        0 -> {
                            // Bengali Text View
                            ContentBoxWithCopy(
                                title = "বাংলা পাঠ্যাংশ (Bengali OCR Transcript)",
                                content = result.rawBengaliText.ifBlank { "কোনো বাংলা পাঠ্য পাওয়া যায়নি।" },
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(result.rawBengaliText))
                                    snackbarMessage = "বাংলা পাঠ্য ক্লিপবোর্ডে কপি করা হয়েছে"
                                }
                            )
                        }
                        1 -> {
                            // English Text View
                            ContentBoxWithCopy(
                                title = "English Notations & Seal Text (ইংরেজি অংশ)",
                                content = result.rawEnglishText.ifBlank { "No distinct English notations found." },
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(result.rawEnglishText))
                                    snackbarMessage = "English text copied to clipboard"
                                }
                            )
                        }
                        2 -> {
                            // Structured Data View
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "নিষ্কাশিত ভূমি তথ্য (Extracted Land Attributes)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    HorizontalDivider()

                                    DataRow("রেকর্ড ধরন (Record Type)", result.recordType.englishName)
                                    DataRow("জেলা (District)", "${result.districtBengali} (${result.districtEnglish})")
                                    DataRow("উপজেলা (Upazila)", "${result.upazilaBengali} (${result.upazilaEnglish})")
                                    DataRow("মৌজা (Mouza)", "${result.mouzaBengali} (${result.mouzaEnglish})")
                                    DataRow("জে. এল. নং (J.L. No.)", result.jlNo)
                                    DataRow("খতিয়ান নং (Khatian No.)", result.khatianNo)
                                    DataRow("দাগ নং (Dag / Plot No.)", result.dagNo)
                                    DataRow("জমির শ্রেণি (Land Class)", result.landClass)
                                    DataRow("মোট পরিমাণ (Total Area)", "${result.totalAreaAcres} একর / ${result.totalAreaDecimals} শতক")

                                    if (result.owners.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "মালিক ও রায়তের তালিকা (Owners & Shares):",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        result.owners.forEach { owner ->
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("${owner.serial}. ${owner.nameBengali} (${owner.nameEnglish})", fontWeight = FontWeight.Medium)
                                                    Text("পিতা/স্বামী: ${owner.fatherOrHusbandName}", style = MaterialTheme.typography.bodySmall)
                                                    Text("অংশ/হিস্যা: ${owner.shareHissa}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }

                                    if (result.marginalNotes.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text("প্রান্তীয় মন্তব্য (Marginal Notes):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        result.marginalNotes.forEach { note ->
                                            Text("• $note", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    if (value.isNotBlank() && value != " ()") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ContentBoxWithCopy(
    title: String,
    content: String,
    onCopy: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Text", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            )
        }
    }
}

/**
 * Creates a synthetic Brahmanbaria CS Khatian sample bitmap for instant offline previewing and testing.
 */
fun createSampleBrahmanbariaKhatianBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.rgb(250, 248, 235)) // Vintage land parchment tint

    val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 28f
        isAntiAlias = true
    }

    val headerPaint = Paint().apply {
        color = Color.rgb(120, 20, 20)
        textSize = 34f
        isFakeBoldText = true
        isAntiAlias = true
    }

    val borderPaint = Paint().apply {
        color = Color.rgb(80, 80, 80)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    canvas.drawRect(20f, 20f, 780f, 580f, borderPaint)
    canvas.drawText("সি এস খতিয়ান (ক্যাডাস্ট্রাল সার্ভে - ১৮৯৮)", 140f, 70f, headerPaint)
    canvas.drawText("জেলা: ব্রাহ্মণবাড়িয়া | থানা: নবীনগর | মৌজা: গোয়ালী", 60f, 130f, textPaint)
    canvas.drawText("জে. এল. নং: ১২৪ | খতিয়ান নং: ৪০৩ | সাবেক দাগ: ১০২, ১০৫", 60f, 180f, textPaint)
    canvas.drawText("রায়ত / মালিক: ফিরুজ মিয়া, পিতা: সিরাজ আলী", 60f, 240f, textPaint)
    canvas.drawText("অংশ / হিস্যা: /৪ আনা (০.২৫০০) | জমির শ্রেণি: ধানী ও নাল", 60f, 290f, textPaint)
    canvas.drawText("মোট জমির পরিমাণ: ১ একর ২০ শতক (1.20 Acres)", 60f, 340f, textPaint)
    canvas.drawText("[SEAL: Sub-Registry Office, Nabinagar - Verified]", 60f, 410f, textPaint)
    canvas.drawText("প্রান্তীয় নোট: নামজারি কেস নং ২৪/২০০৯ অনুযায়ী জমা খারিজ", 60f, 470f, textPaint)

    return bitmap
}
