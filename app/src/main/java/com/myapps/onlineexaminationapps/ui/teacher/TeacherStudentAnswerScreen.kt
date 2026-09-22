package com.myapps.onlineexaminationapps.ui.teacher

import android.util.Log
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
import kotlinx.coroutines.launch

class TeacherStudentAnswerViewModel : ViewModel() {
    private val chapterRepository = ChapterRepository()
    private val questionRepository = QuestionRepository()
    private val submissionRepository = SubmissionRepository()

    var chapters by mutableStateOf<List<Chapter>>(emptyList())
    var questionCounts by mutableStateOf<Map<String, Int>>(emptyMap())
    var submissionCounts by mutableStateOf<Map<String, Int>>(emptyMap())
    var isLoading by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)

    fun loadTeacherChapters() {
        val currentTeacherUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentTeacherUid == null) {
            isLoading = false
            errorMessage = "Teacher not logged in."
            return
        }

        isLoading = true
        errorMessage = null
        Log.d("TEACHER_ANSWER_DEBUG", "TeacherStudentAnswerViewModel.loadTeacherChapters for UID: $currentTeacherUid")

        viewModelScope.launch {
            val result = chapterRepository.getTeacherChapters(currentTeacherUid)
            isLoading = false
            if (result.isSuccess) {
                val list = result.getOrNull() ?: emptyList()
                Log.d("TEACHER_ANSWER_DEBUG", "Teacher chapters loaded count: ${list.size}")
                chapters = list
                loadCounts(list)
            } else {
                Log.e("TEACHER_ANSWER_DEBUG", "Failed to load teacher chapters", result.exceptionOrNull())
                errorMessage = result.exceptionOrNull()?.message ?: "Failed to load chapters."
            }
        }
    }

    private fun loadCounts(list: List<Chapter>) {
        viewModelScope.launch {
            val qCounts = mutableMapOf<String, Int>()
            val sCounts = mutableMapOf<String, Int>()
            list.forEach { chap ->
                val qRes = questionRepository.getQuestionsByChapter(chap.id)
                if (qRes.isSuccess) {
                    qCounts[chap.id] = qRes.getOrNull()?.size ?: 0
                }
                val subRes = submissionRepository.getSubmissionsForChapter(chap.id)
                if (subRes.isSuccess) {
                    sCounts[chap.id] = subRes.getOrNull()?.size ?: 0
                }
            }
            questionCounts = qCounts
            submissionCounts = sCounts
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherStudentAnswerScreen(
    onBackClick: () -> Unit,
    onChapterClick: (String) -> Unit,
    viewModel: TeacherStudentAnswerViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadTeacherChapters()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("View Student Answers") },
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
                        text = "Loading chapters...",
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
                        text = viewModel.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadTeacherChapters() }) {
                        Text("Retry")
                    }
                }
            } else if (viewModel.chapters.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Select Chapter to View Submissions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    items(viewModel.chapters, key = { it.id }) { chapter ->
                        val qCount = viewModel.questionCounts[chapter.id] ?: 0
                        val sCount = viewModel.submissionCounts[chapter.id] ?: 0

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChapterClick(chapter.id) },
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
                                    Text(
                                        text = chapter.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$qCount Questions",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "$sCount ${if (sCount == 1) "Student Submitted" else "Students Submitted"}",
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
