package com.myapps.onlineexaminationapps.ui.home

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.myapps.onlineexaminationapps.firebase.CProgrammingDataSeeder
import com.myapps.onlineexaminationapps.firebase.ChapterRepository
import com.myapps.onlineexaminationapps.firebase.QuestionRepository
import com.myapps.onlineexaminationapps.firebase.SubmissionRepository
import com.myapps.onlineexaminationapps.model.Chapter
import com.myapps.onlineexaminationapps.model.ExamResult
import com.myapps.onlineexaminationapps.model.Question
import com.myapps.onlineexaminationapps.ui.components.ExpandableText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Locale

class StudentDashboardViewModel : ViewModel() {
    private val chapterRepository = ChapterRepository()
    private val questionRepository = QuestionRepository()
    private val submissionRepository = SubmissionRepository()

    var chapters by mutableStateOf<List<Chapter>>(emptyList())
    var results by mutableStateOf<List<ExamResult>>(emptyList())
    var questionCounts by mutableStateOf<Map<String, Int>>(emptyMap())
    var questionsList by mutableStateOf<List<Question>>(emptyList())
    var isLoading by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)

    private var chaptersJob: Job? = null
    private var resultsJob: Job? = null

    val userEmail: String
        get() = FirebaseAuth.getInstance().currentUser?.email ?: "Student"

    val pendingChapters: List<Chapter>
        get() = chapters.filter { chap -> results.none { it.chapterId == chap.effectiveId } }

    val answeredChapterIds: Set<String>
        get() = results.map { it.chapterId }.toSet()

    fun loadStudentData() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            isLoading = false
            errorMessage = "User not logged in."
            return
        }

        isLoading = true
        errorMessage = null
        chaptersJob?.cancel()
        resultsJob?.cancel()

        // Seed 10 C Programming course chapters & short questions if not present
        CProgrammingDataSeeder.seedCProgrammingCourse()

        // Realtime Firestore snapshot listener for Available Chapters
        chaptersJob = viewModelScope.launch {
            chapterRepository.getAllChaptersRealtime()
                .catch { e ->
                    Log.e("StudentDashboard", "Flow error in getAllChaptersRealtime, falling back to direct fetch", e)
                    fetchDirectly()
                }
                .collect { result ->
                    isLoading = false
                    if (result.isSuccess) {
                        val list = result.getOrNull() ?: emptyList()
                        chapters = list
                        errorMessage = null
                        loadQuestionsAndCounts(list)
                    } else {
                        fetchDirectly()
                    }
                }
        }

        resultsJob = viewModelScope.launch {
            submissionRepository.getStudentSubmissionsRealtime(user.uid)
                .catch {
                    Log.e("StudentDashboard", "Failed to load student submissions", it)
                }
                .collect { res ->
                    if (res.isSuccess) {
                        results = res.getOrNull() ?: emptyList()
                    }
                }
        }
    }

    private fun loadQuestionsAndCounts(chapterList: List<Chapter>) {
        viewModelScope.launch {
            val counts = mutableMapOf<String, Int>()
            val allQ = mutableListOf<Question>()
            chapterList.forEach { chapter ->
                val qResult = questionRepository.getQuestionsByChapter(chapter.effectiveId)
                if (qResult.isSuccess) {
                    val qList = qResult.getOrNull() ?: emptyList()
                    counts[chapter.effectiveId] = qList.size
                    allQ.addAll(qList)
                }
            }
            questionCounts = counts
            questionsList = allQ
        }
    }

    private suspend fun fetchDirectly() {
        val directResult = chapterRepository.getAllChapters()
        isLoading = false
        if (directResult.isSuccess) {
            chapters = directResult.getOrNull() ?: emptyList()
            errorMessage = null
            loadQuestionsAndCounts(chapters)
        } else {
            errorMessage = "Failed to load chapters due to network or Firestore error."
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(
    onChapterClick: (String) -> Unit,
    onResultClick: (String) -> Unit = {},
    onLogoutClick: () -> Unit = {},
    viewModel: StudentDashboardViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadStudentData()
    }

    val chapterMap = remember(viewModel.chapters) {
        viewModel.chapters.associateBy { it.effectiveId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Student Dashboard") },
                actions = {
                    IconButton(onClick = onLogoutClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
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
            if (viewModel.isLoading && viewModel.chapters.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading dashboard...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (viewModel.errorMessage != null && viewModel.chapters.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = viewModel.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadStudentData() }) {
                        Text("Retry")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    // Welcome Message Header
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Welcome, ${viewModel.userEmail}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val pendingCount = viewModel.pendingChapters.size
                                Text(
                                    text = if (pendingCount > 0)
                                        "You have $pendingCount pending exam(s) to complete."
                                    else
                                        "All available chapter exams have been completed!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Section 1: Available Chapters (Displayed at beginning as clickable button/card-style items)
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Available Chapters",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (viewModel.chapters.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No chapter available",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    } else {
                        items(viewModel.chapters, key = { "btn_chapter_${it.effectiveId}" }) { chapter ->
                            val submission = viewModel.results.find { it.chapterId == chapter.effectiveId }
                            Button(
                                onClick = {
                                    if (submission != null) {
                                        onResultClick(submission.id)
                                    } else {
                                        onChapterClick(chapter.effectiveId)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = chapter.displayTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (chapter.description.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = chapter.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (submission != null) "View Result" else "Open Chapter",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Section 2: Questions Feed (Formatted exactly like Chapter Feed Buttons)
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Questions Feed",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (viewModel.questionsList.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No questions available",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    } else {
                        items(viewModel.questionsList, key = { "q_feed_${it.effectiveId}" }) { question ->
                            val chapTitle = chapterMap[question.chapterId]?.displayTitle ?: "Chapter Exam"
                            val submission = viewModel.results.find { it.chapterId == question.chapterId }

                            Button(
                                onClick = {
                                    if (submission != null) {
                                        onResultClick(submission.id)
                                    } else {
                                        onChapterClick(question.chapterId)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Q: ${question.effectiveQuestionText}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$chapTitle • ${if (question.isMcq) "MCQ" else "Short"} (${question.marks} marks)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (submission != null) "View Result" else "Open Question",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Section 3: Pending Exams (Unanswered)
                    if (viewModel.pendingChapters.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Pending Exams (Unanswered)",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(viewModel.pendingChapters, key = { "pending_exam_${it.effectiveId}" }) { chapter ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onChapterClick(chapter.effectiveId) },
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = chapter.displayTitle,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        AssistChip(
                                            onClick = { onChapterClick(chapter.effectiveId) },
                                            label = { Text("Pending Exam") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.HourglassTop,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                labelColor = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        )
                                    }

                                    if (chapter.description.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        ExpandableText(
                                            text = chapter.description,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    val count = viewModel.questionCounts[chapter.effectiveId] ?: 0
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "$count ${if (count == 1) "Question" else "Questions"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = { onChapterClick(chapter.effectiveId) },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Start Exam")
                                    }
                                }
                            }
                        }
                    }

                    // Section 4: My Exam Results
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "My Exam Results",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (viewModel.results.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No exam results yet.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    } else {
                        items(viewModel.results, key = { "result_item_${it.id}" }) { result ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onResultClick(result.id) },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (result.chapterName.isNotEmpty()) result.chapterName else "Chapter Exam",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        AssistChip(
                                            onClick = { onResultClick(result.id) },
                                            label = { Text(result.status) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = if (result.status.equals("COMPLETED", ignoreCase = true) || result.status == "Completed")
                                                    MaterialTheme.colorScheme.primaryContainer
                                                else
                                                    MaterialTheme.colorScheme.secondaryContainer
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Score: ${result.obtainedMarks} / ${result.totalMarks}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = String.format(Locale.getDefault(), "%.1f%%", result.percentage),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
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
