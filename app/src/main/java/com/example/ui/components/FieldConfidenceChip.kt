package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConfidenceLevel
import com.example.ui.theme.StatusProbable
import com.example.ui.theme.StatusUncertain
import com.example.ui.theme.StatusVerified

@Composable
fun FieldConfidenceChip(
    confidence: ConfidenceLevel,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon, label) = when (confidence) {
        ConfidenceLevel.VERIFIED -> Quad(
            StatusVerified.copy(alpha = 0.15f),
            StatusVerified,
            Icons.Default.CheckCircle,
            "যাচাইকৃত"
        )
        ConfidenceLevel.PROBABLE -> Quad(
            StatusProbable.copy(alpha = 0.15f),
            StatusProbable,
            Icons.Default.HelpOutline,
            "সম্ভাব্য"
        )
        ConfidenceLevel.UNCERTAIN -> Quad(
            StatusUncertain.copy(alpha = 0.15f),
            StatusUncertain,
            Icons.Default.Warning,
            "অযাচাইকৃত"
        )
    }

    Row(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = textColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
