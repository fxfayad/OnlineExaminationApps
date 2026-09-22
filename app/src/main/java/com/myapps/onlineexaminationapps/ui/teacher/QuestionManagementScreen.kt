package com.myapps.onlineexaminationapps.ui.teacher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapps.onlineexaminationapps.firebase.ChapterRepository
import com.myapps.onlineexaminationapps.firebase.QuestionRepository
import com.myapps.onlineexaminationapps.model.Chapter
import com.myapps.onlineexaminationapps.model.Question
import kotlinx.coroutines.launch

class QuestionManagementViewModel : ViewModel() {
    private val chapterRepository = ChapterRepository()
    private val questionRepository = QuestionRepository()

    var chapter by mutableStateOf<Chapter?>(null)
    var questions by mutableStateOf<List<Question>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun loadData(chapterId: String) {
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            val chapterResult = chapterRepository.getChapterById(chapterId)
            if (chapterResult.isSuccess) {
                chapter = chapterResult.getOrNull()
                val questionsResult = questionRepository.getQuestions(chapterId)
                isLoading = false
                if (questionsResult.isSuccess) {
                    questions = questionsResult.getOrNull() ?: emptyList()
                } else {
                    errorMessage = questionsResult.exceptionOrNull()?.message ?: "Failed to load questions"
                }
            } else {
                isLoading = false
                errorMessage = chapterResult.exceptionOrNull()?.message ?: "Failed to load chapter"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionManagementScreen(
    chapterId: String,
    onBackClick: () -> Unit,
    onAddQuestionClick: (String) -> Unit,
    onEditChapterClick: (String) -> Unit,
    onEditQuestionClick: (String, String) -> Unit,
    viewModel: QuestionManagementViewModel = viewModel()
) {
    LaunchedEffect(chapterId) {
        viewModel.loadData(chapterId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Question Management") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddQuestionClick(chapterId) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Question")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (viewModel.errorMessage != null) {
                Text(
                    text = viewModel.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    viewModel.chapter?.let { chapter ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = chapter.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { onEditChapterClick(chapter.id) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Chapter")
                                    }
                                }
                                if (chapter.description.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = chapter.description, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        HorizontalDivider()
                    }

                    if (viewModel.questions.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "No questions found.")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(viewModel.questions) { question ->
                                QuestionItem(
                                    question = question,
                                    onEditClick = { onEditQuestionClick(chapterId, question.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestionItem(question: Question, onEditClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = question.question, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Question")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Type: ${if (question.type == "mcq") "MCQ" else "Short Question"}", style = MaterialTheme.typography.bodySmall)
            
            if (question.type == "mcq") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "A: ${question.optionA}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "B: ${question.optionB}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "C: ${question.optionC}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "D: ${question.optionD}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Correct Answer: ${question.correctAnswer}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Answer: ${question.answer}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
