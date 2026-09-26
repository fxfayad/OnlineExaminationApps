package com.myapps.onlineexaminationapps.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.myapps.onlineexaminationapps.model.Question
import com.myapps.onlineexaminationapps.model.StudentAnswer
import kotlinx.coroutines.launch

class QuestionFeedViewModel : ViewModel() {
    private val chapterRepository = ChapterRepository()
    private val questionRepository = QuestionRepository()
    private val submissionRepository = SubmissionRepository()

    var chapters by mutableStateOf<List<Chapter>>(emptyList())
    var questions by mutableStateOf<List<Question>>(emptyList())
    var chapterMap by mutableStateOf<Map<String, String>>(emptyMap())
    var results by mutableStateOf<List<ExamResult>>(emptyList())
    var isLoading by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)
    var isSubmitting by mutableStateOf(false)

    // Maps questionId -> answer text
    val answersMap = mutableStateMapOf<String, String>()

    fun loadFeedData() {
        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                val subRes = submissionRepository.getStudentSubmissions(user.uid)
                if (subRes.isSuccess) {
                    results = subRes.getOrNull() ?: emptyList()
                }
            }

            val chapterRes = chapterRepository.getAllChapters()
            if (chapterRes.isSuccess) {
                chapters = chapterRes.getOrNull() ?: emptyList()
                chapterMap = chapters.associate { it.effectiveId to it.displayTitle }

                val allQ = mutableListOf<Question>()
                for (chap in chapters) {
                    val qRes = questionRepository.getQuestionsByChapter(chap.effectiveId)
                    if (qRes.isSuccess) {
                        allQ.addAll(qRes.getOrNull() ?: emptyList())
                    }
                }
                questions = allQ.sortedWith(compareBy<Question> { it.chapterNumber }.thenBy { it.questionNumber })
                isLoading = false
            } else {
                isLoading = false
                errorMessage = chapterRes.exceptionOrNull()?.message ?: "Failed to load questions feed."
            }
        }
    }

    fun setAnswer(questionId: String, answer: String) {
        answersMap[questionId] = answer
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionFeedScreen(
    onBackClick: () -> Unit,
    onChapterExamClick: (String) -> Unit,
    onResultClick: (String) -> Unit,
    viewModel: QuestionFeedViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadFeedData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Question Feed") },
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading questions feed...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (viewModel.errorMessage != null) {
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
                    Button(onClick = { viewModel.loadFeedData() }) {
                        Text("Retry")
                    }
                }
            } else if (viewModel.questions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No questions available in the feed.",
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
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "All Available Questions (${viewModel.questions.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Practice questions or click 'Open Exam' to submit full chapter tests.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    itemsIndexed(viewModel.questions, key = { _, q -> "q_feed_item_${q.effectiveId}" }) { index, question ->
                        val chapTitle = viewModel.chapterMap[question.chapterId] ?: "Chapter"
                        val submission = viewModel.results.find { it.chapterId == question.chapterId }
                        val currentAns = viewModel.answersMap[question.effectiveId] ?: ""

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // 1. Chapter Title & Question Type Badge Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = chapTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                text = if (question.isMcq) "MCQ" else "Short Question",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 2. Question Prompt
                                Text(
                                    text = "Q${index + 1}: ${question.effectiveQuestionText}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // 3. Options / Input (Strictly hiding correct answer before submission)
                                if (question.isMcq) {
                                    val options = question.effectiveOptions
                                    options.forEachIndexed { optIndex, optionText ->
                                        val optionPrefix = when (optIndex) {
                                            0 -> "A. "
                                            1 -> "B. "
                                            2 -> "C. "
                                            else -> "D. "
                                        }
                                        if (optionText.isNotBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                            ) {
                                                RadioButton(
                                                    selected = currentAns == optionText || currentAns == optionPrefix.trim().replace(".", ""),
                                                    onClick = {
                                                        viewModel.setAnswer(question.effectiveId, optionText)
                                                    },
                                                    enabled = !viewModel.isSubmitting
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "$optionPrefix$optionText",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = currentAns,
                                        onValueChange = { newText ->
                                            viewModel.setAnswer(question.effectiveId, newText)
                                        },
                                        label = { Text("Write your answer here...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        maxLines = 4,
                                        enabled = !viewModel.isSubmitting
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Spacer(modifier = Modifier.height(12.dp))

                                // 4. Marks & Action Button Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Marks: ${question.marks}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (submission != null) {
                                        OutlinedButton(
                                            onClick = { onResultClick(submission.id) }
                                        ) {
                                            Text("View Result", style = MaterialTheme.typography.labelLarge)
                                        }
                                    } else {
                                        Button(
                                            onClick = { onChapterExamClick(question.chapterId) }
                                        ) {
                                            Text("Open Exam", style = MaterialTheme.typography.labelLarge)
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
