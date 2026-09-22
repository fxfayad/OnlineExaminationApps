package com.myapps.onlineexaminationapps.ui.teacher

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.myapps.onlineexaminationapps.firebase.ChapterRepository
import com.myapps.onlineexaminationapps.model.Chapter
import kotlinx.coroutines.launch

class ChapterListViewModel : ViewModel() {
    private val repository = ChapterRepository()

    var chapters by mutableStateOf<List<Chapter>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun fetchChapters() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            val result = if (uid != null) {
                repository.getTeacherChapters(uid)
            } else {
                repository.getAllChapters()
            }
            isLoading = false
            if (result.isSuccess) {
                chapters = result.getOrNull() ?: emptyList()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Failed to fetch chapters"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterListScreen(
    onBackClick: () -> Unit,
    onChapterClick: (String) -> Unit,
    viewModel: ChapterListViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.fetchChapters()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chapters") },
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
                Text(
                    text = viewModel.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else if (viewModel.chapters.isEmpty()) {
                Text(
                    text = "No chapters found.",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.chapters) { chapter ->
                        ChapterListItem(
                            chapter = chapter,
                            onClick = { onChapterClick(chapter.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterListItem(
    chapter: Chapter,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(text = chapter.name, style = MaterialTheme.typography.titleMedium)
            if (chapter.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = chapter.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
