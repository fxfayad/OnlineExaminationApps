package com.myapps.onlineexaminationapps.ui.teacher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.myapps.onlineexaminationapps.firebase.AnalyticsRepository
import com.myapps.onlineexaminationapps.firebase.ChapterRepository
import com.myapps.onlineexaminationapps.firebase.QuestionRepository
import com.myapps.onlineexaminationapps.model.Chapter
import com.myapps.onlineexaminationapps.model.Question
import com.myapps.onlineexaminationapps.model.TeacherAnalytics
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class TeacherHomeViewModel : ViewModel() {
    private val chapterRepository = ChapterRepository()
    private val questionRepository = QuestionRepository()
    private val analyticsRepository = AnalyticsRepository()

    var chapters by mutableStateOf<List<Chapter>>(emptyList())
    var questions by mutableStateOf<List<Question>>(emptyList())
    var analytics by mutableStateOf<TeacherAnalytics?>(null)
    var isLoading by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)

    private var chaptersJob: Job? = null
    private var questionsJob: Job? = null

    fun loadTeacherData() {
        val currentTeacherUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentTeacherUid == null) {
            isLoading = false
            errorMessage = "User not logged in."
            return
        }

        isLoading = true
        errorMessage = null
        chaptersJob?.cancel()
        questionsJob?.cancel()

        chaptersJob = viewModelScope.launch {
            chapterRepository.getTeacherChaptersRealtime(currentTeacherUid)
                .catch {
                    isLoading = false
                    errorMessage = "Failed to load chapters."
                }
                .collect { result ->
                    if (result.isSuccess) {
                        chapters = result.getOrNull() ?: emptyList()
                    } else {
                        errorMessage = "Failed to load chapters."
                    }
                    isLoading = false
                }
        }

        questionsJob = viewModelScope.launch {
            questionRepository.getTeacherQuestionsRealtime(currentTeacherUid)
                .catch {
                    errorMessage = "Failed to load questions."
                }
                .collect { result ->
                    if (result.isSuccess) {
                        questions = result.getOrNull() ?: emptyList()
                    }
                }
        }

        viewModelScope.launch {
            val res = analyticsRepository.getTeacherAnalytics(currentTeacherUid)
            if (res.isSuccess) {
                analytics = res.getOrNull()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherHomeScreen(
    onCreateChapterClick: () -> Unit,
    onCreateQuestionClick: () -> Unit,
    onViewStudentAnswersClick: () -> Unit,
    onAnalyticsClick: () -> Unit = {},
    onChapterClick: (String) -> Unit,
    onLogoutClick: () -> Unit,
    viewModel: TeacherHomeViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadTeacherData()
    }

    val chapterMap = remember(viewModel.chapters) {
        viewModel.chapters.associateBy { it.id }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teacher Dashboard") },
                actions = {
                    IconButton(onClick = onLogoutClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (viewModel.isLoading && viewModel.chapters.isEmpty() && viewModel.questions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Loading dashboard...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Top Action Buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCreateChapterClick,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("Create Chapter", textAlign = TextAlign.Center)
                        }
                        Button(
                            onClick = onCreateQuestionClick,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("Create Question", textAlign = TextAlign.Center)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onViewStudentAnswersClick,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("View Student Answer", textAlign = TextAlign.Center)
                        }
                        OutlinedButton(
                            onClick = onAnalyticsClick,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Analytics", textAlign = TextAlign.Center)
                        }
                    }
                }

                // Section 0: Performance Overview
                val analyticsData = viewModel.analytics
                if (analyticsData != null) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAnalyticsClick() },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Performance Overview",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Chapters: ${analyticsData.totalChapters}", style = MaterialTheme.typography.bodyMedium)
                                    Text("Questions: ${analyticsData.totalQuestions}", style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Students: ${analyticsData.totalStudents}", style = MaterialTheme.typography.bodyMedium)
                                    Text("Submissions: ${analyticsData.totalSubmissions}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                // Section 1: My Chapters
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "My Chapters",
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
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No chapters created yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                } else {
                    items(viewModel.chapters, key = { it.id }) { chapter ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChapterClick(chapter.id) },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = chapter.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (chapter.description.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = chapter.description,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2: My Questions
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "My Questions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (viewModel.questions.isEmpty()) {
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
                                    text = "No questions created yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                } else {
                    items(viewModel.questions, key = { it.id }) { question ->
                        val chapterName = chapterMap[question.chapterId]?.name ?: "Chapter"
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Q: ${question.question}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Type: ${if (question.type == "mcq") "MCQ" else "Short Question"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Marks: ${question.marks}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Chapter: $chapterName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
