package com.myapps.onlineexaminationapps.ui.teacher

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
import com.myapps.onlineexaminationapps.firebase.AnswerRepository
import com.myapps.onlineexaminationapps.firebase.ChapterRepository
import com.myapps.onlineexaminationapps.model.Answer
import com.myapps.onlineexaminationapps.model.Chapter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ViewStudentAnswersViewModel : ViewModel() {
    private val answerRepository = AnswerRepository()
    private val chapterRepository = ChapterRepository()

    var allAnswers by mutableStateOf<List<Answer>>(emptyList())
    var chapters by mutableStateOf<List<Chapter>>(emptyList())
    var selectedChapterId by mutableStateOf("All")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun loadData() {
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            val chaptersResult = chapterRepository.getChapters()
            if (chaptersResult.isSuccess) {
                chapters = chaptersResult.getOrNull() ?: emptyList()
            }
            
            val answersResult = answerRepository.getStudentAnswers()
            isLoading = false
            if (answersResult.isSuccess) {
                allAnswers = answersResult.getOrNull() ?: emptyList()
            } else {
                errorMessage = answersResult.exceptionOrNull()?.message ?: "Failed to load answers"
            }
        }
    }

    val filteredAnswers: List<Answer>
        get() {
            return if (selectedChapterId == "All") {
                allAnswers
            } else {
                allAnswers.filter { it.chapterId == selectedChapterId }
            }
        }

    fun getChapterName(chapterId: String): String {
        return chapters.find { it.id == chapterId }?.name ?: "Unknown Chapter"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewStudentAnswersScreen(
    onBackClick: () -> Unit,
    viewModel: ViewStudentAnswersViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    var expanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Student Submissions") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Filter by Chapter:", fontWeight = FontWeight.Bold)
                Box {
                    val selectedLabel = if (viewModel.selectedChapterId == "All") "All Chapters" else viewModel.getChapterName(viewModel.selectedChapterId)
                    Button(onClick = { expanded = true }) {
                        Text(selectedLabel)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("All Chapters") },
                            onClick = {
                                viewModel.selectedChapterId = "All"
                                expanded = false
                            }
                        )
                        viewModel.chapters.forEach { chapter ->
                            DropdownMenuItem(
                                text = { Text(chapter.name) },
                                onClick = {
                                    viewModel.selectedChapterId = chapter.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Box(modifier = Modifier.fillMaxSize()) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (viewModel.errorMessage != null) {
                    Text(
                        text = viewModel.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                } else if (viewModel.filteredAnswers.isEmpty()) {
                    Text(
                        text = "No submissions found.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(viewModel.filteredAnswers) { ans ->
                            val timeStr = try {
                                dateFormat.format(Date(ans.submittedAt))
                            } catch (e: Exception) {
                                ""
                            }
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
                                            text = ans.studentName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = if (ans.isCorrect) "Correct" else "Wrong",
                                            fontWeight = FontWeight.Bold,
                                            color = if (ans.isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Chapter: ${viewModel.getChapterName(ans.chapterId)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = "Question: ${ans.questionText}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "Student Answer: ${ans.answer}", style = MaterialTheme.typography.bodyMedium)
                                    Text(text = "Correct Answer: ${ans.correctAnswer}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                                    
                                    if (timeStr.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Submitted at: $timeStr",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
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
