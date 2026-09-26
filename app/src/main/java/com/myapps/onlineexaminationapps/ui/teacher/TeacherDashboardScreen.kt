package com.myapps.onlineexaminationapps.ui.teacher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.myapps.onlineexaminationapps.firebase.CProgrammingDataSeeder
import com.myapps.onlineexaminationapps.firebase.ChapterRepository
import com.myapps.onlineexaminationapps.firebase.QuestionRepository
import com.myapps.onlineexaminationapps.model.Chapter
import com.myapps.onlineexaminationapps.model.Question
import com.myapps.onlineexaminationapps.model.TeacherAnalytics
import com.myapps.onlineexaminationapps.ui.components.ExpandableText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class TeacherDashboardViewModel : ViewModel() {
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
        val currentTeacher = FirebaseAuth.getInstance().currentUser
        if (currentTeacher == null) {
            isLoading = false
            errorMessage = "User not logged in. Please sign in."
            return
        }

        val teacherUid = currentTeacher.uid
        isLoading = true
        errorMessage = null
        chaptersJob?.cancel()
        questionsJob?.cancel()

        CProgrammingDataSeeder.seedCProgrammingCourse()

        // Realtime Firestore snapshot listener for My Chapters (teacherId == currentUser.uid)
        chaptersJob = viewModelScope.launch {
            chapterRepository.getTeacherChaptersRealtime(teacherUid)
                .catch { e ->
                    isLoading = false
                    errorMessage = "Firestore operation failed: ${e.message ?: "Unable to fetch chapters."}"
                }
                .collect { result ->
                    isLoading = false
                    if (result.isSuccess) {
                        chapters = result.getOrNull() ?: emptyList()
                        errorMessage = null
                    } else {
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to load chapters."
                    }
                }
        }

        // Realtime Firestore snapshot listener for My Questions
        questionsJob = viewModelScope.launch {
            questionRepository.getTeacherQuestionsRealtime(teacherUid)
                .catch {
                    // non-fatal
                }
                .collect { result ->
                    if (result.isSuccess) {
                        questions = result.getOrNull() ?: emptyList()
                    }
                }
        }

        viewModelScope.launch {
            val res = analyticsRepository.getTeacherAnalytics(teacherUid)
            if (res.isSuccess) {
                analytics = res.getOrNull()
            }
        }
    }

    fun deleteChapter(chapterId: String) {
        viewModelScope.launch {
            questionRepository.deleteQuestionsByChapter(chapterId)
            chapterRepository.deleteChapter(chapterId)
        }
    }

    fun deleteQuestion(questionId: String) {
        viewModelScope.launch {
            questionRepository.deleteQuestion(questionId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDashboardScreen(
    onCreateChapterClick: () -> Unit,
    onCreateQuestionClick: () -> Unit = {},
    onViewStudentAnswersClick: () -> Unit = {},
    onAnalyticsClick: () -> Unit = {},
    onChapterClick: (String) -> Unit = {},
    onEditQuestionClick: (String, String) -> Unit = { _, _ -> },
    onLogoutClick: () -> Unit = {},
    viewModel: TeacherDashboardViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadTeacherData()
    }

    val chapterMap = remember(viewModel.chapters) {
        viewModel.chapters.associateBy { it.effectiveId }
    }

    var chapterToDelete by remember { mutableStateOf<Chapter?>(null) }
    var questionToDelete by remember { mutableStateOf<Question?>(null) }

    if (chapterToDelete != null) {
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            title = { Text("Delete Chapter") },
            text = { Text("Are you sure you want to delete chapter \"${chapterToDelete?.displayTitle}\"? All questions associated with this chapter will also be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        chapterToDelete?.let { viewModel.deleteChapter(it.effectiveId) }
                        chapterToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { chapterToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (questionToDelete != null) {
        AlertDialog(
            onDismissRequest = { questionToDelete = null },
            title = { Text("Delete Question") },
            text = { Text("Are you sure you want to delete this question?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        questionToDelete?.let { viewModel.deleteQuestion(it.effectiveId) }
                        questionToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { questionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (viewModel.isLoading && viewModel.chapters.isEmpty() && viewModel.questions.isEmpty()) {
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
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadTeacherData() }) {
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
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No chapters created yet.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    } else {
                        items(viewModel.chapters, key = { it.effectiveId }) { chapter ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onChapterClick(chapter.effectiveId) },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
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
                                            Spacer(modifier = Modifier.height(4.dp))
                                            ExpandableText(
                                                text = chapter.description,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { chapterToDelete = chapter }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Chapter",
                                            tint = MaterialTheme.colorScheme.error
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
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No questions created yet.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    } else {
                        items(viewModel.questions, key = { it.effectiveId }) { question ->
                            val chapName = chapterMap[question.chapterId]?.displayTitle ?: "Chapter"
                            var isExpanded by remember { mutableStateOf(false) }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isExpanded = !isExpanded },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Q: ${question.effectiveQuestionText}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { onEditQuestionClick(question.chapterId, question.effectiveId) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Question"
                                                )
                                            }
                                            IconButton(
                                                onClick = { questionToDelete = question }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Question",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            IconButton(onClick = { isExpanded = !isExpanded }) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(if (question.isMcq) "MCQ" else "Short Question") },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                        Text(
                                            text = "Marks: ${question.marks}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Chapter: $chapName",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (isExpanded) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider()
                                        Spacer(modifier = Modifier.height(12.dp))

                                        if (question.isMcq) {
                                            Text(
                                                text = "Options & Answer:",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))

                                            val opts = listOf(
                                                "Option A: " + question.effectiveOptionA,
                                                "Option B: " + question.effectiveOptionB,
                                                "Option C: " + question.effectiveOptionC,
                                                "Option D: " + question.effectiveOptionD
                                            )
                                            opts.forEach { optStr ->
                                                if (optStr.length > 10) {
                                                    val isCorrect = question.correctAnswer.isNotBlank() && optStr.contains(question.correctAnswer)
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = optStr,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (isCorrect) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Correct Answer",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Text(
                                                                text = "(Correct)",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = "Expected Answer:",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (question.effectiveAnswer.isBlank()) "No expected answer set." else question.effectiveAnswer,
                                                style = MaterialTheme.typography.bodyMedium,
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
        }
    }
}
