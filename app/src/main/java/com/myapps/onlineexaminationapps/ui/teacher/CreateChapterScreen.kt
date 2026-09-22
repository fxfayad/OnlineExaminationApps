package com.myapps.onlineexaminationapps.ui.teacher

import android.widget.Toast
import androidx.compose.foundation.layout.*
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
import com.myapps.onlineexaminationapps.firebase.ChapterRepository
import com.myapps.onlineexaminationapps.model.Chapter
import kotlinx.coroutines.launch

class CreateChapterViewModel : ViewModel() {
    private val repository = ChapterRepository()

    var chapterId by mutableStateOf<String?>(null)
    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var successMessage by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)

    fun loadChapter(id: String) {
        chapterId = id
        isLoading = true
        viewModelScope.launch {
            val result = repository.getChapterById(id)
            isLoading = false
            if (result.isSuccess) {
                val chapter = result.getOrNull()
                chapter?.let {
                    name = it.name
                    description = it.description
                }
            }
        }
    }

    fun saveChapter(onSuccess: () -> Unit) {
        val trimmedName = name.trim()
        val trimmedDesc = description.trim()

        if (trimmedName.isEmpty()) {
            errorMessage = "Chapter name cannot be empty."
            return
        }

        if (trimmedDesc.isEmpty()) {
            errorMessage = "Chapter description cannot be empty."
            return
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            errorMessage = "User not logged in."
            return
        }

        isLoading = true
        errorMessage = null
        successMessage = null

        viewModelScope.launch {
            val result = if (chapterId == null) {
                repository.createChapter(trimmedName, trimmedDesc)
            } else {
                repository.updateChapter(
                    Chapter(
                        id = chapterId!!,
                        name = trimmedName,
                        description = trimmedDesc,
                        createdBy = currentUser.uid
                    )
                )
            }
            isLoading = false
            if (result.isSuccess) {
                successMessage = if (chapterId == null) "Chapter created successfully" else "Chapter updated successfully"
                name = ""
                description = ""
                onSuccess()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Failed to save chapter"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChapterScreen(
    chapterId: String? = null,
    onBackClick: () -> Unit,
    onChapterSaved: () -> Unit,
    viewModel: CreateChapterViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(chapterId) {
        chapterId?.let { viewModel.loadChapter(it) }
    }

    LaunchedEffect(viewModel.successMessage) {
        viewModel.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (chapterId == null) "Create Chapter" else "Edit Chapter") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = {
                    viewModel.name = it
                    if (viewModel.errorMessage != null) viewModel.errorMessage = null
                },
                label = { Text("Chapter Name") },
                placeholder = { Text("e.g. C Programming") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !viewModel.isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.description,
                onValueChange = {
                    viewModel.description = it
                    if (viewModel.errorMessage != null) viewModel.errorMessage = null
                },
                label = { Text("Chapter Description") },
                placeholder = { Text("e.g. Basic concepts of C programming.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                enabled = !viewModel.isLoading
            )

            if (viewModel.errorMessage != null) {
                Text(
                    text = viewModel.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.saveChapter(onChapterSaved) },
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
                    Text(if (chapterId == null) "Create Chapter" else "Save Changes")
                }
            }
        }
    }
}
