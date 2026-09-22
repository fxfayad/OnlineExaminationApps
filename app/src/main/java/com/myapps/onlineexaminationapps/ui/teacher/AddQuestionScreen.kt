package com.myapps.onlineexaminationapps.ui.teacher

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.myapps.onlineexaminationapps.firebase.QuestionRepository
import com.myapps.onlineexaminationapps.model.Question
import kotlinx.coroutines.launch

class AddQuestionViewModel : ViewModel() {
    private val repository = QuestionRepository()

    var questionId by mutableStateOf<String?>(null)
    var questionText by mutableStateOf("")
    var type by mutableStateOf("mcq") // "mcq" or "short"
    
    // MCQ fields
    var optionA by mutableStateOf("")
    var optionB by mutableStateOf("")
    var optionC by mutableStateOf("")
    var optionD by mutableStateOf("")
    var correctAnswer by mutableStateOf("A")

    // Short Question fields
    var answer by mutableStateOf("")

    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var successMessage by mutableStateOf<String?>(null)

    fun loadQuestion(chapterId: String, id: String) {
        questionId = id
        isLoading = true
        viewModelScope.launch {
            val result = repository.getQuestionById(chapterId, id)
            isLoading = false
            if (result.isSuccess) {
                val q = result.getOrNull()
                q?.let {
                    questionText = it.question
                    type = it.type
                    optionA = it.optionA
                    optionB = it.optionB
                    optionC = it.optionC
                    optionD = it.optionD
                    correctAnswer = it.correctAnswer
                    answer = it.answer
                }
            }
        }
    }

    fun saveQuestion(chapterId: String, onSuccess: () -> Unit) {
        if (questionText.isBlank()) {
            errorMessage = "Question cannot be empty"
            return
        }

        if (type == "mcq") {
            if (optionA.isBlank() || optionB.isBlank() || optionC.isBlank() || optionD.isBlank()) {
                errorMessage = "All options must be filled"
                return
            }
        } else {
            if (answer.isBlank()) {
                errorMessage = "Answer cannot be empty"
                return
            }
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            errorMessage = "User not logged in"
            return
        }

        isLoading = true
        errorMessage = null

        val question = Question(
            id = questionId ?: "",
            chapterId = chapterId,
            question = questionText,
            type = type,
            options = if (type == "mcq") listOf(optionA, optionB, optionC, optionD) else emptyList(),
            correctAnswer = if (type == "mcq") correctAnswer else answer,
            createdBy = currentUser.uid
        )

        viewModelScope.launch {
            val result = if (questionId == null) {
                repository.createQuestion(chapterId, question)
            } else {
                repository.updateQuestion(chapterId, question)
            }
            isLoading = false
            if (result.isSuccess) {
                successMessage = if (questionId == null) "Question created successfully" else "Question updated successfully"
                onSuccess()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Failed to save question"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddQuestionScreen(
    chapterId: String,
    questionId: String? = null,
    onBackClick: () -> Unit,
    onQuestionSaved: () -> Unit,
    viewModel: AddQuestionViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(chapterId, questionId) {
        questionId?.let { viewModel.loadQuestion(chapterId, it) }
    }

    LaunchedEffect(viewModel.successMessage) {
        viewModel.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (questionId == null) "Add Question" else "Edit Question") },
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
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Question Type", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                RadioButton(
                    selected = viewModel.type == "mcq",
                    onClick = { viewModel.type = "mcq" }
                )
                Text("MCQ", modifier = Modifier.padding(end = 16.dp))
                RadioButton(
                    selected = viewModel.type == "short",
                    onClick = { viewModel.type = "short" }
                )
                Text("Short Question")
            }

            OutlinedTextField(
                value = viewModel.questionText,
                onValueChange = { viewModel.questionText = it },
                label = { Text("Question") },
                modifier = Modifier.fillMaxWidth()
            )

            if (viewModel.type == "mcq") {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = viewModel.optionA,
                    onValueChange = { viewModel.optionA = it },
                    label = { Text("Option A") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = viewModel.optionB,
                    onValueChange = { viewModel.optionB = it },
                    label = { Text("Option B") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = viewModel.optionC,
                    onValueChange = { viewModel.optionC = it },
                    label = { Text("Option C") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = viewModel.optionD,
                    onValueChange = { viewModel.optionD = it },
                    label = { Text("Option D") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Correct Answer", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("A", "B", "C", "D").forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = viewModel.correctAnswer == option,
                                onClick = { viewModel.correctAnswer = option }
                            )
                            Text(option)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = viewModel.answer,
                    onValueChange = { viewModel.answer = it },
                    label = { Text("Answer") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (viewModel.errorMessage != null) {
                Text(
                    text = viewModel.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.saveQuestion(chapterId, onQuestionSaved) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isLoading
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (questionId == null) "Save Question" else "Save Changes")
                }
            }
        }
    }
}
