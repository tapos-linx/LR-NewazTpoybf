package com.example.ui.screens.atlas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.atlas.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtlasTimelineScreen(
    onNavigateBack: () -> Unit,
    mouza: String = "দিলকুশা",
    dagNo: String = "৫০১"
) {
    val engine = remember { AtlasTimelineEngine() }
    val timelineNodes = remember { engine.buildHistoricalTimeline(mouza, dagNo) }
    val splitSample = remember {
        engine.detectDagSplit(
            parentDag = "৪১২",
            parentArea = 50.0,
            childDags = listOf("৮১৫/১" to 25.0, "৮১৫/২" to 25.0)
        )
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
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_atlas_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান (Back)"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("card_atlas_header"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoGraph,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "মৌজা: $mouza | মূল দাগ: $dagNo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "১৮৮৮ থেকে বর্তমান পর্যন্ত ব্রিটিশ ও বাংলাদেশ জরিপের ৪ স্তরের ধারাবাহিক স্বত্ব প্রবাহ ও দাগ বিভাজন পর্যালোচনা।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Dag Split Detection Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("card_dag_split_analysis"),
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
                            text = "সাবেক দাগ: ${splitSample.parentDag} (${splitSample.parentAreaDecimals} শতাংশ) ➔ হাল দাগ: ${splitSample.childDags.joinToString(", ")} (মোট ${splitSample.combinedChildAreaDecimals} শতাংশ)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text(splitSample.remarks, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (splitSample.isAreaBalanced) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (splitSample.isAreaBalanced) StatusVerified else StatusRecordGap
                                )
                            }
                        )
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
