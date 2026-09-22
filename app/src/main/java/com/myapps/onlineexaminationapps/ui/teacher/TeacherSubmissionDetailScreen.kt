package com.myapps.onlineexaminationapps.ui.teacher

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.myapps.onlineexaminationapps.firebase.ChapterRepository
import com.myapps.onlineexaminationapps.firebase.QuestionRepository
import com.myapps.onlineexaminationapps.firebase.SubmissionRepository
import com.myapps.onlineexaminationapps.model.ExamResult
import com.myapps.onlineexaminationapps.model.Question
import com.myapps.onlineexaminationapps.model.StudentAnswer
import kotlinx.coroutines.launch

class TeacherSubmissionDetailViewModel : ViewModel() {
    private val chapterRepository = ChapterRepository()
    private val questionRepository = QuestionRepository()
    private val submissionRepository = SubmissionRepository()

    var submission by mutableStateOf<ExamResult?>(null)
    var studentName by mutableStateOf("Student")
    var questionsMap by mutableStateOf<Map<String, Question>>(emptyMap())
    var studentAnswers by mutableStateOf<List<StudentAnswer>>(emptyList())

    // Editable state for short question marks: questionId -> String (entered marks)
    val shortQuestionMarksInput = mutableStateMapOf<String, String>()

    var isLoading by mutableStateOf(true)
    var isSaving by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var validationError by mutableStateOf<String?>(null)

    fun loadSubmissionData(submissionId: String) {
        val currentTeacherUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentTeacherUid == null) {
            isLoading = false
            errorMessage = "Teacher not logged in."
            return
        }

        isLoading = true
        errorMessage = null
        Log.d("TEACHER_ANSWER_DEBUG", "loadSubmissionData for submissionId: $submissionId, Teacher UID: $currentTeacherUid")

        viewModelScope.launch {
            val subRes = submissionRepository.getStudentResult(submissionId)
            if (subRes.isSuccess) {
                val sub = subRes.getOrNull()
                submission = sub

                if (sub == null) {
                    isLoading = false
                    errorMessage = "Submission not found."
                    return@launch
                }

                // Security Check: Verify chapter belongs to current teacher
                val chapRes = chapterRepository.getChapterById(sub.chapterId)
                if (chapRes.isSuccess) {
                    val chap = chapRes.getOrNull()
                    if (chap != null && chap.createdBy != currentTeacherUid) {
                        isLoading = false
                        Log.e("TEACHER_ANSWER_DEBUG", "Security Violation: Chapter createdBy '${chap.createdBy}' != Teacher UID '$currentTeacherUid'")
                        errorMessage = "Unauthorized: You do not have permission to evaluate this submission."
                        return@launch
                    }
                }

                // Load student name
                val nameRes = submissionRepository.getStudentName(sub.studentId)
                studentName = nameRes.getOrDefault("Student")

                // Load questions for this chapter
                val qRes = questionRepository.getQuestionsByChapter(sub.chapterId)
                if (qRes.isSuccess) {
                    val qList = qRes.getOrNull() ?: emptyList()
                    questionsMap = qList.associateBy { it.id }
                }

                // Load student answers
                val ansRes = submissionRepository.getStudentAnswersForSubmission(submissionId)
                isLoading = false
                if (ansRes.isSuccess) {
                    val ansList = ansRes.getOrNull() ?: emptyList()
                    studentAnswers = ansList
                    Log.d("TEACHER_ANSWER_DEBUG", "Loaded ${ansList.size} student answers for submission $submissionId")

                    // Populate short question marks input
                    ansList.forEach { ans ->
                        if (ans.questionType == "short" || questionsMap[ans.questionId]?.type == "short") {
                            shortQuestionMarksInput[ans.id] = ans.obtainedMarks.toString()
                        }
                    }
                } else {
                    errorMessage = ansRes.exceptionOrNull()?.message ?: "Failed to load student answers."
                }
            } else {
                isLoading = false
                errorMessage = subRes.exceptionOrNull()?.message ?: "Failed to load submission."
            }
        }
    }

    fun saveEvaluation(onSuccess: () -> Unit) {
        val currentSub = submission
        if (currentSub == null) {
            errorMessage = "Submission data missing."
            return
        }

        // Validate all short question mark inputs
        var validationFailed = false
        var valMsg = ""

        val updatedAnswers = studentAnswers.map { ans ->
            val question = questionsMap[ans.questionId]
            val isShort = ans.questionType == "short" || question?.type == "short"

            if (isShort) {
                val inputStr = shortQuestionMarksInput[ans.id]?.trim() ?: "0"
                val marksInt = inputStr.toIntOrNull()
                val maxMarks = if (ans.marks > 0) ans.marks else (question?.marks ?: 5)

                if (marksInt == null || marksInt < 0 || marksInt > maxMarks) {
                    validationFailed = true
                    valMsg = "Marks for short questions must be between 0 and $maxMarks."
                    ans
                } else {
                    ans.copy(
                        obtainedMarks = marksInt,
                        isCorrect = (marksInt == maxMarks)
                    )
                }
            } else {
                ans
            }
        }

        if (validationFailed) {
            validationError = valMsg
            return
        }

        validationError = null
        isSaving = true

        viewModelScope.launch {
            val shortAnswers = updatedAnswers.filter {
                it.questionType == "short" || questionsMap[it.questionId]?.type == "short"
            }

            val shortObtainedMarks = shortAnswers.sumOf { it.obtainedMarks }
            val mcqObtainedMarks = currentSub.mcqObtainedMarks
            val totalObtainedMarks = mcqObtainedMarks + shortObtainedMarks

            // Check if all short questions have been evaluated
            val isFullyEvaluated = shortAnswers.all {
                val inputStr = shortQuestionMarksInput[it.id]?.trim()
                !inputStr.isNullOrEmpty()
            }

            val saveRes = submissionRepository.saveEvaluation(
                submissionId = currentSub.id,
                answers = updatedAnswers,
                shortQuestionObtainedMarks = shortObtainedMarks,
                totalObtainedMarks = totalObtainedMarks,
                isFullyEvaluated = isFullyEvaluated
            )

            isSaving = false
            if (saveRes.isSuccess) {
                Log.d("TEACHER_ANSWER_DEBUG", "saveEvaluation completed successfully!")
                // Refresh local state
                submission = currentSub.copy(
                    shortQuestionObtainedMarks = shortObtainedMarks,
                    obtainedMarks = totalObtainedMarks,
                    status = if (isFullyEvaluated) "Completed" else "Pending Teacher Evaluation"
                )
                studentAnswers = updatedAnswers
                onSuccess()
            } else {
                errorMessage = saveRes.exceptionOrNull()?.message ?: "Failed to save evaluation."
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherSubmissionDetailScreen(
    submissionId: String,
    onBackClick: () -> Unit,
    viewModel: TeacherSubmissionDetailViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(submissionId) {
        viewModel.loadSubmissionData(submissionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Evaluate Submission") },
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
                        text = "Loading submission answers...",
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
                    Button(onClick = { viewModel.loadSubmissionData(submissionId) }) {
                        Text("Retry")
                    }
                }
            } else {
                val sub = viewModel.submission
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Student: ${viewModel.studentName}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (sub != null && sub.chapterName.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Chapter: ${sub.chapterName}",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Current Score: ${sub?.obtainedMarks ?: 0} / ${sub?.totalMarks ?: 0}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Status: ${sub?.status ?: ""}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (sub?.status == "Completed") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }

                        if (viewModel.validationError != null) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = viewModel.validationError!!,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        // Answer Cards
                        itemsIndexed(viewModel.studentAnswers, key = { _, a -> a.id }) { index, answer ->
                            val question = viewModel.questionsMap[answer.questionId]
                            val isShort = answer.questionType == "short" || question?.type == "short"
                            val maxMarks = if (answer.marks > 0) answer.marks else (question?.marks ?: 1)

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
                                            text = "Question ${index + 1} (${if (isShort) "Short Question" else "MCQ"})",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Max Marks: $maxMarks",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = question?.question ?: "Question Text",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "Student Answer:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (answer.answer.isBlank()) "No answer provided" else answer.answer,
                                        style = MaterialTheme.typography.bodyLarge
                                    )

                                    if (!isShort) {
                                        // MCQ
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Correct Answer: ${question?.correctAnswer ?: ""}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Obtained Marks: ${answer.obtainedMarks} / $maxMarks",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            AssistChip(
                                                onClick = {},
                                                label = { Text(if (answer.isCorrect == true) "Correct" else "Incorrect") },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = if (answer.isCorrect == true)
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    else
                                                        MaterialTheme.colorScheme.errorContainer
                                                )
                                            )
                                        }
                                    } else {
                                        // Short Question Evaluation Input
                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider()
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Text(
                                            text = "Evaluate Short Question",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        OutlinedTextField(
                                            value = viewModel.shortQuestionMarksInput[answer.id] ?: "",
                                            onValueChange = { input ->
                                                viewModel.shortQuestionMarksInput[answer.id] = input
                                                if (viewModel.validationError != null) viewModel.validationError = null
                                            },
                                            label = { Text("Obtained Marks (0 to $maxMarks)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            enabled = !viewModel.isSaving
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Save Evaluation Bottom Bar
                    Surface(
                        tonalElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            Button(
                                onClick = {
                                    viewModel.saveEvaluation(
                                        onSuccess = {
                                            Toast.makeText(context, "Evaluation saved successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !viewModel.isSaving
                            ) {
                                if (viewModel.isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Save Evaluation", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
