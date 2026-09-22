package com.myapps.onlineexaminationapps.ui.home

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

class StudentExamViewModel : ViewModel() {
    private val chapterRepository = ChapterRepository()
    private val questionRepository = QuestionRepository()
    private val submissionRepository = SubmissionRepository()

    var chapter by mutableStateOf<Chapter?>(null)
    var questions by mutableStateOf<List<Question>>(emptyList())
    var isLoading by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)
    var isSubmitting by mutableStateOf(false)

    // Maps questionId -> answer text / selected option
    val answersMap = mutableStateMapOf<String, String>()

    var unansweredQuestionNumbers by mutableStateOf<List<Int>>(emptyList())
    var validationError by mutableStateOf<String?>(null)

    fun loadChapterAndQuestions(chapterId: String) {
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            val chapterResult = chapterRepository.getChapterById(chapterId)
            if (chapterResult.isSuccess) {
                chapter = chapterResult.getOrNull()
                val questionsResult = questionRepository.getQuestionsByChapter(chapterId)
                isLoading = false
                if (questionsResult.isSuccess) {
                    val loadedQuestions = questionsResult.getOrNull() ?: emptyList()
                    // Sort deterministically by createdAt
                    questions = loadedQuestions.sortedBy { it.createdAt?.seconds ?: 0L }
                    // Initialize answers map if empty
                    questions.forEach { q ->
                        if (!answersMap.containsKey(q.id)) {
                            answersMap[q.id] = ""
                        }
                    }
                } else {
                    errorMessage = questionsResult.exceptionOrNull()?.message ?: "Failed to load questions."
                }
            } else {
                isLoading = false
                errorMessage = chapterResult.exceptionOrNull()?.message ?: "Failed to load chapter."
            }
        }
    }

    fun setAnswer(questionId: String, answer: String) {
        answersMap[questionId] = answer
        if (validationError != null) {
            validationError = null
            unansweredQuestionNumbers = emptyList()
        }
    }

    val answeredCount: Int
        get() = questions.count { (answersMap[it.id] ?: "").trim().isNotEmpty() }

    val totalMarks: Int
        get() = questions.sumOf { it.marks }

    fun validateAndProceed(): Boolean {
        val unanswered = mutableListOf<Int>()
        questions.forEachIndexed { index, question ->
            val ans = (answersMap[question.id] ?: "").trim()
            if (ans.isEmpty()) {
                unanswered.add(index + 1)
            }
        }

        if (unanswered.isNotEmpty()) {
            unansweredQuestionNumbers = unanswered
            validationError = "You have unanswered questions."
            return false
        }

        unansweredQuestionNumbers = emptyList()
        validationError = null
        return true
    }

    fun submitExam(
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onFailure("User not authenticated.")
            return
        }

        val currentChapter = chapter
        if (currentChapter == null) {
            onFailure("Chapter information missing.")
            return
        }

        if (!validateAndProceed()) {
            onFailure("You have unanswered questions.")
            return
        }

        isSubmitting = true

        viewModelScope.launch {
            // Check for duplicate submission first
            val checkResult = submissionRepository.hasStudentSubmittedChapter(user.uid, currentChapter.id)
            if (checkResult.isSuccess && checkResult.getOrDefault(false)) {
                isSubmitting = false
                val existingRes = submissionRepository.getStudentSubmissionForChapter(user.uid, currentChapter.id)
                val existingId = existingRes.getOrNull()?.id
                if (existingId != null) {
                    onSuccess(existingId)
                } else {
                    onFailure("You have already submitted this exam.")
                }
                return@launch
            }

            var mcqObtainedMarks = 0
            val mcqs = questions.filter { it.type == "mcq" }
            val shortQuestions = questions.filter { it.type == "short" }

            val mcqTotalMarks = mcqs.sumOf { it.marks }
            val shortQuestionTotalMarks = shortQuestions.sumOf { it.marks }
            val calcTotalMarks = questions.sumOf { it.marks }

            val studentAnswerList = questions.map { q ->
                val ans = (answersMap[q.id] ?: "").trim()
                if (q.type == "mcq") {
                    val isCorr = ans.equals(q.correctAnswer.trim(), ignoreCase = true)
                    val obtained = if (isCorr) q.marks else 0
                    if (isCorr) mcqObtainedMarks += q.marks
                    StudentAnswer(
                        chapterId = currentChapter.id,
                        questionId = q.id,
                        answer = ans,
                        questionType = "mcq",
                        marks = q.marks,
                        obtainedMarks = obtained,
                        isCorrect = isCorr
                    )
                } else {
                    StudentAnswer(
                        chapterId = currentChapter.id,
                        questionId = q.id,
                        answer = ans,
                        questionType = "short",
                        marks = q.marks,
                        obtainedMarks = 0,
                        isCorrect = null
                    )
                }
            }

            val status = if (shortQuestionTotalMarks > 0) "Pending Teacher Evaluation" else "Completed"

            val submissionObj = ExamResult(
                studentId = user.uid,
                chapterId = currentChapter.id,
                chapterName = currentChapter.name,
                totalQuestions = questions.size,
                totalMarks = calcTotalMarks,
                mcqTotalMarks = mcqTotalMarks,
                mcqObtainedMarks = mcqObtainedMarks,
                shortQuestionTotalMarks = shortQuestionTotalMarks,
                shortQuestionObtainedMarks = 0,
                obtainedMarks = mcqObtainedMarks,
                status = status
            )

            val submitResult = submissionRepository.submitExam(submissionObj, studentAnswerList)
            isSubmitting = false

            if (submitResult.isSuccess) {
                val submissionId = submitResult.getOrNull() ?: ""
                onSuccess(submissionId)
            } else {
                onFailure(submitResult.exceptionOrNull()?.message ?: "Failed to submit exam.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentChapterScreen(
    chapterId: String,
    onBackClick: () -> Unit,
    onNavigateToReview: () -> Unit,
    viewModel: StudentExamViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(chapterId) {
        viewModel.loadChapterAndQuestions(chapterId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.chapter?.name ?: "Chapter Questions") },
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
                        text = "Loading questions...",
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
                        text = "Failed to load questions.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadChapterAndQuestions(chapterId) }) {
                        Text("Retry")
                    }
                }
            } else if (viewModel.questions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No questions available for this chapter.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header Section: Chapter details & Progress
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                viewModel.chapter?.let { chap ->
                                    Text(
                                        text = chap.name,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (chap.description.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = chap.description,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${viewModel.questions.size} Questions",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Total Marks: ${viewModel.totalMarks}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Progress Bar
                                val progress = if (viewModel.questions.isNotEmpty()) {
                                    viewModel.answeredCount.toFloat() / viewModel.questions.size
                                } else 0f

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Answered: ${viewModel.answeredCount} / ${viewModel.questions.size}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${(progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider()
                            }
                        }

                        // Validation Banner if unanswered
                        if (viewModel.validationError != null) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = viewModel.validationError!!,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (viewModel.unansweredQuestionNumbers.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Unanswered Questions: ${viewModel.unansweredQuestionNumbers.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Questions List
                        itemsIndexed(viewModel.questions, key = { _, q -> q.id }) { index, question ->
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
                                        Text(
                                            text = "Question ${index + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${question.marks} ${if (question.marks == 1) "mark" else "marks"}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = question.question,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    val currentAns = viewModel.answersMap[question.id] ?: ""

                                    if (question.type == "mcq") {
                                        question.options.forEach { optionText ->
                                            if (optionText.isNotBlank()) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                ) {
                                                    RadioButton(
                                                        selected = currentAns == optionText,
                                                        onClick = {
                                                            viewModel.setAnswer(question.id, optionText)
                                                        }
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = optionText,
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // Short Question Form
                                        OutlinedTextField(
                                            value = currentAns,
                                            onValueChange = { newText ->
                                                viewModel.setAnswer(question.id, newText)
                                            },
                                            label = { Text("Type your answer here...") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 3,
                                            maxLines = 6
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Submit Exam Bar
                    Surface(
                        tonalElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            Button(
                                onClick = {
                                    if (viewModel.validateAndProceed()) {
                                        onNavigateToReview()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Please answer all questions before submitting.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Submit Exam", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
