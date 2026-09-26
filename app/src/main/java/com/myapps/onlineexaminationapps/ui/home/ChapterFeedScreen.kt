package com.myapps.onlineexaminationapps.ui.home

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HourglassTop
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
import com.myapps.onlineexaminationapps.firebase.ChapterRepository
import com.myapps.onlineexaminationapps.firebase.QuestionRepository
import com.myapps.onlineexaminationapps.firebase.SubmissionRepository
import com.myapps.onlineexaminationapps.model.Chapter
import com.myapps.onlineexaminationapps.model.ExamResult
import com.myapps.onlineexaminationapps.ui.components.ExpandableText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ChapterFeedViewModel : ViewModel() {
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

    fun loadChapterFeedData() {
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

        chaptersJob = viewModelScope.launch {
            chapterRepository.getAllChaptersRealtime()
                .catch { e ->
                    Log.e("ChapterFeed", "Error fetching chapters realtime", e)
                    fetchDirectly()
                }
                .collect { res ->
                    isLoading = false
                    if (res.isSuccess) {
                        val list = res.getOrNull() ?: emptyList()
                        chapters = list
                        errorMessage = null
                        loadQuestionCounts(list)
                    } else {
                        fetchDirectly()
                    }
                }
        }

        resultsJob = viewModelScope.launch {
            submissionRepository.getStudentSubmissionsRealtime(user.uid)
                .catch {
                    Log.e("ChapterFeed", "Error loading submissions", it)
                }
                .collect { res ->
                    if (res.isSuccess) {
                        results = res.getOrNull() ?: emptyList()
                    }
                }
        }
    }

    private fun loadQuestionCounts(chapterList: List<Chapter>) {
        viewModelScope.launch {
            val counts = mutableMapOf<String, Int>()
            chapterList.forEach { chapter ->
                val qResult = questionRepository.getQuestionsByChapter(chapter.effectiveId)
                if (qResult.isSuccess) {
                    counts[chapter.effectiveId] = qResult.getOrNull()?.size ?: 0
                }
            }
            questionCounts = counts
        }
    }

    private suspend fun fetchDirectly() {
        val directResult = chapterRepository.getAllChapters()
        isLoading = false
        if (directResult.isSuccess) {
            chapters = directResult.getOrNull() ?: emptyList()
            errorMessage = null
            loadQuestionCounts(chapters)
        } else {
            errorMessage = "Failed to load chapters due to network or Firestore error."
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterFeedScreen(
    onBackClick: () -> Unit,
    onChapterClick: (String) -> Unit,
    onResultClick: (String) -> Unit,
    viewModel: ChapterFeedViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadChapterFeedData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chapter Feed") },
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
            if (viewModel.isLoading && viewModel.chapters.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading chapter feed...",
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
                    Button(onClick = { viewModel.loadChapterFeedData() }) {
                        Text("Retry")
                    }
                }
            } else if (viewModel.chapters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No chapters available in the feed.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "All Available Chapters (${viewModel.chapters.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Select any chapter to start its test exam or review your result.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    items(viewModel.chapters, key = { "chap_feed_${it.effectiveId}" }) { chapter ->
                        val submission = viewModel.results.find { it.chapterId == chapter.effectiveId }
                        val count = viewModel.questionCounts[chapter.effectiveId] ?: 0

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (submission != null) {
                                        onResultClick(submission.id)
                                    } else {
                                        onChapterClick(chapter.effectiveId)
                                    }
                                },
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
                                    if (submission == null) {
                                        AssistChip(
                                            onClick = { onChapterClick(chapter.effectiveId) },
                                            label = { Text("Available Exam") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.HourglassTop,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                    } else {
                                        AssistChip(
                                            onClick = { onResultClick(submission.id) },
                                            label = { Text("Result Ready") },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                                            )
                                        )
                                    }
                                }

                                if (chapter.description.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    ExpandableText(
                                        text = chapter.description,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "$count ${if (count == 1) "Question" else "Questions"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    if (submission != null) {
                                        OutlinedButton(
                                            onClick = { onResultClick(submission.id) }
                                        ) {
                                            Text("View Result")
                                        }
                                    } else {
                                        Button(
                                            onClick = { onChapterClick(chapter.effectiveId) }
                                        ) {
                                            Text("Start Chapter Exam")
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
