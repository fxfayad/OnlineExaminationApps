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
import com.google.firebase.firestore.FirebaseFirestore
import com.myapps.onlineexaminationapps.firebase.AnswerRepository
import com.myapps.onlineexaminationapps.firebase.ChapterRepository
import com.myapps.onlineexaminationapps.firebase.QuestionRepository
import com.myapps.onlineexaminationapps.model.Answer
import com.myapps.onlineexaminationapps.model.Chapter
import com.myapps.onlineexaminationapps.model.Question
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class StudentQuestionsViewModel : ViewModel() {
    private val chapterRepository = ChapterRepository()
    private val questionRepository = QuestionRepository()
    private val answerRepository = AnswerRepository()

    var chapter by mutableStateOf<Chapter?>(null)
    var questions by mutableStateOf<List<Question>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var isSubmitting by mutableStateOf(false)

    // Maps question index to selected/written answer
    val studentAnswers = mutableStateMapOf<Int, String>()

    fun loadQuestions(chapterId: String) {
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            val chapResult = chapterRepository.getChapterById(chapterId)
            if (chapResult.isSuccess) {
                chapter = chapResult.getOrNull()
                val qResult = questionRepository.getQuestions(chapterId)
                isLoading = false
                if (qResult.isSuccess) {
                    questions = qResult.getOrNull() ?: emptyList()
                    questions.forEachIndexed { index, _ ->
                        if (!studentAnswers.containsKey(index)) {
                            studentAnswers[index] = ""
                        }
                    }
                } else {
                    errorMessage = qResult.exceptionOrNull()?.message ?: "Failed to load questions."
                }
            } else {
                isLoading = false
                errorMessage = chapResult.exceptionOrNull()?.message ?: "Failed to load chapter."
            }
        }
    }

    private suspend fun fetchStudentName(uid: String): String = suspendCoroutine { continuation ->
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name")
                val email = doc.getString("email")
                val finalName = if (!name.isNullOrBlank()) name else if (!email.isNullOrBlank()) email else "Student"
                continuation.resume(finalName)
            }
            .addOnFailureListener {
                continuation.resume("Student")
            }
    }

    fun submitAllAnswers(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            onFailure("User session expired. Please log in again.")
            return
        }

        // Check if all questions are answered
        for (i in questions.indices) {
            val ans = studentAnswers[i] ?: ""
            if (ans.trim().isEmpty()) {
                onFailure("Please answer all questions before submitting.")
                return
            }
        }

        isSubmitting = true
        viewModelScope.launch {
            try {
                val studentName = fetchStudentName(uid)
                val submittedAt = System.currentTimeMillis()
                var hasError = false
                var errorMsg = "Failed to submit answers."

                for (i in questions.indices) {
                    val q = questions[i]
                    val submittedAns = (studentAnswers[i] ?: "").trim()
                    
                    val corrAns = if (q.type == "mcq") q.correctAnswer.trim() else q.answer.trim()
                    val isCorrect = submittedAns.equals(corrAns, ignoreCase = true)

                    val answerObj = Answer(
                        id = "",
                        studentId = uid,
                        studentName = studentName,
                        chapterId = chapter?.id ?: "",
                        questionId = q.id,
                        questionText = q.question,
                        answer = submittedAns,
                        correctAnswer = corrAns,
                        isCorrect = isCorrect,
                        submittedAt = submittedAt
                    )

                    val result = answerRepository.submitAnswer(answerObj)
                    if (result.isFailure) {
                        hasError = true
                        errorMsg = result.exceptionOrNull()?.message ?: "Failed to submit answers."
                    }
                }

                isSubmitting = false
                if (!hasError) {
                    onSuccess()
                } else {
                    onFailure(errorMsg)
                }
            } catch (e: Exception) {
                isSubmitting = false
                onFailure(e.message ?: "An unexpected error occurred during submission.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentQuestionsScreen(
    chapterId: String,
    onBackClick: () -> Unit,
    onSubmitSuccess: () -> Unit,
    viewModel: StudentQuestionsViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(chapterId) {
        viewModel.loadQuestions(chapterId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.chapter?.name ?: "Questions") },
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
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadQuestions(chapterId) }) {
                        Text("Retry")
                    }
                }
            } else if (viewModel.questions.isEmpty()) {
                Text(
                    text = "No questions available for this chapter.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            viewModel.chapter?.let {
                                Text(
                                    text = "Chapter: ${it.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                if (it.description.isNotEmpty()) {
                                    Text(
                                        text = it.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                }
                                HorizontalDivider()
                            }
                        }

                        itemsIndexed(viewModel.questions) { index, question ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Question ${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = question.question,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    if (question.type == "mcq") {
                                        val currentSelection = viewModel.studentAnswers[index] ?: ""
                                        val options = listOf(
                                            "A" to question.optionA,
                                            "B" to question.optionB,
                                            "C" to question.optionC,
                                            "D" to question.optionD
                                        )

                                        options.forEach { (label, optionText) ->
                                            if (optionText.isNotEmpty()) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                ) {
                                                    RadioButton(
                                                        selected = currentSelection == label,
                                                        onClick = { viewModel.studentAnswers[index] = label }
                                                    )
                                                    Text(
                                                        text = "$label: $optionText",
                                                        modifier = Modifier.padding(start = 8.dp),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        val currentText = viewModel.studentAnswers[index] ?: ""
                                        OutlinedTextField(
                                            value = currentText,
                                            onValueChange = { viewModel.studentAnswers[index] = it },
                                            label = { Text("Write your answer") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            Button(
                                onClick = {
                                    viewModel.submitAllAnswers(
                                        onSuccess = {
                                            Toast.makeText(context, "Answers submitted successfully.", Toast.LENGTH_SHORT).show()
                                            onSubmitSuccess()
                                        },
                                        onFailure = { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !viewModel.isSubmitting
                            ) {
                                if (viewModel.isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Submit Answers")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
