package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.coloredGlow

// Simple data class to hold the date information
data class CalendarDate(
    val dayOfWeek: String, // e.g., "Mon", "Tue"
    val dayOfMonth: String // e.g., "10", "11"
)

@Composable
fun DateSlider(
    dates: List<CalendarDate>,
    selectedIndex: Int,
    onDateSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.testTag("date_slider"),
        // Spacing between the date pills
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        // Padding at the start and end of the row so it aligns with your screen's edges (usually 24.dp)
        // Plus vertical padding so the bottom glow doesn't get clipped by the LazyRow bounds!
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        itemsIndexed(dates) { index, date ->
            val isSelected = index == selectedIndex
            DatePill(
                date = date,
                isSelected = isSelected,
                onClick = { onDateSelected(index) },
                modifier = Modifier.testTag("date_pill_$index")
            )
        }
    }
}

@Composable
fun DatePill(
    date: CalendarDate,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Dynamically change colors based on selection state
    val backgroundColor = if (isSelected) BrandIndigo else Color.Transparent
    val dayTextColor = if (isSelected) Color.White.copy(alpha = 0.7f) else TextSecondaryMuted
    val numberTextColor = if (isSelected) Color.White else TextPrimaryDark

    Column(
        modifier = modifier
            .width(60.dp) // Fixed width gives it that uniform "pill" look
            // 1. Conditionally apply the glow ONLY if this pill is selected
            .then(
                if (isSelected) {
                    Modifier.coloredGlow(
                        color = BrandIndigo,
                        alpha = 0.25f,
                        borderRadius = 24.dp,
                        blurRadius = 16.dp, // Tighter blur for a smaller element
                        offsetY = 8.dp
                    )
                } else {
                    Modifier
                }
            )
            // 2. Apply background and shape
            .background(color = backgroundColor, shape = RoundedCornerShape(24.dp))
            // 3. Clip the clickable ripple effect to the rounded corners
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }
            // 4. Internal padding
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Day of the Week (e.g., "Wed")
        Text(
            text = date.dayOfWeek,
            style = MaterialTheme.typography.bodyMedium,
            color = dayTextColor
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Date Number (e.g., "12")
        Text(
            text = date.dayOfMonth,
            style = MaterialTheme.typography.titleMedium,
            color = numberTextColor
        )
    }
}
