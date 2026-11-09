package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AccentPink
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.WhiteTransparent20
import com.example.ui.theme.coloredGlow

/**
 * Primary Indigo Card featuring a deep BrandIndigo background and matching soft ambient glow.
 */
@Composable
fun PrimaryIndigoCard(
    timeRange: String = "10:00 AM - 12:30 PM",
    taskTitle: String = "ওয়ারিশ সনদ ও আরএস খতিয়ান যাচাই",
    subtitle: String = "কাইতলা (দক্ষিণ) ইউনিয়ন • ৮টি মাস্টার সনদ",
    categoryBadgeText: String = "Verification",
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 1. Apply the shadow first, matching the card's border radius
            .coloredGlow(
                color = BrandIndigo,
                alpha = 0.3f,
                borderRadius = 28.dp,
                blurRadius = 24.dp,
                offsetY = 12.dp
            )
            // 2. Set the actual card background and shape
            .background(color = BrandIndigo, shape = RoundedCornerShape(28.dp))
            // 3. Apply the internal padding
            .padding(24.dp)
            .testTag("primary_indigo_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = "Time",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = timeRange,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("primary_card_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More Options",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = taskTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarCluster(avatarDrawables = emptyList(), borderColor = BrandIndigo)

                Box(
                    modifier = Modifier
                        .background(
                            color = WhiteTransparent20,
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = categoryBadgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Secondary Task Card with scaled-down ambient glow using the AccentPink palette.
 */
@Composable
fun SecondaryTaskCard(
    timeRange: String = "1:30 PM - 3:00 PM",
    taskTitle: String = "UI Design",
    categoryBadgeText: String = "Design",
    avatarDrawables: List<Int> = emptyList(),
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 1. Scaled-down ambient glow using the Pink accent
            .coloredGlow(
                color = AccentPink,
                alpha = 0.25f, // Slightly softer than the primary card
                borderRadius = 24.dp,
                blurRadius = 20.dp,
                offsetY = 10.dp
            )
            // 2. Card surface
            .background(color = AccentPink, shape = RoundedCornerShape(24.dp))
            // 3. Slightly tighter internal padding
            .padding(20.dp)
            .testTag("secondary_task_card")
    ) {
        // --- Top Row: Time & Menu Icon ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = "Time",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = timeRange,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(20.dp)
                    .testTag("secondary_card_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More Options",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Middle Row: Task Title ---
        Text(
            text = taskTitle,
            style = MaterialTheme.typography.titleMedium, // Smaller than the primary card's titleLarge
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Bottom Row: Avatars & Category Badge ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarCluster(
                avatarDrawables = avatarDrawables,
                borderColor = AccentPink
            )

            Box(
                modifier = Modifier
                    .background(
                        color = WhiteTransparent20,
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = categoryBadgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun AvatarCluster(
    avatarDrawables: List<Int>,
    borderColor: Color
) {
    if (avatarDrawables.isEmpty()) {
        val initials = listOf("ফ", "র", "খ")
        Row(verticalAlignment = Alignment.CenterVertically) {
            initials.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .offset(x = (-8 * index).dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(WhiteTransparent20)
                        .border(1.5.dp, borderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            avatarDrawables.take(4).forEachIndexed { index, drawableRes ->
                Image(
                    painter = painterResource(id = drawableRes),
                    contentDescription = "Participant Avatar ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(x = (-8 * index).dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, borderColor, CircleShape)
                )
            }
        }
    }
}
