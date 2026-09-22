package com.myapps.onlineexaminationapps.ui.teacher

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.myapps.onlineexaminationapps.firebase.AnalyticsRepository
import com.myapps.onlineexaminationapps.model.ChapterAnalytics
import com.myapps.onlineexaminationapps.model.TeacherAnalytics
import kotlinx.coroutines.launch
import java.util.Locale

class TeacherAnalyticsViewModel : ViewModel() {
    private val repository = AnalyticsRepository()

    var analytics by mutableStateOf<TeacherAnalytics?>(null)
    var isLoading by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)

    fun loadAnalytics() {
        val teacherUid = FirebaseAuth.getInstance().currentUser?.uid
        if (teacherUid == null) {
            isLoading = false
            errorMessage = "Teacher not logged in."
            return
        }

        isLoading = true
        errorMessage = null
        Log.d("TEACHER_ANALYTICS_DEBUG", "TeacherAnalyticsViewModel.loadAnalytics for UID: $teacherUid")

        viewModelScope.launch {
            val res = repository.getTeacherAnalytics(teacherUid)
            isLoading = false
            if (res.isSuccess) {
                analytics = res.getOrNull()
                Log.d("TEACHER_ANALYTICS_DEBUG", "Analytics loaded successfully!")
            } else {
                Log.e("TEACHER_ANALYTICS_DEBUG", "Failed to load analytics", res.exceptionOrNull())
                errorMessage = res.exceptionOrNull()?.message ?: "Failed to load analytics."
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherAnalyticsScreen(
    onBackClick: () -> Unit,
    onSubmissionClick: (String) -> Unit = {},
    viewModel: TeacherAnalyticsViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadAnalytics()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Analytics") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (viewModel.isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Loading analytics...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (viewModel.errorMessage != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Failed to load analytics: ${viewModel.errorMessage}",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadAnalytics() }) {
                        Text("Retry")
                    }
                }
            } else {
                val data = viewModel.analytics
                if (data == null || data.totalChapters == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No Chapters Available",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Section 1: Performance Overview
                        item {
                            Text(
                                text = "Performance Overview",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OverviewCard("Chapters", "${data.totalChapters}", Modifier.weight(1f))
                                OverviewCard("Questions", "${data.totalQuestions}", Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OverviewCard("Students", "${data.totalStudents}", Modifier.weight(1f))
                                OverviewCard("Submissions", "${data.totalSubmissions}", Modifier.weight(1f))
                            }
                        }

                        // Section 2: Bar Chart - Average Score by Chapter
                        if (data.chapterAnalyticsList.isNotEmpty()) {
                            item {
                                ChapterBarChart(analyticsList = data.chapterAnalyticsList)
                            }
                        }

                        // Section 3: Submission Status Summary Graph
                        if (data.totalSubmissions > 0) {
                            item {
                                StatusSummaryCard(
                                    completed = data.completedSubmissions,
                                    pending = data.pendingSubmissions
                                )
                            }
                        } else {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Students have not submitted any exams yet.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }

                        // Section 4: Chapter Performance Details
                        item {
                            Text(
                                text = "Chapter Performance",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(data.chapterAnalyticsList, key = { it.chapterId }) { chap ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = chap.chapterName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = String.format(Locale.getDefault(), "%.1f%%", chap.averagePercentage),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Questions: ${chap.questionCount}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Students: ${chap.uniqueStudentsCount}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Submissions: ${chap.totalSubmissions}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    if (chap.totalSubmissions > 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Status: ${chap.completedSubmissions} Completed • ${chap.pendingSubmissions} Pending",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Section 5: Recent Submissions
                        if (data.recentSubmissions.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Recent Submissions",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            items(data.recentSubmissions, key = { it.id }) { sub ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSubmissionClick(sub.id) },
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = if (sub.chapterName.isNotEmpty()) sub.chapterName else "Exam",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            AssistChip(
                                                onClick = { onSubmissionClick(sub.id) },
                                                label = { Text(sub.status) },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = if (sub.status == "Completed")
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    else
                                                        MaterialTheme.colorScheme.secondaryContainer
                                                )
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = "Score: ${sub.obtainedMarks} / ${sub.totalMarks} (${String.format(Locale.getDefault(), "%.1f%%", sub.percentage)})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ChapterBarChart(
    analyticsList: List<ChapterAnalytics>,
    modifier: Modifier = Modifier
) {
    if (analyticsList.isEmpty()) return

    val barColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outline

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Average Score by Chapter",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val width = size.width
                val height = size.height
                val bottomPadding = 20f
                val topPadding = 20f
                val chartHeight = height - bottomPadding - topPadding

                val count = analyticsList.size
                val barWidth = (width / (count * 2)).coerceIn(16f, 60f)
                val spacing = (width - (count * barWidth)) / (count + 1)

                // Y-axis
                drawLine(
                    color = axisColor,
                    start = Offset(0f, topPadding),
                    end = Offset(0f, height - bottomPadding),
                    strokeWidth = 2f
                )

                // X-axis
                drawLine(
                    color = axisColor,
                    start = Offset(0f, height - bottomPadding),
                    end = Offset(width, height - bottomPadding),
                    strokeWidth = 2f
                )

                analyticsList.forEachIndexed { index, item ->
                    val x = spacing + index * (barWidth + spacing) + barWidth / 2
                    val pct = item.averagePercentage.coerceIn(0.0, 100.0).toFloat()
                    val barHeight = (pct / 100f) * chartHeight
                    val yTop = height - bottomPadding - barHeight

                    if (barHeight > 0f) {
                        drawRect(
                            color = barColor,
                            topLeft = Offset(x - barWidth / 2, yTop),
                            size = Size(barWidth, barHeight)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                analyticsList.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.chapterName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f%%", item.averagePercentage),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusSummaryCard(
    completed: Int,
    pending: Int,
    modifier: Modifier = Modifier
) {
    val total = completed + pending
    val completedRatio = if (total > 0) completed.toFloat() / total else 0f

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Submission Status Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Completed: $completed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Pending: $pending",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { completedRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.secondaryContainer
            )
        }
    }
}
