package com.example.ui.screens.atlas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.LandDocumentEntity
import com.example.domain.atlas.AtlasTimelineEngine
import com.example.domain.atlas.DagSplitAnalysis
import com.example.ui.theme.StatusProbable
import com.example.ui.theme.StatusRecordGap
import com.example.ui.theme.StatusVerified

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtlasTimelineScreen(
    onNavigateBack: () -> Unit,
    mouza: String = "",
    dagNo: String = "",
    records: List<LandDocumentEntity> = emptyList(),
    splitAnalysis: DagSplitAnalysis? = null
) {
    val engine = remember { AtlasTimelineEngine() }
    val timelineNodes = remember(records, mouza, dagNo) {
        if (records.isNotEmpty() && (mouza.isNotBlank() || dagNo.isNotBlank())) {
            engine.buildHistoricalTimeline(mouza, dagNo, records)
        } else {
            emptyList()
        }
    }

    Scaffold(
        modifier = Modifier.testTag("atlas_timeline_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ঐতিহাসিক প্রপার্টি অ্যাটলাস (মডিউল ১৩)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Historical Property Atlas (CS → SA → RS → BS)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "পিছনে যান")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overview Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_atlas_overview"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "জরিপকালীন ধারাবাহিক স্বত্ব ও দাগ বিভাজন",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (mouza.isNotBlank() && dagNo.isNotBlank()) "মৌজা: $mouza | মূল দাগ: $dagNo" else "মৌজা ও দাগের ধারাবাহিক রেকর্ড নিরীক্ষা",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Dag Split Detection Card (if available from actual evidence)
            if (splitAnalysis != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_dag_split_analysis"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountTree,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "দাগ বিভাজন বিশ্লেষণ (Dag Split Detection):",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "সাবেক দাগ: ${splitAnalysis.parentDag} (${splitAnalysis.parentAreaDecimals} শতাংশ) ➔ হাল দাগ: ${splitAnalysis.childDags.joinToString(", ")} (মোট ${splitAnalysis.combinedChildAreaDecimals} শতাংশ)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text(splitAnalysis.remarks, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (splitAnalysis.isAreaBalanced) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (splitAnalysis.isAreaBalanced) StatusVerified else StatusRecordGap
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Timeline Header
            item {
                Text(
                    text = "জরিপকালীন ধারাবাহিক স্বত্ব প্রবাহ (Chain of Title Timeline):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (timelineNodes.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "কোনো ঐতিহাসিক স্বত্ব প্রবাহ রেকর্ড পাওয়া যায়নি",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "নথি আপলোড বা স্ক্যান করে CS, SA, RS ও BS খতিয়ান যুক্ত করলে স্বত্ব প্রবাহ গঠিত হবে।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                // Timeline Nodes
                itemsIndexed(timelineNodes) { index, node ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Timeline step indicator
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(40.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (node.isContinuityVerified) StatusVerified else StatusProbable),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = node.era.code,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            if (index < timelineNodes.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(110.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Node details card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 12.dp)
                                .testTag("node_${node.era.code.lowercase()}"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = node.era.nameBn,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = node.era.period,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "খতিয়ান নং: ${node.khatianNo} | দাগ নং: ${node.dagNo} (${node.areaDecimals} শতাংশ)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "রেকর্ডীয় স্বত্বাধিকারী: ${node.owners.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${node.eventType.displayNameBn}: ${node.relationshipNotes}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(6.dp),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
