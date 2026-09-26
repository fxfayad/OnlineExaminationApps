package com.myapps.onlineexaminationapps.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.myapps.onlineexaminationapps.firebase.AuthRepository
import com.myapps.onlineexaminationapps.firebase.ChatRepository
import com.myapps.onlineexaminationapps.model.Chat
import com.myapps.onlineexaminationapps.model.User
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class UserChatItem(
    val user: User,
    val chat: Chat?
)

class UserListViewModel : ViewModel() {
    private val chatRepository = ChatRepository()
    private val authRepository = AuthRepository()

    var currentUser by mutableStateOf<User?>(null)
        private set

    var targetUsers by mutableStateOf<List<User>>(emptyList())
        private set

    var userChats by mutableStateOf<List<Chat>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var showNicknameDialog by mutableStateOf(false)
    var nicknameInput by mutableStateOf("")
    var isUpdatingNickname by mutableStateOf(false)
    var nicknameError by mutableStateOf<String?>(null)

    private var usersJob: Job? = null
    private var chatsJob: Job? = null
    private var currentUserJob: Job? = null

    fun initialize(userRole: String) {
        val currentUid = chatRepository.getCurrentUserUid()
        if (currentUid == null) {
            isLoading = false
            errorMessage = "User not authenticated"
            return
        }

        isLoading = true
        errorMessage = null

        // Listen to current user profile for nickname updates
        currentUserJob?.cancel()
        currentUserJob = viewModelScope.launch {
            chatRepository.getUserProfileRealtime(currentUid)
                .catch { Log.e("UserListViewModel", "Error fetching user profile", it) }
                .collect { res ->
                    if (res.isSuccess) {
                        currentUser = res.getOrNull()
                        if (nicknameInput.isEmpty()) {
                            nicknameInput = currentUser?.nickname ?: ""
                        }
                    }
                }
        }

        // Fetch target users based on role (if student, fetch teachers; if teacher, fetch students)
        val targetRole = if (userRole.equals("teacher", ignoreCase = true)) "student" else "teacher"

        usersJob?.cancel()
        usersJob = viewModelScope.launch {
            chatRepository.getUsersByRoleRealtime(targetRole)
                .catch { e ->
                    isLoading = false
                    errorMessage = "Failed to load ${targetRole}s: ${e.localizedMessage}"
                }
                .collect { res ->
                    isLoading = false
                    if (res.isSuccess) {
                        targetUsers = res.getOrNull() ?: emptyList()
                        errorMessage = null
                    } else {
                        errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Error loading users"
                    }
                }
        }

        // Fetch user chats for latest message preview
        chatsJob?.cancel()
        chatsJob = viewModelScope.launch {
            chatRepository.getUserChatsRealtime(currentUid)
                .catch { Log.e("UserListViewModel", "Error fetching chats", it) }
                .collect { res ->
                    if (res.isSuccess) {
                        userChats = res.getOrNull() ?: emptyList()
                    }
                }
        }
    }

    val userChatItems: List<UserChatItem>
        get() {
            val chatMap = userChats.associateBy { chat ->
                // The other participant's UID
                chat.participants.firstOrNull { it != currentUser?.uid } ?: ""
            }
            return targetUsers.map { user ->
                UserChatItem(
                    user = user,
                    chat = chatMap[user.uid]
                )
            }.sortedByDescending { item ->
                item.chat?.lastMessageTimeMillis ?: 0L
            }
        }

    fun openNicknameDialog() {
        nicknameInput = currentUser?.nickname ?: ""
        nicknameError = null
        showNicknameDialog = true
    }

    fun closeNicknameDialog() {
        showNicknameDialog = false
        nicknameError = null
    }

    fun updateNickname() {
        val trimmed = nicknameInput.trim()
        if (trimmed.isBlank()) {
            nicknameError = "Nickname cannot be empty"
            return
        }
        val currentUid = currentUser?.uid ?: return

        isUpdatingNickname = true
        nicknameError = null

        viewModelScope.launch {
            val res = chatRepository.updateNickname(currentUid, trimmed)
            isUpdatingNickname = false
            if (res.isSuccess) {
                showNicknameDialog = false
            } else {
                nicknameError = res.exceptionOrNull()?.localizedMessage ?: "Failed to update nickname"
            }
        }
    }
}

// Simple Log import helper
private object Log {
    fun e(tag: String, msg: String, t: Throwable?) {
        android.util.Log.e(tag, msg, t)
    }
}
