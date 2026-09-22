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
import com.myapps.onlineexaminationapps.firebase.SubmissionRepository
import com.myapps.onlineexaminationapps.model.Chapter
import com.myapps.onlineexaminationapps.model.ExamResult
import kotlinx.coroutines.launch

class TeacherSubmissionListViewModel : ViewModel() {
    private val chapterRepository = ChapterRepository()
    private val submissionRepository = SubmissionRepository()

    var chapter by mutableStateOf<Chapter?>(null)
    var submissions by mutableStateOf<List<ExamResult>>(emptyList())
    var studentNames by mutableStateOf<Map<String, String>>(emptyMap())
    var isLoading by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)

    fun loadSubmissions(chapterId: String) {
        val currentTeacherUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentTeacherUid == null) {
            isLoading = false
            errorMessage = "Teacher not logged in."
            return
        }

        isLoading = true
        errorMessage = null
        Log.d("TEACHER_ANSWER_DEBUG", "TeacherSubmissionListViewModel.loadSubmissions for chapterId: $chapterId, Teacher UID: $currentTeacherUid")

        viewModelScope.launch {
            val chapterRes = chapterRepository.getChapterById(chapterId)
            if (chapterRes.isSuccess) {
                val chap = chapterRes.getOrNull()
                chapter = chap

                // Security Check: Verify chapter belongs to current teacher
                if (chap != null && chap.createdBy != currentTeacherUid) {
                    isLoading = false
                    Log.e("TEACHER_ANSWER_DEBUG", "Security Violation: Chapter createdBy '${chap.createdBy}' != Teacher UID '$currentTeacherUid'")
                    errorMessage = "Unauthorized: You do not have permission to view submissions for this chapter."
                    return@launch
                }

                val subRes = submissionRepository.getSubmissionsForChapter(chapterId)
                isLoading = false
                if (subRes.isSuccess) {
                    val list = subRes.getOrNull() ?: emptyList()
                    Log.d("TEACHER_ANSWER_DEBUG", "Submissions found for chapterId $chapterId: ${list.size}")
                    submissions = list
                    loadStudentNames(list)
                } else {
                    errorMessage = subRes.exceptionOrNull()?.message ?: "Failed to load submissions."
                }
            } else {
                isLoading = false
                errorMessage = chapterRes.exceptionOrNull()?.message ?: "Failed to load chapter info."
            }
        }
    }

    private fun loadStudentNames(list: List<ExamResult>) {
        viewModelScope.launch {
            val names = mutableMapOf<String, String>()
            list.forEach { sub ->
                if (!names.containsKey(sub.studentId)) {
                    val nameRes = submissionRepository.getStudentName(sub.studentId)
                    names[sub.studentId] = nameRes.getOrDefault("Student")
                }
            }
            studentNames = names
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherSubmissionListScreen(
    chapterId: String,
    onBackClick: () -> Unit,
    onSubmissionClick: (String) -> Unit,
    viewModel: TeacherSubmissionListViewModel = viewModel()
) {
    LaunchedEffect(chapterId) {
        viewModel.loadSubmissions(chapterId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.chapter?.name ?: "Student Submissions") },
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
                        text = "Loading submissions...",
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
                    Button(onClick = { viewModel.loadSubmissions(chapterId) }) {
                        Text("Retry")
                    }
                }
            } else if (viewModel.submissions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No Submissions Found",
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
                            text = "${viewModel.submissions.size} Submissions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    items(viewModel.submissions, key = { it.id }) { sub ->
                        val studentName = viewModel.studentNames[sub.studentId] ?: "Student"
                        val isCompleted = sub.status == "Completed"

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSubmissionClick(sub.id) },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = studentName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    AssistChip(
                                        onClick = { onSubmissionClick(sub.id) },
                                        label = { Text(sub.status) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (isCompleted)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else
                                                MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Marks: ${sub.obtainedMarks} / ${sub.totalMarks}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${sub.totalQuestions} Questions",
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
