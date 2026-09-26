package com.myapps.onlineexaminationapps.firebase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.myapps.onlineexaminationapps.model.Chat
import com.myapps.onlineexaminationapps.model.ChatMessage
import com.myapps.onlineexaminationapps.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun getChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }

    fun getCurrentUserUid(): String? = auth.currentUser?.uid

    /**
     * Fetch users by role in real-time (e.g. students for teacher, teachers for student)
     */
    fun getUsersByRoleRealtime(targetRole: String): Flow<Result<List<User>>> = callbackFlow {
        val listener = firestore.collection("users")
            .whereEqualTo("role", targetRole.trim().lowercase())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatRepository", "Error fetching users by role: $targetRole", error)
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val userList = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(User::class.java)?.copy(uid = doc.id)
                        } catch (e: Exception) {
                            Log.e("ChatRepository", "Failed to parse user doc: ${doc.id}", e)
                            null
                        }
                    }
                    trySend(Result.success(userList))
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Get single user document real-time
     */
    fun getUserProfileRealtime(uid: String): Flow<Result<User>> = callbackFlow {
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)?.copy(uid = snapshot.id)
                    if (user != null) {
                        trySend(Result.success(user))
                    } else {
                        trySend(Result.failure(Exception("User not found")))
                    }
                } else {
                    trySend(Result.failure(Exception("User document does not exist")))
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Update user nickname in users/{uid}
     */
    suspend fun updateNickname(uid: String, newNickname: String): Result<Unit> {
        val currentUid = getCurrentUserUid()
        if (currentUid == null || currentUid != uid) {
            return Result.failure(IllegalStateException("Unauthorized to update nickname for another user"))
        }
        val trimmed = newNickname.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Nickname cannot be blank"))
        }
        return try {
            firestore.collection("users").document(uid)
                .update("nickname", trimmed)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to update nickname for $uid", e)
            Result.failure(e)
        }
    }

    /**
     * Ensures chat document exists at chats/{chatId}
     */
    suspend fun getOrCreateChat(studentId: String, teacherId: String): Result<String> {
        val chatId = getChatId(studentId, teacherId)
        val chatRef = firestore.collection("chats").document(chatId)
        return try {
            val doc = chatRef.get().await()
            if (!doc.exists()) {
                val chatData = hashMapOf(
                    "chatId" to chatId,
                    "studentId" to studentId,
                    "teacherId" to teacherId,
                    "participants" to listOf(studentId, teacherId),
                    "lastMessage" to "",
                    "lastMessageType" to "text",
                    "lastMessageTime" to FieldValue.serverTimestamp()
                )
                chatRef.set(chatData).await()
            }
            Result.success(chatId)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error in getOrCreateChat for $chatId", e)
            Result.failure(e)
        }
    }

    /**
     * Realtime flow of user chats where user is participant
     */
    fun getUserChatsRealtime(uid: String): Flow<Result<List<Chat>>> = callbackFlow {
        val listener = firestore.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatRepository", "Error fetching user chats for $uid", error)
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val chatList = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(Chat::class.java)?.copy(chatId = doc.id)
                        } catch (e: Exception) {
                            Log.e("ChatRepository", "Failed to parse chat doc ${doc.id}", e)
                            null
                        }
                    }
                    trySend(Result.success(chatList))
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Realtime flow of chat messages in chats/{chatId}/messages
     */
    fun getMessagesRealtime(chatId: String): Flow<Result<List<ChatMessage>>> = callbackFlow {
        val listener = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatRepository", "Error fetching messages for chat $chatId", error)
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(ChatMessage::class.java)?.copy(messageId = doc.id)
                        } catch (e: Exception) {
                            Log.e("ChatRepository", "Failed to parse message doc ${doc.id}", e)
                            null
                        }
                    }
                    trySend(Result.success(messages))
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Send text message
     */
    suspend fun sendTextMessage(
        chatId: String,
        senderId: String,
        receiverId: String,
        studentId: String,
        teacherId: String,
        text: String
    ): Result<Unit> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message text cannot be empty"))
        }

        val currentUid = getCurrentUserUid()
        if (currentUid == null || currentUid != senderId) {
            return Result.failure(IllegalStateException("Sender ID must match authenticated user"))
        }

        val chatRef = firestore.collection("chats").document(chatId)
        val msgRef = chatRef.collection("messages").document()

        val msgData = hashMapOf(
            "messageId" to msgRef.id,
            "senderId" to senderId,
            "receiverId" to receiverId,
            "messageType" to "text",
            "message" to trimmed,
            "imageUrl" to "",
            "timestamp" to FieldValue.serverTimestamp()
        )

        val chatData = hashMapOf(
            "chatId" to chatId,
            "studentId" to studentId,
            "teacherId" to teacherId,
            "participants" to listOf(studentId, teacherId),
            "lastMessage" to trimmed,
            "lastMessageType" to "text",
            "lastMessageTime" to FieldValue.serverTimestamp()
        )

        return try {
            firestore.runBatch { batch ->
                batch.set(msgRef, msgData)
                batch.set(chatRef, chatData, SetOptions.merge())
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error sending text message", e)
            Result.failure(e)
        }
    }

    /**
     * Upload photo to Firebase Storage & send image message
     */
    fun uploadAndSendImageMessage(
        chatId: String,
        senderId: String,
        receiverId: String,
        studentId: String,
        teacherId: String,
        imageUri: Uri,
        onProgress: (Float) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentUid = getCurrentUserUid()
        if (currentUid == null || currentUid != senderId) {
            onError("Sender ID must match authenticated user")
            return
        }

        val fileName = "chat_${UUID.randomUUID()}.jpg"
        val imageStorageRef = storage.reference.child("chat_images/$chatId/$fileName")

        val uploadTask = imageStorageRef.putFile(imageUri)
        uploadTask.addOnProgressListener { taskSnapshot ->
            val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toFloat()
            onProgress(progress)
        }.addOnFailureListener { e ->
            Log.e("ChatRepository", "Image upload failed", e)
            onError(e.localizedMessage ?: "Failed to upload image")
        }.addOnSuccessListener {
            imageStorageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                val chatRef = firestore.collection("chats").document(chatId)
                val msgRef = chatRef.collection("messages").document()

                val msgData = hashMapOf(
                    "messageId" to msgRef.id,
                    "senderId" to senderId,
                    "receiverId" to receiverId,
                    "messageType" to "image",
                    "message" to "📷 Photo",
                    "imageUrl" to downloadUrl.toString(),
                    "timestamp" to FieldValue.serverTimestamp()
                )

                val chatData = hashMapOf(
                    "chatId" to chatId,
                    "studentId" to studentId,
                    "teacherId" to teacherId,
                    "participants" to listOf(studentId, teacherId),
                    "lastMessage" to "📷 Photo",
                    "lastMessageType" to "image",
                    "lastMessageTime" to FieldValue.serverTimestamp()
                )

                firestore.runBatch { batch ->
                    batch.set(msgRef, msgData)
                    batch.set(chatRef, chatData, SetOptions.merge())
                }.addOnSuccessListener {
                    onSuccess()
                }.addOnFailureListener { e ->
                    Log.e("ChatRepository", "Failed to save image message to Firestore", e)
                    onError(e.localizedMessage ?: "Failed to save message")
                }
            }.addOnFailureListener { e ->
                Log.e("ChatRepository", "Failed to get download URL", e)
                onError(e.localizedMessage ?: "Failed to retrieve image URL")
            }
        }
    }
}
