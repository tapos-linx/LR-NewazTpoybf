package com.example.ui.screens.drive

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.drive.GoogleDriveManager
import com.example.domain.saf.DiscoveredFile
import com.example.ui.components.LegalDisclaimerBanner
import com.example.ui.theme.StatusVerified

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleDriveInjectScreen(
    viewModel: GoogleDriveInjectViewModel,
    onNavigateBack: () -> Unit,
    onExtractionComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var inputDriveUrl by remember { mutableStateOf("") }
    val driveAccountUri = remember { GoogleDriveManager.buildDriveAccountRootUri(GoogleDriveManager.DEFAULT_DRIVE_ACCOUNT_EMAIL) }

    // SAF Activity Result Launchers targeting tapos.cpl@gmail.com
    val masterFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.injectMasterFolder(it) }
    }

    val singleFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.injectFolder(it) }
    }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { viewModel.injectZipArchive(it) }
    }

    val multiFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val uris = mutableListOf<Uri>()
        data?.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i)?.uri?.let { uris.add(it) }
            }
        } ?: data?.data?.let { uris.add(it) }
        if (uris.isNotEmpty()) {
            viewModel.injectFiles(uris)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "গুগল ড্রাইভ সরাসরি ইনজেকশন",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "মাস্টার ফোল্ডার, সাব-ফোল্ডার, জিপ ও ফাইলসমূহ",
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
                    if (uiState.scanSummary != null) {
                        IconButton(onClick = { viewModel.reset() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "রিসেট",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F766E), // Deep Emerald / Teal
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            if (uiState.scanSummary != null && uiState.scanSummary!!.supportedFiles.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (uiState.isProcessingBatch) {
                            Text(
                                text = "নিষ্কাশন চলছে (${uiState.currentProcessingIndex}/${uiState.totalToProcess}): ${uiState.processingFileName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val progress = if (uiState.totalToProcess > 0) {
                                uiState.currentProcessingIndex.toFloat() / uiState.totalToProcess
                            } else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = StatusVerified
                            )
                        } else {
                            Button(
                                onClick = {
                                    viewModel.startBatchExtraction { total ->
                                        Toast.makeText(
                                            context,
                                            "গুগল ড্রাইভ থেকে $total টি নথি সফলভাবে নিষ্কাশন করা হয়েছে",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onExtractionComplete()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_start_drive_extraction"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${uiState.scanSummary!!.supportedFiles.size}টি নথির নিষ্কাশন ও ডেটাবেস সংরক্ষণ শুরু করুন",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                LegalDisclaimerBanner()
            }

            // Google Drive Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF0F766E), Color(0xFF047857))
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "গুগল ড্রাইভ ডিরেক্ট ইনজেকশন হাব",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "সক্রিয় ড্রাইভ অ্যাকাউন্ট: ${GoogleDriveManager.DEFAULT_DRIVE_ACCOUNT_EMAIL}",
                                        color = Color.White.copy(alpha = 0.95f),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "আপনার গুগল ড্রাইভ (${GoogleDriveManager.DEFAULT_DRIVE_ACCOUNT_EMAIL})-এ থাকা মাস্টার ফোল্ডার (সকল সাব-ফোল্ডারসহ), জিপ আর্কাইভ, একক ফোল্ডার বা একাধিক ফাইল সরাসরি নির্বাচন করে অফলাইন ডেটাবেসে আমদানি ও নিষ্কাশন করুন।",
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // 4 Direct Injection Action Cards
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "গুগল ড্রাইভ আমদানি মাধ্যম নির্বাচন করুন (${GoogleDriveManager.DEFAULT_DRIVE_ACCOUNT_EMAIL}):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // 1. Master folder with many subfolders
                    DriveActionRow(
                        title = "📁 মাস্টার ফোল্ডার (Master Folder with subfolders)",
                        subtitle = "গুগল ড্রাইভের মূল ফোল্ডার যাতে CS, SA, RS, দলিল ও নামজারির অসংখ্য সাব-ফোল্ডার রয়েছে",
                        buttonText = "মাস্টার ফোল্ডার ইনজেক্ট",
                        icon = Icons.Default.Folder,
                        containerColor = Color(0xFF0F766E),
                        testTag = "btn_inject_drive_master_folder",
                        onClick = { masterFolderLauncher.launch(driveAccountUri) }
                    )

                    // 2. A zip folder
                    DriveActionRow(
                        title = "🗜️ জিপ ফোল্ডার / আর্কাইভ (.zip Folder)",
                        subtitle = "গুগল ড্রাইভ থেকে .zip ফাইল নির্বাচন করুন; স্বয়ংক্রিয়ভাবে ভেতরের সকল সাব-ফোল্ডার আনপ্যাক ও সাজানো হবে",
                        buttonText = "ড্রাইভ জিপ নির্বাচন",
                        icon = Icons.Default.FolderZip,
                        containerColor = Color(0xFFD97706),
                        testTag = "btn_inject_drive_zip",
                        onClick = { zipPickerLauncher.launch(GoogleDriveManager.createDriveZipPickerIntent()) }
                    )

                    // 3. Folders
                    DriveActionRow(
                        title = "📂 নির্দিষ্ট ফোল্ডার (Drive Folders)",
                        subtitle = "গুগল ড্রাইভের যেকোনো একটি নির্দিষ্ট ফোল্ডারের সকল খতিয়ান বা দলিল আমদানি করুন",
                        buttonText = "ড্রাইভ ফোল্ডার ব্রাউজ",
                        icon = Icons.Default.FolderOpen,
                        containerColor = Color(0xFF2563EB),
                        testTag = "btn_inject_drive_folder",
                        onClick = { singleFolderLauncher.launch(driveAccountUri) }
                    )

                    // 4. Files
                    DriveActionRow(
                        title = "📄 একাধিক ফাইলসমূহ (Drive Multi-Files)",
                        subtitle = "গুগল ড্রাইভ থেকে একযোগে বহুসংখ্যক PDF বা দলিল ছবি সরাসরি নির্বাচন করুন",
                        buttonText = "ড্রাইভ ফাইলসমূহ বাছুন",
                        icon = Icons.Default.Description,
                        containerColor = Color(0xFF7C3AED),
                        testTag = "btn_inject_drive_files",
                        onClick = { multiFilesLauncher.launch(GoogleDriveManager.createDriveMultiFilesPickerIntent()) }
                    )
                }
            }

            // 5. Shared Google Drive URL / File ID Direct Download Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = Color(0xFF0F766E),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "গুগল ড্রাইভ শেয়ার্ড লিঙ্ক / আইডি ইনজেকশন",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "আপনার গুগল ড্রাইভের শেয়ার্ড লিঙ্ক বা ফাইল/ফোল্ডার আইডি পেস্ট করে সরাসরি অ্যাপে ডাউনলোড ও ইনজেক্ট করুন:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = inputDriveUrl,
                            onValueChange = { inputDriveUrl = it },
                            placeholder = { Text("https://drive.google.com/file/d/... অথবা ফাইল আইডি", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_drive_url")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (inputDriveUrl.isNotBlank()) {
                                    viewModel.injectFromDriveUrl(inputDriveUrl)
                                }
                            },
                            enabled = !uiState.isDownloading && inputDriveUrl.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_drive_link_download"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("লিঙ্ক থেকে ডাউনলোড ও ইনজেক্ট করুন")
                        }
                    }
                }
            }

            // Progress / Status Messages
            if (uiState.isDownloading || uiState.isScanning) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color(0xFF0F766E)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = if (uiState.isDownloading) "গুগল ড্রাইভ ডাউনলোড চলছে..." else "ফাইল ও সাব-ফোল্ডার স্ক্যানিং...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = uiState.downloadProgressText.ifBlank { "সাব-ফোল্ডারসমূহ বিশ্লেষণ ও নথির শ্রেণিবিন্যাস করা হচ্ছে..." },
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Error Message
            uiState.error?.let { err ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearError() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "রিসেট", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Discovered Files Summary & Hierarchy
            uiState.scanSummary?.let { summary ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "আহরিত নথির বিবরণ (${summary.supportedFiles.size} টি নথি)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "উৎস: ${uiState.sourceLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Subfolder / Category Chips Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                summary.categoriesFound.forEach { (category, count) ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = "$category: $count টি",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Discovered Files List
                items(summary.supportedFiles.take(50)) { file ->
                    DiscoveredFileCard(file = file)
                }

                if (summary.supportedFiles.size > 50) {
                    item {
                        Text(
                            text = "...এবং আরও ${summary.supportedFiles.size - 50}টি নথি প্রস্তুত",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveActionRow(
    title: String,
    subtitle: String,
    buttonText: String,
    icon: ImageVector,
    containerColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = containerColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = containerColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag),
                colors = ButtonDefaults.buttonColors(containerColor = containerColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = buttonText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DiscoveredFileCard(file: DiscoveredFile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when (file.format.lowercase()) {
                    "pdf" -> Color(0xFFDC2626).copy(alpha = 0.15f)
                    "zip" -> Color(0xFFD97706).copy(alpha = 0.15f)
                    else -> Color(0xFF0F766E).copy(alpha = 0.15f)
                },
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = file.format.uppercase().take(3),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = when (file.format.lowercase()) {
                            "pdf" -> Color(0xFFDC2626)
                            "zip" -> Color(0xFFD97706)
                            else -> Color(0xFF0F766E)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "ফোল্ডার: ${file.parentFolder} | আকার: ${file.sizeBytes / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = file.classifiedType.bengaliName,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
