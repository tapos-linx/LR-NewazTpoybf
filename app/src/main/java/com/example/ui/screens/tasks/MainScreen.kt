package com.example.ui.screens.tasks

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.CalendarDate
import com.example.ui.components.DateSlider
import com.example.ui.components.PrimaryIndigoCard
import com.example.ui.components.SecondaryTaskCard
import com.example.ui.theme.BackgroundWhite
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryMuted

data class ScheduledTaskItem(
    val id: String,
    val timeRange: String,
    val title: String,
    val subtitle: String = "",
    val categoryBadge: String,
    val isPrimary: Boolean = false
)

@Composable
fun TaskManagerScreen(
    onNavigateBack: (() -> Unit)? = null,
    onPrimaryTaskClick: (() -> Unit)? = null,
    onSecondaryTaskClick: (() -> Unit)? = null
) {
    if (onNavigateBack != null) {
        BackHandler { onNavigateBack() }
    }

    // Generate dummy data (You can replace this with LocalDate logic)
    val weekDates = remember {
        listOf(
            CalendarDate("Mon", "10"),
            CalendarDate("Tue", "11"),
            CalendarDate("Wed", "12"), // Active default
            CalendarDate("Thu", "13"),
            CalendarDate("Fri", "14")
        )
    }

    // Remember the active selected date
    var selectedDateIndex by remember { mutableStateOf(2) }
    val selectedDate = weekDates[selectedDateIndex]

    val tasksByDay = remember {
        mapOf(
            0 to listOf(
                ScheduledTaskItem(
                    id = "mon_1",
                    timeRange = "9:30 AM - 11:30 AM",
                    title = "ফিরুজ মিয়া ওয়ারিশ সনদ যাচাই",
                    subtitle = "সনদ নং ২০২৬৫৪১১৮১২০০০০০২ • ১১ জন ওয়ারিশ",
                    categoryBadge = "Warishan",
                    isPrimary = true
                ),
                ScheduledTaskItem(
                    id = "mon_2",
                    timeRange = "1:30 PM - 3:00 PM",
                    title = "UI Design & Mouza Map Overlay",
                    categoryBadge = "Design",
                    isPrimary = false
                )
            ),
            1 to listOf(
                ScheduledTaskItem(
                    id = "tue_1",
                    timeRange = "10:00 AM - 12:00 PM",
                    title = "সিরাজ আলী ওয়ারিশ সনদ ও হিস্যা মিলকরণ",
                    subtitle = "সনদ নং ২০২৬৫৪১১৮১২০০০০০৫ • কাইতলা দক্ষিণ",
                    categoryBadge = "Verification",
                    isPrimary = true
                ),
                ScheduledTaskItem(
                    id = "tue_2",
                    timeRange = "2:00 PM - 4:00 PM",
                    title = "বাংলা হস্তলিপি (HCR) মার্জিনাল নোট সংশোধন",
                    categoryBadge = "HCR Review",
                    isPrimary = false
                )
            ),
            2 to listOf(
                ScheduledTaskItem(
                    id = "wed_1",
                    timeRange = "10:00 AM - 12:30 PM",
                    title = "ওয়ারিশ সনদ ও আরএস খতিয়ান যাচাই",
                    subtitle = "কাইতলা (দক্ষিণ) ইউনিয়ন • ৮টি মাস্টার সনদ",
                    categoryBadge = "Verification",
                    isPrimary = true
                ),
                ScheduledTaskItem(
                    id = "wed_2",
                    timeRange = "1:30 PM - 3:00 PM",
                    title = "UI Design",
                    categoryBadge = "Design",
                    isPrimary = false
                ),
                ScheduledTaskItem(
                    id = "wed_3",
                    timeRange = "3:30 PM - 5:00 PM",
                    title = "Gemini AI ভূমি রেকর্ড অপটিক্যাল নিষ্কাশন",
                    categoryBadge = "AI OCR",
                    isPrimary = false
                )
            ),
            3 to listOf(
                ScheduledTaskItem(
                    id = "thu_1",
                    timeRange = "10:30 AM - 1:00 PM",
                    title = "CS ➔ SA ➔ RS ➔ BS প্রপার্টি অ্যাটলাস চেইন",
                    subtitle = "গোয়ালী মৌজা • দাগ বিভাজন ও ধারাবাহিক স্বত্ব",
                    categoryBadge = "Atlas",
                    isPrimary = true
                ),
                ScheduledTaskItem(
                    id = "thu_2",
                    timeRange = "2:00 PM - 3:30 PM",
                    title = "বাল্ক পিডিএফ ও খতিয়ান ইমেজ ইমপোর্ট",
                    categoryBadge = "Batch",
                    isPrimary = false
                )
            ),
            4 to listOf(
                ScheduledTaskItem(
                    id = "fri_1",
                    timeRange = "9:00 AM - 11:30 AM",
                    title = "চূড়ান্ত CSV ও JSON এভিডেন্স রিপোর্ট এক্সপোর্ট",
                    subtitle = "সকল যাচাইকৃত খতিয়ান ও ওয়ারিশান তালিকা",
                    categoryBadge = "Export",
                    isPrimary = true
                ),
                ScheduledTaskItem(
                    id = "fri_2",
                    timeRange = "1:30 PM - 3:00 PM",
                    title = "মৌজা নকশা প্লট বাউন্ডারি যাচাই",
                    categoryBadge = "Cadastral",
                    isPrimary = false
                )
            )
        )
    }

    val currentTasks = tasksByDay[selectedDateIndex].orEmpty()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("task_manager_screen"),
        containerColor = BackgroundWhite
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundWhite)
                .padding(innerPadding)
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(BrandIndigo.copy(alpha = 0.08f))
                                .testTag("btn_task_manager_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "ফিরে যান",
                                tint = BrandIndigo
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "September, 2026",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Task Manager (${selectedDate.dayOfWeek} ${selectedDate.dayOfMonth})",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                    }
                }

                IconButton(
                    onClick = { onPrimaryTaskClick?.invoke() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(BrandIndigo.copy(alpha = 0.1f))
                        .testTag("btn_task_manager_calendar")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = "ক্যালেন্ডার",
                        tint = BrandIndigo
                    )
                }
            }

            // Render the Date Slider
            DateSlider(
                dates = weekDates,
                selectedIndex = selectedDateIndex,
                onDateSelected = { newIndex ->
                    selectedDateIndex = newIndex
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Section Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ongoing Tasks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
                Text(
                    text = "${currentTasks.size} Tasks",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondaryMuted
                )
            }

            // Task Cards list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("task_cards_lazy_column"),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = 8.dp,
                    bottom = 36.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                itemsIndexed(
                    items = currentTasks,
                    key = { _, item -> item.id }
                ) { _, task ->
                    if (task.isPrimary) {
                        PrimaryIndigoCard(
                            timeRange = task.timeRange,
                            taskTitle = task.title,
                            subtitle = task.subtitle,
                            categoryBadgeText = task.categoryBadge,
                            onMenuClick = { onPrimaryTaskClick?.invoke() }
                        )
                    } else {
                        SecondaryTaskCard(
                            timeRange = task.timeRange,
                            taskTitle = task.title,
                            categoryBadgeText = task.categoryBadge,
                            onMenuClick = { onSecondaryTaskClick?.invoke() }
                        )
                    }
                }
            }
        }
    }
}
