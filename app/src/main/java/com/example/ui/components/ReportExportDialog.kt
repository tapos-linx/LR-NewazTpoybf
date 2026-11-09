package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.export.ExportFormat

@Composable
fun ReportExportDialog(
    documentTitle: String,
    suggestedFileNameJson: String,
    suggestedFileNameText: String,
    suggestedFileNameCsv: String = "",
    suggestedFileNameZip: String = "",
    jsonPreview: String,
    textPreview: String,
    csvPreview: String = "",
    zipPreview: String = "",
    initialFormat: ExportFormat = ExportFormat.CSV,
    onSaveToSafUri: (Uri, ExportFormat) -> Unit,
    onSaveToPhoneDownloads: ((ExportFormat) -> Unit)? = null,
    onShare: (ExportFormat) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFormat by remember { mutableStateOf(initialFormat) }
    var showPreview by remember { mutableStateOf(true) }

    val currentSuggestedFileName = when (selectedFormat) {
        ExportFormat.JSON -> suggestedFileNameJson
        ExportFormat.TEXT -> suggestedFileNameText
        ExportFormat.CSV -> if (suggestedFileNameCsv.isNotBlank()) suggestedFileNameCsv else suggestedFileNameText.replace(".txt", ".csv")
        ExportFormat.ZIP -> if (suggestedFileNameZip.isNotBlank()) suggestedFileNameZip else suggestedFileNameText.replace(".txt", ".zip")
    }

    // Use "*/*" so dynamic format switching (.csv, .zip, .json, .txt) works seamlessly on PC local disk & SAF
    val safCreateDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri != null) {
            onSaveToSafUri(uri, selectedFormat)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "রিপোর্ট সংরক্ষণ (SAF)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "বন্ধ করুন")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "নথি: $documentTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Text(
                    text = "রিপোর্ট ও আর্কাইভ ফরম্যাট নির্বাচন করুন:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedFormat == ExportFormat.CSV,
                        onClick = { selectedFormat = ExportFormat.CSV },
                        label = { Text("CSV (.csv)", fontSize = 11.sp) },
                        modifier = Modifier.testTag("chip_export_csv")
                    )

                    FilterChip(
                        selected = selectedFormat == ExportFormat.JSON,
                        onClick = { selectedFormat = ExportFormat.JSON },
                        label = { Text("JSON (.json)", fontSize = 11.sp) },
                        modifier = Modifier.testTag("chip_export_json")
                    )

                    FilterChip(
                        selected = selectedFormat == ExportFormat.TEXT,
                        onClick = { selectedFormat = ExportFormat.TEXT },
                        label = { Text("টেক্সট (.txt)", fontSize = 11.sp) },
                        modifier = Modifier.testTag("chip_export_text")
                    )

                    FilterChip(
                        selected = selectedFormat == ExportFormat.ZIP,
                        onClick = { selectedFormat = ExportFormat.ZIP },
                        label = { Text("ZIP (.zip)", fontSize = 11.sp) },
                        modifier = Modifier.testTag("chip_export_zip")
                    )
                }

                // Suggested filename info (above the fold)
                Text(
                    text = "প্রস্তাবিত নাম: $currentSuggestedFileName",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.secondary
                )

                // Direct Phone Memory Download button
                if (onSaveToPhoneDownloads != null) {
                    Button(
                        onClick = { onSaveToPhoneDownloads(selectedFormat) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_phone_downloads"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ফোন মেমোরি (Downloads) ফোল্ডারে সরাসরি ডাউনলোড",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Storage Access Framework description note
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "💻 পিসি লোকাল ডিস্ক ও ফোন মেমোরি (SAF / MediaStore)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "ফোন মেমোরির Downloads/NewazLandRecords ফোল্ডারে সরাসরি ডাউনলোড করুন অথবা পিসির লোকাল ডিস্ক / ফোল্ডারে সেভ করুন।",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Preview toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "রিপোর্ট কনটেন্ট প্রিভিউ:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { showPreview = !showPreview }) {
                        Text(if (showPreview) "লুকান" else "দেখান", fontSize = 12.sp)
                    }
                }

                AnimatedVisibility(visible = showPreview) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        val previewContent = when (selectedFormat) {
                            ExportFormat.JSON -> jsonPreview
                            ExportFormat.TEXT -> textPreview
                            ExportFormat.CSV -> csvPreview
                            ExportFormat.ZIP -> if (zipPreview.isNotBlank()) zipPreview else "📦 সম্পূর্ণ আর্কাইভ বান্ডেল ($currentSuggestedFileName)\nঅন্তর্ভুক্ত: মূল নথি, CSV, JSON ও সারসংক্ষেপ রিপোর্ট।"
                        }
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = previewContent,
                                modifier = Modifier.testTag("text_report_preview"),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    safCreateDocumentLauncher.launch(currentSuggestedFileName)
                },
                modifier = Modifier.testTag("btn_save_saf_confirm"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ডিভাইসে সেভ করুন (SAF)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { onShare(selectedFormat) },
                modifier = Modifier.testTag("btn_share_report_dialog")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("শেয়ার")
            }
        }
    )
}
