package com.example.ui.screens.importcenter

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.drive.GoogleDriveManager

/**
 * Represents a recent selection or submission captured through the Import Center.
 */
data class ImportCenterSelection(
    val sourceLabel: String,
    val reference: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * Import Center Composable providing:
 * 1. Local file selection via [ActivityResultContracts.OpenDocument].
 * 2. Local directory tree selection via [ActivityResultContracts.OpenDocumentTree].
 * 3. Google Drive entry point pre-configured for [GoogleDriveManager.DEFAULT_DRIVE_ACCOUNT_EMAIL].
 * 4. Manual entry field for computer uploads (pairing code, companion URL, or batch reference).
 */
@Composable
fun ImportCenter(
    modifier: Modifier = Modifier,
    documentImportViewModel: DocumentImportViewModel = viewModel(
        factory = DocumentImportViewModel.Factory(LocalContext.current)
    ),
    processDirectlyInViewModel: Boolean = false,
    defaultDriveAccount: String = GoogleDriveManager.DEFAULT_DRIVE_ACCOUNT_EMAIL,
    onDocumentSelected: (Uri) -> Unit = {},
    onFolderSelected: (Uri) -> Unit = {},
    onGoogleDriveRequested: (String) -> Unit = {},
    onComputerUploadSubmitted: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val importUiState by documentImportViewModel.uiState.collectAsStateWithLifecycle()
    var computerUploadEntry by remember { mutableStateOf("") }
    var statusBannerText by remember { mutableStateOf<String?>(null) }
    val recentSelections = remember { mutableStateListOf<ImportCenterSelection>() }

    // 1. ActivityResultContracts.OpenDocument from DocumentImportViewModel for selecting a local document via SAF
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = documentImportViewModel.openDocumentContract
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Provider may not support persistable permissions; transient grant remains valid
            }
            recentSelections.add(
                0,
                ImportCenterSelection(
                    sourceLabel = "Local File (SAF)",
                    reference = uri.toString()
                )
            )
            statusBannerText = "লোকাল ফাইল নির্বাচিত হয়েছে: ${uri.lastPathSegment ?: uri}"
            if (processDirectlyInViewModel) {
                documentImportViewModel.onOpenDocumentResult(uri)
            }
            onDocumentSelected(uri)
        }
    }

    // 2. ActivityResultContracts.OpenDocumentTree from DocumentImportViewModel for selecting a local folder tree via SAF
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = documentImportViewModel.openDocumentTreeContract
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Provider may not support persistable permissions
            }
            recentSelections.add(
                0,
                ImportCenterSelection(
                    sourceLabel = "Local Folder (SAF Tree)",
                    reference = treeUri.toString()
                )
            )
            statusBannerText = "লোকাল ফোল্ডার নির্বাচিত হয়েছে: ${treeUri.lastPathSegment ?: treeUri}"
            if (processDirectlyInViewModel) {
                documentImportViewModel.onOpenDocumentTreeResult(treeUri)
            }
            onFolderSelected(treeUri)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("import_center_root"),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with accent gradient badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = "Import Center Icon",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Import Center (ইমপোর্ট সেন্টার)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "লোকাল ফাইল, ফোল্ডার, গুগল ড্রাইভ ও কম্পিউটার আপলোড সংযোগ কেন্দ্র",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Section 1: Local Storage SAF Actions (OpenDocument & OpenDocumentTree)
            Text(
                text = "১. ডিভাইস মেমোরি (Android Storage Access Framework)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        openDocumentLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "image/*",
                                "application/zip",
                                "text/csv",
                                "text/plain",
                                "application/json",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_import_center_open_document"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Select Local File",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "লোকাল ফাইল",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = {
                        openDocumentTreeLauncher.launch(null)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_import_center_open_document_tree"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Select Local Folder",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "লোকাল ফোল্ডার",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Section 2: Google Drive Entry Point
            Text(
                text = "২. ক্লাউড স্টোরেজ (Google Drive ইন্টিগ্রেশন)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F766E)
            )

            OutlinedButton(
                onClick = {
                    recentSelections.add(
                        0,
                        ImportCenterSelection(
                            sourceLabel = "Google Drive ($defaultDriveAccount)",
                            reference = "drive://$defaultDriveAccount/land-records"
                        )
                    )
                    statusBannerText = "গুগল ড্রাইভ এন্ট্রি পয়েন্ট খোলা হয়েছে ($defaultDriveAccount)"
                    onGoogleDriveRequested(defaultDriveAccount)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_import_center_google_drive"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF0F766E)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Google Drive Entry Point",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "গুগল ড্রাইভ থেকে আমদানি ($defaultDriveAccount)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Section 3: Manual Entry Field for Computer Uploads
            Text(
                text = "৩. কম্পিউটার আপলোড (ম্যানুয়াল এন্ট্রি / পেয়ারিং রেফারেন্স)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = computerUploadEntry,
                    onValueChange = { computerUploadEntry = it },
                    label = {
                        Text("কম্পিউটার আপলোড কোড / URL / ফাইল রেফারেন্স", fontSize = 12.sp)
                    },
                    placeholder = {
                        Text("যেমন: http://10.0.2.2:8000 বা পেয়ারিং টোকেন...", fontSize = 11.sp)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = "Computer Upload Input"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_import_center_computer_upload")
                )

                Button(
                    onClick = {
                        val trimmed = computerUploadEntry.trim()
                        if (trimmed.isNotEmpty()) {
                            recentSelections.add(
                                0,
                                ImportCenterSelection(
                                    sourceLabel = "Computer Upload",
                                    reference = trimmed
                                )
                            )
                            statusBannerText = "কম্পিউটার আপলোড রেফারেন্স গৃহীত হয়েছে: $trimmed"
                            onComputerUploadSubmitted(trimmed)
                            computerUploadEntry = ""
                        }
                    },
                    enabled = computerUploadEntry.isNotBlank(),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("btn_import_center_computer_submit"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Submit Computer Upload",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("যুক্ত করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Live status feedback banner
            val activeBanner = statusBannerText ?: importUiState.statusMessage ?: importUiState.errorMessage
            AnimatedVisibility(visible = activeBanner != null) {
                activeBanner?.let { statusMessage ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_center_status_banner"),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (recentSelections.isNotEmpty()) {
                                val latest = recentSelections.first()
                                Text(
                                    text = "সর্বশেষ উৎস: ${latest.sourceLabel} -> ${latest.reference}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-screen wrapper around [ImportCenter] when hosted as a standalone destination.
 */
@Composable
fun ImportCenterScreen(
    modifier: Modifier = Modifier,
    documentImportViewModel: DocumentImportViewModel = viewModel(
        factory = DocumentImportViewModel.Factory(LocalContext.current)
    ),
    onDocumentSelected: (Uri) -> Unit = {},
    onFolderSelected: (Uri) -> Unit = {},
    onGoogleDriveRequested: (String) -> Unit = {},
    onComputerUploadSubmitted: (String) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ImportCenter(
            documentImportViewModel = documentImportViewModel,
            processDirectlyInViewModel = true,
            onDocumentSelected = onDocumentSelected,
            onFolderSelected = onFolderSelected,
            onGoogleDriveRequested = onGoogleDriveRequested,
            onComputerUploadSubmitted = onComputerUploadSubmitted
        )
    }
}
