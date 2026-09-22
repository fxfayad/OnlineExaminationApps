package com.myapps.onlineexaminationapps.ui.teacher

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.myapps.onlineexaminationapps.model.Chapter
import com.myapps.onlineexaminationapps.model.Question
import kotlinx.coroutines.launch

class CreateQuestionViewModel : ViewModel() {
    private val chapterRepository = ChapterRepository()
    private val questionRepository = QuestionRepository()

    var teacherChapters by mutableStateOf<List<Chapter>>(emptyList())
    var selectedChapter by mutableStateOf<Chapter?>(null)
    var isChaptersLoading by mutableStateOf(true)

    var questionText by mutableStateOf("")
    var type by mutableStateOf("mcq") // "mcq" or "short"

    // MCQ fields
    var optionA by mutableStateOf("")
    var optionB by mutableStateOf("")
    var optionC by mutableStateOf("")
    var optionD by mutableStateOf("")
    var selectedCorrectOptionIndex by mutableStateOf<Int?>(null) // 0 for A, 1 for B, 2 for C, 3 for D

    // Common field
    var marksText by mutableStateOf("1")

    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var successMessage by mutableStateOf<String?>(null)

    fun loadTeacherChapters(preselectedChapterId: String? = null) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        isChaptersLoading = true
        viewModelScope.launch {
            val result = chapterRepository.getTeacherChapters(uid)
            isChaptersLoading = false
            if (result.isSuccess) {
                teacherChapters = result.getOrNull() ?: emptyList()
                if (preselectedChapterId != null) {
                    selectedChapter = teacherChapters.find { it.id == preselectedChapterId }
                } else if (teacherChapters.isNotEmpty() && selectedChapter == null) {
                    selectedChapter = teacherChapters.first()
                }
            }
        }
    }

    fun saveQuestion(onSuccess: () -> Unit) {
        val chapter = selectedChapter
        if (chapter == null) {
            errorMessage = "Please select a chapter."
            return
        }

        val trimmedQuestion = questionText.trim()
        if (trimmedQuestion.isEmpty()) {
            errorMessage = "Question cannot be empty."
            return
        }

        val marksInt = marksText.trim().toIntOrNull()
        if (marksInt == null || marksInt <= 0) {
            errorMessage = "Marks must be greater than 0."
            return
        }

        val optionsList = mutableListOf<String>()
        var correctAnswerStr = ""

        if (type == "mcq") {
            val a = optionA.trim()
            val b = optionB.trim()
            val c = optionC.trim()
            val d = optionD.trim()

            if (a.isEmpty() || b.isEmpty() || c.isEmpty() || d.isEmpty()) {
                errorMessage = "Please enter all four options."
                return
            }

            optionsList.addAll(listOf(a, b, c, d))

            val correctIdx = selectedCorrectOptionIndex
            if (correctIdx == null || correctIdx !in 0..3) {
                errorMessage = "Please select the correct answer."
                return
            }
            correctAnswerStr = optionsList[correctIdx]
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            errorMessage = "User not logged in."
            return
        }

        isLoading = true
        errorMessage = null

        val question = Question(
            chapterId = chapter.id,
            question = trimmedQuestion,
            type = type,
            options = optionsList,
            correctAnswer = correctAnswerStr,
            marks = marksInt,
            createdBy = currentUser.uid
        )

        viewModelScope.launch {
            val result = questionRepository.createQuestion(question)
            isLoading = false
            if (result.isSuccess) {
                successMessage = "Question created successfully"
                // Clear form fields
                questionText = ""
                optionA = ""
                optionB = ""
                optionC = ""
                optionD = ""
                selectedCorrectOptionIndex = null
                marksText = if (type == "mcq") "1" else "5"
                onSuccess()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Failed to save question."
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateQuestionScreen(
    preselectedChapterId: String? = null,
    onBackClick: () -> Unit,
    onCreateChapterClick: () -> Unit,
    onQuestionSaved: () -> Unit,
    viewModel: CreateQuestionViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(preselectedChapterId) {
        viewModel.loadTeacherChapters(preselectedChapterId)
    }

    LaunchedEffect(viewModel.successMessage) {
        viewModel.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Question") },
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
            horizontalAlignment = Alignment.Start
        ) {
            // 1. Chapter Selection
            Text(
                text = "Select Chapter",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (viewModel.isChaptersLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Loading chapters...")
                }
            } else if (viewModel.teacherChapters.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "No chapters available. Please create a chapter first.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onCreateChapterClick) {
                            Text("Create Chapter")
                        }
                    }
                }
            } else {
                var dropdownExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = viewModel.selectedChapter?.name ?: "Select Chapter",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        viewModel.teacherChapters.forEach { chapter ->
                            DropdownMenuItem(
                                text = { Text(chapter.name) },
                                onClick = {
                                    viewModel.selectedChapter = chapter
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Question Type Selection
            Text(
                text = "Question Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = viewModel.type == "mcq",
                    onClick = {
                        viewModel.type = "mcq"
                        if (viewModel.marksText == "5") viewModel.marksText = "1"
                    },
                    label = { Text("MCQ") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = viewModel.type == "short",
                    onClick = {
                        viewModel.type = "short"
                        if (viewModel.marksText == "1") viewModel.marksText = "5"
                    },
                    label = { Text("Short Question") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Question Text Input
            OutlinedTextField(
                value = viewModel.questionText,
                onValueChange = {
                    viewModel.questionText = it
                    if (viewModel.errorMessage != null) viewModel.errorMessage = null
                },
                label = { Text("Question") },
                placeholder = { Text("Enter question") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                enabled = !viewModel.isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Type-Specific Form
            if (viewModel.type == "mcq") {
                OutlinedTextField(
                    value = viewModel.optionA,
                    onValueChange = { viewModel.optionA = it },
                    label = { Text("Option A") },
                    placeholder = { Text("Enter option A") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !viewModel.isLoading
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = viewModel.optionB,
                    onValueChange = { viewModel.optionB = it },
                    label = { Text("Option B") },
                    placeholder = { Text("Enter option B") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !viewModel.isLoading
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = viewModel.optionC,
                    onValueChange = { viewModel.optionC = it },
                    label = { Text("Option C") },
                    placeholder = { Text("Enter option C") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !viewModel.isLoading
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = viewModel.optionD,
                    onValueChange = { viewModel.optionD = it },
                    label = { Text("Option D") },
                    placeholder = { Text("Enter option D") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !viewModel.isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Correct Answer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                val optionLabels = listOf("Option A", "Option B", "Option C", "Option D")
                optionLabels.forEachIndexed { index, label ->
                    val optValue = when (index) {
                        0 -> viewModel.optionA
                        1 -> viewModel.optionB
                        2 -> viewModel.optionC
                        else -> viewModel.optionD
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !viewModel.isLoading) {
                                viewModel.selectedCorrectOptionIndex = index
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = viewModel.selectedCorrectOptionIndex == index,
                            onClick = { viewModel.selectedCorrectOptionIndex = index },
                            enabled = !viewModel.isLoading
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (optValue.isBlank()) label else "$label ($optValue)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Marks
            OutlinedTextField(
                value = viewModel.marksText,
                onValueChange = { viewModel.marksText = it },
                label = { Text("Marks") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !viewModel.isLoading
            )

            if (viewModel.errorMessage != null) {
                Text(
                    text = viewModel.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Button
            Button(
                onClick = { viewModel.saveQuestion(onQuestionSaved) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isLoading && viewModel.teacherChapters.isNotEmpty()
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Question")
                }
            }
        }
    }
}
