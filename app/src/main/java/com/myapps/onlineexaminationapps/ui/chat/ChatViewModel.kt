package com.myapps.onlineexaminationapps.ui.chat

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapps.onlineexaminationapps.firebase.ChatRepository
import com.myapps.onlineexaminationapps.model.ChatMessage
import com.myapps.onlineexaminationapps.model.User
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val chatRepository = ChatRepository()

    var currentUser by mutableStateOf<User?>(null)
        private set

    var otherUser by mutableStateOf<User?>(null)
        private set

    var chatId by mutableStateOf<String?>(null)
        private set

    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

    var messageText by mutableStateOf("")

    var isLoading by mutableStateOf(true)
        private set

    var isSending by mutableStateOf(false)
        private set

    var isUploadingImage by mutableStateOf(false)
        private set

    var uploadProgress by mutableFloatStateOf(0f)

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var fullScreenImageUrl by mutableStateOf<String?>(null)

    private var otherUserJob: Job? = null
    private var currentUserJob: Job? = null
    private var messagesJob: Job? = null

    fun initialize(otherUserId: String) {
        val currentUid = chatRepository.getCurrentUserUid()
        if (currentUid == null) {
            isLoading = false
            errorMessage = "User not logged in"
            return
        }

        isLoading = true
        errorMessage = null

        // Fetch current user
        currentUserJob?.cancel()
        currentUserJob = viewModelScope.launch {
            chatRepository.getUserProfileRealtime(currentUid)
                .catch { Log.e("ChatViewModel", "Error loading current user", it) }
                .collect { res ->
                    if (res.isSuccess) currentUser = res.getOrNull()
                }
        }

        // Fetch other user
        otherUserJob?.cancel()
        otherUserJob = viewModelScope.launch {
            chatRepository.getUserProfileRealtime(otherUserId)
                .catch {
                    isLoading = false
                    errorMessage = "Failed to load user profile"
                }
                .collect { res ->
                    if (res.isSuccess) {
                        otherUser = res.getOrNull()
                        setupChatSession(currentUid, otherUserId)
                    } else {
                        isLoading = false
                        errorMessage = "User not found"
                    }
                }
        }
    }

    private fun setupChatSession(currentUid: String, otherUserId: String) {
        val targetOtherUser = otherUser ?: return
        val studentId: String
        val teacherId: String

        // Determine student & teacher IDs based on roles
        if (currentUser?.role.equals("teacher", ignoreCase = true) || targetOtherUser.role.equals("student", ignoreCase = true)) {
            studentId = if (targetOtherUser.role.equals("student", ignoreCase = true)) targetOtherUser.uid else currentUid
            teacherId = if (currentUser?.role.equals("teacher", ignoreCase = true)) currentUid else targetOtherUser.uid
        } else {
            studentId = currentUid
            teacherId = otherUserId
        }

        viewModelScope.launch {
            val chatRes = chatRepository.getOrCreateChat(studentId, teacherId)
            if (chatRes.isSuccess) {
                val id = chatRes.getOrNull()!!
                chatId = id
                listenToMessages(id)
            } else {
                isLoading = false
                errorMessage = "Failed to initialize conversation"
            }
        }
    }

    private fun listenToMessages(cId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.getMessagesRealtime(cId)
                .catch { e ->
                    isLoading = false
                    errorMessage = "Error loading messages: ${e.localizedMessage}"
                }
                .collect { res ->
                    isLoading = false
                    if (res.isSuccess) {
                        messages = res.getOrNull() ?: emptyList()
                        errorMessage = null
                    } else {
                        errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Failed to receive messages"
                    }
                }
        }
    }

    fun sendTextMessage() {
        val text = messageText.trim()
        if (text.isEmpty()) return

        val cId = chatId ?: return
        val sender = currentUser ?: return
        val receiver = otherUser ?: return

        val studentId = if (sender.role.equals("student", ignoreCase = true)) sender.uid else receiver.uid
        val teacherId = if (sender.role.equals("teacher", ignoreCase = true)) sender.uid else receiver.uid

        isSending = true
        messageText = "" // Clear input field immediately for good UX

        viewModelScope.launch {
            val res = chatRepository.sendTextMessage(
                chatId = cId,
                senderId = sender.uid,
                receiverId = receiver.uid,
                studentId = studentId,
                teacherId = teacherId,
                text = text
            )
            isSending = false
            if (res.isFailure) {
                errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Failed to send message"
            }
        }
    }

    fun sendImageMessage(imageUri: Uri) {
        val cId = chatId ?: return
        val sender = currentUser ?: return
        val receiver = otherUser ?: return

        val studentId = if (sender.role.equals("student", ignoreCase = true)) sender.uid else receiver.uid
        val teacherId = if (sender.role.equals("teacher", ignoreCase = true)) sender.uid else receiver.uid

        isUploadingImage = true
        uploadProgress = 0f

        chatRepository.uploadAndSendImageMessage(
            chatId = cId,
            senderId = sender.uid,
            receiverId = receiver.uid,
            studentId = studentId,
            teacherId = teacherId,
            imageUri = imageUri,
            onProgress = { progress ->
                uploadProgress = progress
            },
            onSuccess = {
                isUploadingImage = false
                uploadProgress = 0f
            },
            onError = { err ->
                isUploadingImage = false
                uploadProgress = 0f
                errorMessage = err
            }
        )
    }

    fun openImageViewer(imageUrl: String) {
        fullScreenImageUrl = imageUrl
    }

    fun closeImageViewer() {
        fullScreenImageUrl = null
    }
}
