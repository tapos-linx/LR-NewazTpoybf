package com.example.ui.screens.queue

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConfidenceLevel
import com.example.ui.components.FieldConfidenceChip
import com.example.ui.theme.StatusCorroborated
import com.example.ui.theme.StatusUncertain
import com.example.ui.theme.StatusVerified

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractionQueueDashboardScreen(
    viewModel: ExtractionQueueViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            for (uri in uris) {
                val fileName = uri.lastPathSegment ?: "LandRecord_${System.currentTimeMillis()}.jpg"
                viewModel.addDocumentToQueue(title = fileName, category = "CS", uri = uri)
            }
            Toast.makeText(context, "${uris.size}টি নতুন নথি কিউতে যুক্ত হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("extraction_queue_dashboard"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ধারাবাহিক নিষ্কাশন কিউ ও প্রোগ্রেস ড্যাশবোর্ড",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "মাল্টি-ফটো সিকোয়েন্সিয়াল OCR ও মেটাডেটা যাচাই ট্র্যাকিং",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_queue_back")
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
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { imagePickerLauncher.launch(arrayOf("image/*", "application/pdf")) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_add_photos"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("নথি যোগ করুন", fontSize = 13.sp)
                    }

                    if (uiState.isRunning) {
                        Button(
                            onClick = { viewModel.pauseExtraction() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_pause_extraction"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পজ করুন", fontSize = 13.sp)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startSequentialExtraction() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_start_extraction"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("শুরু করুন", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp)
        ) {
            // Dashboard Header Statistics Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(label = "মোট কিউ", value = "${uiState.totalQueued}", color = MaterialTheme.colorScheme.primary)
                    StatItem(label = "সম্পন্ন", value = "${uiState.completedCount}", color = StatusVerified)
                    StatItem(label = "ত্রুটি/রিভিউ", value = "${uiState.failedCount}", color = StatusUncertain)
                    IconButton(onClick = { viewModel.clearQueue() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "সাফ করুন", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active Progress Banner if Running
            AnimatedVisibility(visible = uiState.isRunning || uiState.currentIndex >= 0) {
                val currentItem = uiState.items.getOrNull(uiState.currentIndex)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "চলমান প্রসেসিং: ${currentItem?.title ?: "অপেক্ষা করছে..."}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { currentItem?.progress ?: 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentItem?.status?.bengaliLabel ?: "প্রস্তুত",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "নথির কিউ তালিকা (${uiState.items.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Queue Items List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(uiState.items) { index, item ->
                    QueueItemCard(
                        item = item,
                        isActive = index == uiState.currentIndex,
                        onRetry = { viewModel.retryItem(item.id) },
                        onDelete = { viewModel.removeDocument(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun QueueItemCard(
    item: ExtractionQueueItem,
    isActive: Boolean,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (item.status) {
        QueueItemStatus.COMPLETED -> StatusVerified
        QueueItemStatus.FAILED -> MaterialTheme.colorScheme.error
        QueueItemStatus.QUEUED -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (item.status) {
                                    QueueItemStatus.COMPLETED -> Icons.Default.CheckCircle
                                    QueueItemStatus.FAILED -> Icons.Default.Error
                                    QueueItemStatus.QUEUED -> Icons.Default.Schedule
                                    else -> Icons.Default.Autorenew
                                },
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.category} • ${item.status.bengaliLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = statusColor
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.status == QueueItemStatus.FAILED) {
                        IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "পুনরায় চেষ্টা", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "মুছে ফেলুন", tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            if (item.status != QueueItemStatus.QUEUED && item.status != QueueItemStatus.COMPLETED) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.15f)
                )
            }

            if (!item.extractedSummary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.extractedSummary!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (item.ocrConfidence != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val confLevel = when {
                                item.ocrConfidence >= 90f -> ConfidenceLevel.VERIFIED
                                item.ocrConfidence >= 70f -> ConfidenceLevel.PROBABLE
                                else -> ConfidenceLevel.UNCERTAIN
                            }
                            FieldConfidenceChip(confidence = confLevel)
                        }
                    }
                }
            }
        }
    }
}
