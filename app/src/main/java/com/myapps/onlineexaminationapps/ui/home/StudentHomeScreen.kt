package com.myapps.onlineexaminationapps.ui.home

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import com.myapps.onlineexaminationapps.firebase.ChapterRepository
import com.myapps.onlineexaminationapps.firebase.QuestionRepository
import com.myapps.onlineexaminationapps.firebase.SubmissionRepository
import com.myapps.onlineexaminationapps.model.Chapter
import com.myapps.onlineexaminationapps.model.ExamResult
import com.myapps.onlineexaminationapps.ui.components.ExpandableText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Locale

class StudentHomeViewModel : ViewModel() {
    private val chapterRepository = ChapterRepository()
    private val questionRepository = QuestionRepository()
    private val submissionRepository = SubmissionRepository()

    var chapters by mutableStateOf<List<Chapter>>(emptyList())
    var results by mutableStateOf<List<ExamResult>>(emptyList())
    var questionCounts by mutableStateOf<Map<String, Int>>(emptyMap())
    var isLoading by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)

    private var chaptersJob: Job? = null
    private var resultsJob: Job? = null

    fun loadStudentData() {
        val user = FirebaseAuth.getInstance().currentUser
        Log.d("ChapterDebug", "StudentHomeViewModel.loadStudentData called. Student UID: ${user?.uid}")

        isLoading = true
        errorMessage = null
        chaptersJob?.cancel()
        resultsJob?.cancel()

        chaptersJob = viewModelScope.launch {
            chapterRepository.getAllChaptersRealtime()
                .catch { e ->
                    Log.e("ChapterDebug", "Flow error in getAllChaptersRealtime, falling back to direct fetch", e)
                    fetchDirectly()
                }
                .collect { result ->
                    if (result.isSuccess) {
                        val list = result.getOrNull() ?: emptyList()
                        Log.d("ChapterDebug", "StudentHomeViewModel collected ${list.size} chapters")
                        chapters = list
                        isLoading = false
                        errorMessage = null
                        loadQuestionCounts(list)
                    } else {
                        fetchDirectly()
                    }
                }
        }

        if (user != null) {
            resultsJob = viewModelScope.launch {
                submissionRepository.getStudentSubmissionsRealtime(user.uid)
                    .catch {
                        Log.e("ChapterDebug", "Failed to load student submissions", it)
                    }
                    .collect { res ->
                        if (res.isSuccess) {
                            results = res.getOrNull() ?: emptyList()
                        }
                    }
            }
        }
    }

    private fun loadQuestionCounts(chapterList: List<Chapter>) {
        viewModelScope.launch {
            val counts = mutableMapOf<String, Int>()
            chapterList.forEach { chapter ->
                val qResult = questionRepository.getQuestionsByChapter(chapter.id)
                if (qResult.isSuccess) {
                    counts[chapter.id] = qResult.getOrNull()?.size ?: 0
                }
            }
            questionCounts = counts
        }
    }

    private suspend fun fetchDirectly() {
        val directResult = chapterRepository.getAllChapters()
        isLoading = false
        if (directResult.isSuccess) {
            val list = directResult.getOrNull() ?: emptyList()
            chapters = list
            errorMessage = null
            loadQuestionCounts(list)
        } else {
            errorMessage = "Failed to load chapters."
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentHomeScreen(
    onChapterClick: (String) -> Unit,
    onResultClick: (String) -> Unit = {},
    onLogoutClick: () -> Unit,
    viewModel: StudentHomeViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadStudentData()
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
        if (viewModel.isLoading && viewModel.chapters.isEmpty()) {
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
        } else if (viewModel.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Failed to load chapters.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadStudentData() }) {
                        Text("Retry")
                    }
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
                // Section 1: Available Chapters
                item {
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
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No chapters available.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                } else {
                    items(viewModel.chapters, key = { it.id }) { chapter ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = chapter.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (chapter.description.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    ExpandableText(
                                        text = chapter.description,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                val count = viewModel.questionCounts[chapter.id]
                                if (count != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "$count ${if (count == 1) "Question" else "Questions"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { onChapterClick(chapter.id) },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Open Chapter")
                                }
                            }
                        }
                    }
                }

                // Section 2: My Results
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "My Results",
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
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No exam results yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                } else {
                    items(viewModel.results, key = { it.id }) { result ->
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
                                            containerColor = if (result.status == "Completed")
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
