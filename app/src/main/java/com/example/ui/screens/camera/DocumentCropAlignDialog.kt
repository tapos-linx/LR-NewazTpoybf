package com.example.ui.screens.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ui.theme.StatusVerified
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class DragHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    EDGE_TOP,
    EDGE_BOTTOM,
    EDGE_LEFT,
    EDGE_RIGHT,
    CENTER
}

@Composable
fun DocumentCropAlignDialog(
    imageFile: File,
    pageIndex: Int,
    onDismiss: () -> Unit,
    onApplyCrop: (cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float, totalRotation: Float) -> Unit
) {
    // Rotation state: 90-degree step + fine angle (-20° .. +20°)
    var quarterRotation by remember { mutableIntStateOf(0) } // 0, 90, 180, 270
    var fineSkewAngle by remember { mutableFloatStateOf(0f) }

    // Normalized crop rectangle: 0.0 .. 1.0
    var cropLeft by remember { mutableFloatStateOf(0.04f) }
    var cropTop by remember { mutableFloatStateOf(0.04f) }
    var cropRight by remember { mutableFloatStateOf(0.96f) }
    var cropBottom by remember { mutableFloatStateOf(0.96f) }

    var selectedPreset by remember { mutableStateOf("auto") }
    var isProcessing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("document_crop_align_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .testTag("btn_cancel_crop")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "বাতিল", tint = Color.White)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "দলিল ক্রপ ও এলাইনমেন্ট",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "পাতা নং ${pageIndex + 1} • সীমানা ও কোণ সমন্বয়",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = {
                            isProcessing = true
                            val totalRotation = (quarterRotation.toFloat() + fineSkewAngle)
                            onApplyCrop(cropLeft, cropTop, cropRight, cropBottom, totalRotation)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusVerified),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.testTag("btn_apply_crop")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সংরক্ষণ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Main Interactive Cropping Canvas
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val containerWidth = maxWidth.value
                    val containerHeight = maxHeight.value

                    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Render image with rotation
                        AsyncImage(
                            model = imageFile,
                            contentDescription = "দলিল ছবি",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(quarterRotation.toFloat() + fineSkewAngle)
                        )

                        // Interactive Crop Overlay Canvas
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val w = size.width.toFloat()
                                            val h = size.height.toFloat()
                                            val x = offset.x
                                            val y = offset.y

                                            val l = cropLeft * w
                                            val t = cropTop * h
                                            val r = cropRight * w
                                            val b = cropBottom * h

                                            val hitThreshold = 64f

                                            activeHandle = when {
                                                // Corners
                                                abs(x - l) <= hitThreshold && abs(y - t) <= hitThreshold -> DragHandle.TOP_LEFT
                                                abs(x - r) <= hitThreshold && abs(y - t) <= hitThreshold -> DragHandle.TOP_RIGHT
                                                abs(x - l) <= hitThreshold && abs(y - b) <= hitThreshold -> DragHandle.BOTTOM_LEFT
                                                abs(x - r) <= hitThreshold && abs(y - b) <= hitThreshold -> DragHandle.BOTTOM_RIGHT
                                                // Edges
                                                abs(y - t) <= hitThreshold && x in l..r -> DragHandle.EDGE_TOP
                                                abs(y - b) <= hitThreshold && x in l..r -> DragHandle.EDGE_BOTTOM
                                                abs(x - l) <= hitThreshold && y in t..b -> DragHandle.EDGE_LEFT
                                                abs(x - r) <= hitThreshold && y in t..b -> DragHandle.EDGE_RIGHT
                                                // Inside Box
                                                x in l..r && y in t..b -> DragHandle.CENTER
                                                else -> DragHandle.NONE
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val w = size.width.toFloat()
                                            val h = size.height.toFloat()
                                            val dx = dragAmount.x / w
                                            val dy = dragAmount.y / h
                                            val minSize = 0.08f

                                            when (activeHandle) {
                                                DragHandle.TOP_LEFT -> {
                                                    cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - minSize)
                                                    cropTop = (cropTop + dy).coerceIn(0f, cropBottom - minSize)
                                                }
                                                DragHandle.TOP_RIGHT -> {
                                                    cropRight = (cropRight + dx).coerceIn(cropLeft + minSize, 1f)
                                                    cropTop = (cropTop + dy).coerceIn(0f, cropBottom - minSize)
                                                }
                                                DragHandle.BOTTOM_LEFT -> {
                                                    cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - minSize)
                                                    cropBottom = (cropBottom + dy).coerceIn(cropTop + minSize, 1f)
                                                }
                                                DragHandle.BOTTOM_RIGHT -> {
                                                    cropRight = (cropRight + dx).coerceIn(cropLeft + minSize, 1f)
                                                    cropBottom = (cropBottom + dy).coerceIn(cropTop + minSize, 1f)
                                                }
                                                DragHandle.EDGE_TOP -> {
                                                    cropTop = (cropTop + dy).coerceIn(0f, cropBottom - minSize)
                                                }
                                                DragHandle.EDGE_BOTTOM -> {
                                                    cropBottom = (cropBottom + dy).coerceIn(cropTop + minSize, 1f)
                                                }
                                                DragHandle.EDGE_LEFT -> {
                                                    cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - minSize)
                                                }
                                                DragHandle.EDGE_RIGHT -> {
                                                    cropRight = (cropRight + dx).coerceIn(cropLeft + minSize, 1f)
                                                }
                                                DragHandle.CENTER -> {
                                                    val boxW = cropRight - cropLeft
                                                    val boxH = cropBottom - cropTop
                                                    var newL = cropLeft + dx
                                                    var newT = cropTop + dy
                                                    if (newL < 0f) newL = 0f
                                                    if (newL + boxW > 1f) newL = 1f - boxW
                                                    if (newT < 0f) newT = 0f
                                                    if (newT + boxH > 1f) newT = 1f - boxH
                                                    cropLeft = newL
                                                    cropRight = newL + boxW
                                                    cropTop = newT
                                                    cropBottom = newT + boxH
                                                }
                                                DragHandle.NONE -> {}
                                            }
                                        },
                                        onDragEnd = {
                                            activeHandle = DragHandle.NONE
                                        }
                                    )
                                }
                        ) {
                            val w = size.width
                            val h = size.height

                            val l = cropLeft * w
                            val t = cropTop * h
                            val r = cropRight * w
                            val b = cropBottom * h

                            // Dark translucent scrim covering the excluded area
                            val scrimPath = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(Rect(0f, 0f, w, h))
                                addRect(Rect(l, t, r, b))
                            }
                            drawPath(scrimPath, color = Color.Black.copy(alpha = 0.58f))

                            // Outer Crop Border
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(l, t),
                                size = Size(r - l, b - t),
                                style = Stroke(width = 2.5f)
                            )

                            // 3x3 Rule of Thirds alignment grid lines
                            val gridColor = Color.White.copy(alpha = 0.45f)
                            val thirdW = (r - l) / 3f
                            val thirdH = (b - t) / 3f

                            // Vertical grid lines
                            drawLine(gridColor, Offset(l + thirdW, t), Offset(l + thirdW, b), strokeWidth = 1f)
                            drawLine(gridColor, Offset(l + 2 * thirdW, t), Offset(l + 2 * thirdW, b), strokeWidth = 1f)

                            // Horizontal grid lines
                            drawLine(gridColor, Offset(l, t + thirdH), Offset(r, t + thirdH), strokeWidth = 1f)
                            drawLine(gridColor, Offset(l, 2 * thirdH), Offset(r, 2 * thirdH), strokeWidth = 1f)

                            // Draw Corner Grips
                            val handleRadius = 14f
                            val handleStroke = 3f
                            val handleColor = Color(0xFF22C55E) // Bright Green

                            // Corners
                            val corners = listOf(
                                Offset(l, t),
                                Offset(r, t),
                                Offset(l, b),
                                Offset(r, b)
                            )
                            for (c in corners) {
                                drawCircle(Color.White, radius = handleRadius + 2f, center = c)
                                drawCircle(handleColor, radius = handleRadius, center = c)
                            }

                            // Edge Handles
                            val midX = (l + r) / 2f
                            val midY = (t + b) / 2f
                            val edgeRadius = 10f
                            val edgeHandles = listOf(
                                Offset(midX, t),
                                Offset(midX, b),
                                Offset(l, midY),
                                Offset(r, midY)
                            )
                            for (eh in edgeHandles) {
                                drawCircle(Color.White, radius = edgeRadius + 1f, center = eh)
                                drawCircle(Color(0xFF38BDF8), radius = edgeRadius, center = eh)
                            }
                        }
                    }
                }

                // Controls & Alignment Tools Panel
                Surface(
                    color = Color(0xFF18181B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Fine Deskew / Alignment Slider Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { quarterRotation = (quarterRotation - 90 + 360) % 360 },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                                    .testTag("btn_rotate_ccw")
                            ) {
                                Icon(Icons.Default.RotateLeft, contentDescription = "বাম দিকে ৯০° ঘুরান", tint = Color.White)
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "কোণ সমন্বয় (Fine Alignment):",
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "${"%.1f".format(quarterRotation.toFloat() + fineSkewAngle)}°",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Slider(
                                    value = fineSkewAngle,
                                    onValueChange = { fineSkewAngle = it },
                                    valueRange = -20f..20f,
                                    steps = 79,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = StatusVerified,
                                        inactiveTrackColor = Color.DarkGray
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(28.dp)
                                        .testTag("slider_fine_rotation")
                                )
                            }

                            IconButton(
                                onClick = { quarterRotation = (quarterRotation + 90) % 360 },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                                    .testTag("btn_rotate_cw")
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = "ডান দিকে ৯০° ঘুরান", tint = Color.White)
                            }

                            IconButton(
                                onClick = {
                                    fineSkewAngle = 0f
                                    quarterRotation = 0
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = "রিসেট", tint = Color.Yellow)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Aspect Ratio & Auto-Margin Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = selectedPreset == "auto",
                                onClick = {
                                    selectedPreset = "auto"
                                    cropLeft = 0.04f
                                    cropTop = 0.04f
                                    cropRight = 0.96f
                                    cropBottom = 0.96f
                                },
                                label = { Text("স্বয়ংক্রিয় মার্জিন", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = StatusVerified,
                                    selectedLabelColor = Color.White
                                )
                            )

                            FilterChip(
                                selected = selectedPreset == "a4",
                                onClick = {
                                    selectedPreset = "a4"
                                    // A4 aspect ratio 1:1.41
                                    cropLeft = 0.08f
                                    cropRight = 0.92f
                                    cropTop = 0.05f
                                    cropBottom = 0.95f
                                },
                                label = { Text("খতিয়ান (A4)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = StatusVerified,
                                    selectedLabelColor = Color.White
                                )
                            )

                            FilterChip(
                                selected = selectedPreset == "legal",
                                onClick = {
                                    selectedPreset = "legal"
                                    // Legal aspect ratio 1:1.64
                                    cropLeft = 0.10f
                                    cropRight = 0.90f
                                    cropTop = 0.03f
                                    cropBottom = 0.97f
                                },
                                label = { Text("দলিল (Legal)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = StatusVerified,
                                    selectedLabelColor = Color.White
                                )
                            )

                            FilterChip(
                                selected = selectedPreset == "full",
                                onClick = {
                                    selectedPreset = "full"
                                    cropLeft = 0f
                                    cropTop = 0f
                                    cropRight = 1f
                                    cropBottom = 1f
                                },
                                label = { Text("পূর্ণাঙ্গ", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = StatusVerified,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
