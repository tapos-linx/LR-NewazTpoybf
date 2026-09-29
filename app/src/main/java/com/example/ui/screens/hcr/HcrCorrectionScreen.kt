package com.example.ui.screens.hcr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.hcr.HcrContextCategory
import com.example.domain.hcr.HcrToken
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HcrCorrectionScreen(
    viewModel: HcrCorrectionViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.showSuccessSnackbar) {
        if (uiState.showSuccessSnackbar) {
            snackbarHostState.showSnackbar(uiState.snackbarMessage)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        modifier = Modifier.testTag("hcr_correction_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "বাংলা হস্তলিপি স্বীকৃতি (HCR)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Bangla Handwriting & Marginalia Audit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_hcr_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান (Back)"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveCorrection() },
                        modifier = Modifier.testTag("btn_hcr_save_top")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "সংরক্ষণ করুন (Save)"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category selector tabs
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "হস্তলিপির ধরন নির্বাচন করুন (Select Context):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(HcrContextCategory.values()) { category ->
                        FilterChip(
                            selected = uiState.selectedCategory == category,
                            onClick = { viewModel.selectCategory(category) },
                            label = { Text(category.displayNameBn, fontSize = 12.sp) },
                            modifier = Modifier.testTag("chip_hcr_${category.name.lowercase()}")
                        )
                    }
                }
            }

            // Original Handwriting Preview (Simulated / Historical parchment crop)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_original_crop"),
                    colors = CardDefaults.cardColors(
                        containerColor = ParchmentPaper.copy(alpha = 0.85f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = SepiaBrown,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "মূল হস্তলিপি ক্রপ (Original Crop):",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = SepiaBrown
                                )
                            }
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = "${(uiState.currentResult.confidence * 100).toInt()}% নিশ্চিত",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (uiState.currentResult.requiresManualReview)
                                            Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (uiState.currentResult.requiresManualReview)
                                            StatusProbable else StatusVerified
                                    )
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Parchment cursive handwriting representation
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEFE8D8))
                                .border(1.dp, Color(0xFFD4C8B0), RoundedCornerShape(8.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = uiState.currentResult.originalText,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Serif
                                ),
                                color = Color(0xFF2C2416)
                            )
                        }
                    }
                }
            }

            // Recognized Tokens with Confidence Indicators
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_token_confidence"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "স্বীকৃত শব্দসমূহ ও নির্ভরযোগ্যতা (Tokens):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Token confidence chips
                        FlowRowLayout(tokens = uiState.currentResult.tokens)
                    }
                }
            }

            // Manual Correction Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_manual_correction"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ম্যানুয়াল সংশোধন (Manual Verification):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ফরেনসিক নীতি: কোনো তথ্য বাদ দেওয়া যাবে না। অপাঠ্য অংশের জন্য নিচের ট্যাগ ব্যবহার করুন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick forensic tag insertion buttons
                        Text(
                            text = "দ্রুত ট্যাগ সংযোজন (Forensic Tags):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SuggestionChip(
                                onClick = { viewModel.insertForensicTag("[Unclear: কালির দাগ / Faded Ink]") },
                                label = { Text("কালির দাগ", fontSize = 11.sp) },
                                modifier = Modifier.testTag("tag_faded_ink")
                            )
                            SuggestionChip(
                                onClick = { viewModel.insertForensicTag("[Unclear: ছেঁড়া কাগজ / Torn]") },
                                label = { Text("ছেঁড়া কাগজ", fontSize = 11.sp) },
                                modifier = Modifier.testTag("tag_torn_paper")
                            )
                            SuggestionChip(
                                onClick = { viewModel.insertForensicTag("[Unclear: অস্পষ্ট স্বাক্ষর]") },
                                label = { Text("স্বাক্ষর অস্পষ্ট", fontSize = 11.sp) },
                                modifier = Modifier.testTag("tag_unclear_sig")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Text Field for correction
                        OutlinedTextField(
                            value = uiState.editingText,
                            onValueChange = { viewModel.updateEditingText(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_hcr_corrected_text"),
                            label = { Text("সংশোধিত পাঠ্য (Corrected Transcription)") },
                            minLines = 3,
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = uiState.editorNotes,
                            onValueChange = { viewModel.updateEditorNotes(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_hcr_notes"),
                            label = { Text("গবেষকের মন্তব্য (Audit Notes / Reference)") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.saveCorrection() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_hcr_save"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("সংশোধন নিশ্চিত করুন (Save & Audit)")
                        }
                    }
                }
            }

            // Marginal Notes Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_marginal_notes"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "নথির মার্জিনাল নোট ও পার্শ্বীয় মন্তব্য:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        uiState.marginalNotes.forEach { note ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = note.marginLocation,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = note.text,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("${(note.confidence * 100).toInt()}%") }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FlowRowLayout(tokens: List<HcrToken>) {
    // Render token chips in rows
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tokens.chunked(3).forEach { rowTokens ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowTokens.forEach { token ->
                    val chipColor = when {
                        token.confidence >= 0.85f -> StatusVerified
                        token.confidence >= 0.70f -> StatusProbable
                        else -> StatusContradictory
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = chipColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, chipColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = token.text,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${(token.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = chipColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
