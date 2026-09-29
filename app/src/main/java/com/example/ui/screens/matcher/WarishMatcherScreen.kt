package com.example.ui.screens.matcher

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BengaliNumberUtils
import com.example.domain.gis.CadastralParcel
import com.example.domain.gis.CadastralSheetData
import com.example.domain.matcher.EvidenceTier
import com.example.domain.matcher.HeirRecord
import com.example.domain.matcher.MatchedPlotResult
import com.example.domain.matcher.WarishMatchReport
import com.example.ui.components.LegalDisclaimerBanner
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarishMatcherScreen(
    viewModel: WarishMatcherViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAddHeirDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("warish_matcher_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ওয়ারিশনামা ও মৌজা নকশা মেলানো",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "LR-NewazTpoybf • মডিউল ০৩ ইন্টেলিজেন্স",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_warish_matcher_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.resetToSample() },
                        modifier = Modifier.testTag("btn_reset_warish_sample")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "রিসেট করুন",
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Three Tabs: Matcher, Cadastral Map Highlights, Audit Report
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().testTag("warish_tab_row")
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("ওয়ারিশ ও খতিয়ান", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_warish_matcher")
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("মৌজা নকশা", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_cadastral_map")
                )
                Tab(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    text = { Text("ফরেনসিক অডিট", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_forensic_audit")
                )
            }

            // Tab Content
            when (uiState.selectedTab) {
                0 -> WarishMatcherTabContent(
                    uiState = uiState,
                    onAddHeirClick = { showAddHeirDialog = true },
                    onRemoveHeir = { viewModel.removeHeir(it) },
                    onReRunMatch = { viewModel.runMatching() }
                )
                1 -> CadastralMapTabContent(
                    sheetData = uiState.cadastralSheet,
                    selectedParcel = uiState.selectedParcel,
                    onParcelClick = { viewModel.selectParcel(it) },
                    report = uiState.matchReport
                )
                2 -> ForensicAuditTabContent(
                    report = uiState.matchReport,
                    onShareReport = {
                        val shareText = buildAuditSummaryText(uiState.matchReport)
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "ফরেনসিক অডিট রিপোর্ট শেয়ার করুন"))
                    }
                )
            }
        }
    }

    if (showAddHeirDialog) {
        AddHeirDialog(
            onDismiss = { showAddHeirDialog = false },
            onAddHeir = { name, relation, share, label ->
                viewModel.addHeir(name, relation, share, label)
                showAddHeirDialog = false
            }
        )
    }
}

@Composable
fun WarishMatcherTabContent(
    uiState: WarishMatcherUiState,
    onAddHeirClick: () -> Unit,
    onRemoveHeir: (String) -> Unit,
    onReRunMatch: () -> Unit
) {
    val report = uiState.matchReport
    val warish = uiState.currentWarish

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("warish_matcher_tab_content"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Legal disclaimer reminder
        item {
            LegalDisclaimerBanner()
        }

        // Warishnama Header Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("warish_certificate_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ওয়ারিশনামা সনদ তথ্য",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = warish.certificateNo,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "মরহুম / মৃত: ${warish.deceasedName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "পিতা/স্বামী: ${warish.fatherOrHusbandName} • মৃত্যু: ${warish.dateOfDeath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "ইউনিয়ন/ওয়ার্ড: ${warish.unionOrWard}, ${warish.upazila}, ${warish.district}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "চিহ্নিত দাগ নং: ${warish.targetDagNumbers.joinToString(", ")} (মৌজা: ${warish.targetMouza})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = onReRunMatch, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "পুনরায় মেলান", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Hissa Mathematical Balance & Heir List
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hissa_balance_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (warish.isHissaBalanced) StatusVerified.copy(alpha = 0.08f)
                    else StatusContradictory.copy(alpha = 0.08f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (warish.isHissaBalanced) StatusVerified.copy(alpha = 0.4f)
                    else StatusContradictory.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (warish.isHissaBalanced) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (warish.isHissaBalanced) StatusVerified else StatusContradictory,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "হিস্যা সমতা যাচাই (১৬ আনা / ১.০০০)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val sumFormatted = "%.3f".format(warish.totalShareSum)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (warish.isHissaBalanced) StatusVerified else StatusContradictory
                        ) {
                            Text(
                                text = if (warish.isHissaBalanced) "সমতা সঠিক (১.০০০)" else "গরমিল: ${BengaliNumberUtils.toBengaliDigits(sumFormatted)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ঘোষিত ওয়ারিশ তালিকা (${BengaliNumberUtils.toBengaliDigits(warish.heirs.size.toLong())} জন):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    warish.heirs.forEach { heir ->
                        HeirRowItem(heir = heir, onRemove = { onRemoveHeir(heir.id) })
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onAddHeirClick,
                        modifier = Modifier.fillMaxWidth().testTag("btn_add_heir"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ওয়ারিশ যুক্ত করুন", fontSize = 12.sp)
                    }
                }
            }
        }

        // Overall Evidence Tier Summary Card
        if (report != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("overall_evidence_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = report.overallEvidenceTier.badgeColor.copy(alpha = 0.12f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, report.overallEvidenceTier.badgeColor.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ফরেনসিক প্রমাণের মাত্রা (Evidence Tier)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = report.overallEvidenceTier.badgeColor
                            ) {
                                Text(
                                    text = report.overallEvidenceTier.bengaliLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = report.overallEvidenceTier.description,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Confidence score progress
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "কনফিডেন্স স্কোর:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "${BengaliNumberUtils.toBengaliDigits("%.1f".format(report.overallConfidenceScore))}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        LinearProgressIndicator(
                            progress = { (report.overallConfidenceScore / 100.0f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = report.overallEvidenceTier.badgeColor
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Chain completeness chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            report.chainCompleteness.forEach { (survey, present) ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (present) StatusVerified.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = survey, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (present) "বিদ্যমান" else "অনুপস্থিত",
                                            fontSize = 9.sp,
                                            color = if (present) StatusVerified else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Matched Plots Header
            item {
                Text(
                    text = "মিলকৃত দাগ ও খতিয়ান শৃঙ্খল (${BengaliNumberUtils.toBengaliDigits(report.matchedPlots.size.toLong())} টি দাগ)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Matched Plot Items
            items(report.matchedPlots) { plot ->
                MatchedPlotCard(plot = plot)
            }
        }
    }
}

@Composable
fun HeirRowItem(heir: HeirRecord, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = heir.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "সম্পর্ক: ${heir.relationship} • অংশ: ${heir.shareFractionLabel} (${BengaliNumberUtils.toBengaliDigits("%.3f".format(heir.shareHissa))})",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Close, contentDescription = "মুছুন", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun MatchedPlotCard(plot: MatchedPlotResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("matched_plot_${plot.dagNo}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (plot.evidenceTier == EvidenceTier.CONTRADICTORY) StatusContradictory
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = plot.dagNo,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "দাগ নং: ${plot.dagNo} • মৌজা: ${plot.mouza}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "মোট জমি: ${BengaliNumberUtils.toBengaliDigits("%.2f".format(plot.totalAreaDecimals))} শতাংশ (${plot.landClass})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = plot.evidenceTier.badgeColor
                ) {
                    Text(
                        text = plot.evidenceTier.code,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Survey chain traces
            Text(
                text = "শনাক্ত জরিপ শৃঙ্খল: ${plot.surveyedChains.joinToString(" ➔ ")}",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            // Discrepancy warnings
            if (plot.discrepancies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                plot.discrepancies.forEach { disc ->
                    Text(
                        text = "• $disc",
                        fontSize = 10.sp,
                        color = StatusContradictory
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(6.dp))

            // Allocated heir shares
            Text(
                text = "ওয়ারিশদের প্রাপ্ত জমি বণ্টন:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            plot.heirShares.forEach { alloc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${alloc.heir.name} (${alloc.heir.relationship})",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${BengaliNumberUtils.toBengaliDigits("%.2f".format(alloc.allocatedDecimals))} শতাংশ (${alloc.percentageOfPlot}%)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Tab 2: Cadastral Map Highlights (মৌজা নকশা ইন্টেলিজেন্স).
 * Strictly enforces: "Rule: Never highlight all parcels. Highlights ONLY target Dags matched from the certificate."
 */
@Composable
fun CadastralMapTabContent(
    sheetData: CadastralSheetData?,
    selectedParcel: CadastralParcel?,
    onParcelClick: (CadastralParcel) -> Unit,
    report: WarishMatchReport?
) {
    if (sheetData == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("cadastral_map_tab_content")
    ) {
        // Strict Forensic Rule Banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ফরেনসিক নিয়ম: শুধুমাত্র ওয়ারিশ সনদের মিলকৃত দাগ হাইলাইট করা হয়েছে।",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Map Canvas Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(ParchmentPaper)
                .clip(RoundedCornerShape(0.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.8f, 4.0f)
                        offset += pan
                    }
                }
                .testTag("cadastral_map_canvas_box")
        ) {
            // Interactive Map Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(sheetData) {
                        detectTapGestures { tapOffset ->
                            // Hit test normalized parcels
                            val normX = (tapOffset.x - offset.x) / (size.width * scale)
                            val normY = (tapOffset.y - offset.y) / (size.height * scale)
                            val tapped = sheetData.parcels.firstOrNull {
                                normX in it.bounds.left..it.bounds.right &&
                                        normY in it.bounds.top..it.bounds.bottom
                            }
                            if (tapped != null) {
                                onParcelClick(tapped)
                            }
                        }
                    }
            ) {
                val canvasW = size.width
                val canvasH = size.height

                // Draw Sheet Border Frame
                drawRect(
                    color = Color(0xFF333333),
                    topLeft = Offset(4f, 4f),
                    size = Size(canvasW - 8f, canvasH - 8f),
                    style = Stroke(width = 3f)
                )

                // Draw Road Network
                for (road in sheetData.roadLines) {
                    val path = Path()
                    road.pathPoints.forEachIndexed { idx, pt ->
                        val screenX = (pt.x * canvasW * scale) + offset.x
                        val screenY = (pt.y * canvasH * scale) + offset.y
                        if (idx == 0) path.moveTo(screenX, screenY) else path.lineTo(screenX, screenY)
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFFC2410C),
                        style = Stroke(width = 6f * scale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                    )
                }

                // Draw Water Bodies (খাল / পুকুর)
                for (wb in sheetData.waterBodies) {
                    val left = (wb.bounds.left * canvasW * scale) + offset.x
                    val top = (wb.bounds.top * canvasH * scale) + offset.y
                    val width = wb.bounds.width * canvasW * scale
                    val height = wb.bounds.height * canvasH * scale
                    drawRect(
                        color = Color(0xFF38BDF8).copy(alpha = 0.4f),
                        topLeft = Offset(left, top),
                        size = Size(width, height)
                    )
                    drawRect(
                        color = Color(0xFF0284C7),
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 2f)
                    )
                }

                // Draw Parcels
                // Critical Rule: Never highlight all parcels.
                // Highlights ONLY target Dags matched from the certificate!
                for (parcel in sheetData.parcels) {
                    val left = (parcel.bounds.left * canvasW * scale) + offset.x
                    val top = (parcel.bounds.top * canvasH * scale) + offset.y
                    val width = parcel.bounds.width * canvasW * scale
                    val height = parcel.bounds.height * canvasH * scale

                    val isSelected = selectedParcel?.dagNo == parcel.dagNo

                    if (parcel.isTargetMatched) {
                        // Prominent Forensic Highlight (Emerald with Gold/Amber glow)
                        drawRect(
                            color = StatusVerified.copy(alpha = 0.45f),
                            topLeft = Offset(left, top),
                            size = Size(width, height)
                        )
                        drawRect(
                            color = StatusVerified,
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            style = Stroke(width = if (isSelected) 6f else 4f)
                        )
                    } else {
                        // Standard Cadastral survey parcel (unaltered)
                        drawRect(
                            color = Color(0xFF222222),
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            style = Stroke(width = 1.5f)
                        )
                    }

                    if (isSelected) {
                        drawRect(
                            color = SecondaryGold,
                            topLeft = Offset(left - 2f, top - 2f),
                            size = Size(width + 4f, height + 4f),
                            style = Stroke(width = 3f)
                        )
                    }
                }
            }

            // Map Overlay: North Arrow & Scale Bar (Top Left)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.TopStart)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "উত্তর",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "উত্তর (North)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "স্কেল: ${sheetData.scaleRatio}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Map Legend (Top Right)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.88f),
                modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.TopEnd)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(text = "মৌজা: ${sheetData.mouzaName} (সিট ${sheetData.sheetNo})", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(StatusVerified))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "ওয়ারিশ মিলকৃত দাগ", fontSize = 9.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).border(1.dp, Color.Black))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "সাধারণ জরিপ দাগ", fontSize = 9.sp)
                    }
                }
            }
        }

        // Selected Parcel Inspector Bottom Panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .testTag("parcel_inspector_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (selectedParcel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "দাগ নং: ${selectedParcel.dagNo} (${selectedParcel.landClass})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "আয়তন: ${BengaliNumberUtils.toBengaliDigits("%.2f".format(selectedParcel.areaDecimals))} শতাংশ • জরিপ: ${sheetData.surveyType}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        if (selectedParcel.isTargetMatched) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusVerified
                            ) {
                                Text(
                                    text = "ওয়ারিশের লক্ষ্য দাগ",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "অসংযুক্ত সাধারণ দাগ",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Linked heirs info if matched
                    if (selectedParcel.isTargetMatched && report != null) {
                        val plotResult = report.matchedPlots.firstOrNull { it.dagNo == selectedParcel.dagNo }
                        if (plotResult != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "স্বত্বাধিকারী ওয়ারিশগণের হিস্যা বণ্টন:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            plotResult.heirShares.take(3).forEach { alloc ->
                                Text(
                                    text = "• ${alloc.heir.name}: ${BengaliNumberUtils.toBengaliDigits("%.2f".format(alloc.allocatedDecimals))} শতাংশ (${alloc.percentageOfPlot}%)",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "ম্যাপের যেকোনো দাগের উপর ট্যাপ করে বিশদ তথ্য দেখুন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

/**
 * Tab 3: Complete Forensic Audit Report.
 */
@Composable
fun ForensicAuditTabContent(
    report: WarishMatchReport?,
    onShareReport: () -> Unit
) {
    if (report == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("কোনো অডিট ডাটা নেই")
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("forensic_audit_tab_content"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            LegalDisclaimerBanner()
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("audit_summary_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ফরেনসিক ওয়ারিশ ও জরিপ অডিট সার্টিফিকেট",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "নথির শিরোনাম: ${report.warishCertificate.certificateNo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "চূড়ান্ত প্রমাণ স্তর:", fontSize = 12.sp)
                        Text(
                            text = report.overallEvidenceTier.bengaliLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = report.overallEvidenceTier.badgeColor
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "গাণিতিক হিস্যা যোগফল:", fontSize = 12.sp)
                        Text(
                            text = BengaliNumberUtils.toBengaliDigits("%.3f".format(report.totalDeclaredShare)) + if (report.isHissaMathematicallyBalanced) " (সঠিক)" else " (গরমিল)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (report.isHissaMathematicallyBalanced) StatusVerified else StatusContradictory
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "মোট হস্তান্তরিত জমি:", fontSize = 12.sp)
                        Text(
                            text = "${BengaliNumberUtils.toBengaliDigits("%.2f".format(report.totalInheritedDecimals))} শতাংশ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onShareReport,
                        modifier = Modifier.fillMaxWidth().testTag("btn_share_audit_report"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("সম্পূর্ণ অডিট রিপোর্ট রপ্তানি ও শেয়ার করুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Global Discrepancies
        if (report.globalDiscrepancies.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = StatusContradictory.copy(alpha = 0.1f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "শনাক্তকৃত অসঙ্গতি ও গরমিল:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusContradictory
                        )
                        report.globalDiscrepancies.forEach { disc ->
                            Text(text = "• $disc", fontSize = 11.sp, color = StatusContradictory)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddHeirDialog(
    onDismiss: () -> Unit,
    onAddHeir: (String, String, Double, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("পুত্র") }
    var shareStr by remember { mutableStateOf("0.250") }
    var fractionLabel by remember { mutableStateOf("৪ আনা") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন ওয়ারিশ যুক্ত করুন", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ওয়ারিশের নাম") },
                    modifier = Modifier.fillMaxWidth().testTag("input_heir_name")
                )
                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text("সম্পর্ক (যেমন: পুত্র, কন্যা, স্ত্রী)") },
                    modifier = Modifier.fillMaxWidth().testTag("input_heir_relationship")
                )
                OutlinedTextField(
                    value = shareStr,
                    onValueChange = { shareStr = it },
                    label = { Text("হিস্যা দশমিক (যেমন: 0.125, 0.250)") },
                    modifier = Modifier.fillMaxWidth().testTag("input_heir_share")
                )
                OutlinedTextField(
                    value = fractionLabel,
                    onValueChange = { fractionLabel = it },
                    label = { Text("অংশের বিবরণ (যেমন: ২ আনা, ৪ আনা)") },
                    modifier = Modifier.fillMaxWidth().testTag("input_heir_fraction")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val share = shareStr.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && share > 0.0) {
                        onAddHeir(name, relationship, share, fractionLabel)
                    }
                },
                modifier = Modifier.testTag("btn_confirm_add_heir")
            ) {
                Text("যুক্ত করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

fun buildAuditSummaryText(report: WarishMatchReport?): String {
    if (report == null) return ""
    val warish = report.warishCertificate
    val b = StringBuilder()
    b.appendLine("=== বাংলাদেশ ভূমি রেকর্ড ফরেনসিক অডিট রিপোর্ট ===")
    b.appendLine("সনদ নং: ${warish.certificateNo}")
    b.appendLine("মরহুম / মৃত: ${warish.deceasedName} (পিতা/স্বামী: ${warish.fatherOrHusbandName})")
    b.appendLine("এলাকা: ${warish.unionOrWard}, ${warish.upazila}, ${warish.district}")
    b.appendLine("চূড়ান্ত প্রমাণ স্তর (Evidence Tier): ${report.overallEvidenceTier.bengaliLabel}")
    b.appendLine("কনফিডেন্স স্কোর: ${report.overallConfidenceScore}%")
    b.appendLine("হিস্যা সমতা: ${if (report.isHissaMathematicallyBalanced) "সঠিক (১.০০০)" else "গরমিল"}")
    b.appendLine("মোট হস্তান্তরিত জমি: ${report.totalInheritedDecimals} শতাংশ")
    b.appendLine("\nওয়ারিশগণের তালিকা:")
    warish.heirs.forEach { heir ->
        b.appendLine(" - ${heir.name} (${heir.relationship}): অংশ ${heir.shareFractionLabel} (দশমিক ${heir.shareHissa})")
    }
    b.appendLine("\nদাগভিত্তিক বরাদ্দ:")
    report.matchedPlots.forEach { plot ->
        b.appendLine(" - দাগ নং: ${plot.dagNo} (মৌজা: ${plot.mouza}) | মোট: ${plot.totalAreaDecimals} শতাংশ | টিয়ার: ${plot.evidenceTier.code}")
    }
    b.appendLine("\n[আইনগত সতর্কতা: এই ফলাফল প্রযুক্তিগতভাবে আনুমানিক এবং কোনো আইনি মতামত বা স্বত্ব নির্ধারণ করে না।]")
    return b.toString()
}
