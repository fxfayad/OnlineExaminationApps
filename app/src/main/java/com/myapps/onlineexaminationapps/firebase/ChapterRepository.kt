package com.myapps.onlineexaminationapps.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.myapps.onlineexaminationapps.model.Chapter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ChapterRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val chaptersCollection = firestore.collection("chapters")

    suspend fun createChapter(
        name: String,
        description: String
    ): Result<Unit> = suspendCoroutine { continuation ->
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("CHAPTER_DEBUG", "createChapter failed: User not authenticated")
                Log.e("FIREBASE_DEBUG", "createChapter failed: User not authenticated")
                continuation.resume(Result.failure(Exception("User not authenticated")))
                return@suspendCoroutine
            }
            val docRef = chaptersCollection.document()
            val chapter = Chapter(
                id = docRef.id,
                name = name,
                description = description,
                createdBy = currentUser.uid,
                createdAt = Timestamp.now()
            )
            Log.d("CHAPTER_DEBUG", "createChapter saving to Firestore doc: ${docRef.id}, name: '$name', createdBy: '${currentUser.uid}'")
            Log.d("FIREBASE_DEBUG", "createChapter saving to Firestore doc: ${docRef.id}, name: '$name'")
            docRef.set(chapter)
                .addOnSuccessListener {
                    Log.d("CHAPTER_DEBUG", "createChapter write SUCCESS for doc: ${docRef.id}")
                    Log.d("FIREBASE_DEBUG", "createChapter write SUCCESS for doc: ${docRef.id}")
                    continuation.resume(Result.success(Unit))
                }
                .addOnFailureListener { e ->
                    Log.e("CHAPTER_DEBUG", "createChapter write FAILURE for doc: ${docRef.id}", e)
                    Log.e("FIREBASE_DEBUG", "createChapter write FAILURE for doc: ${docRef.id}", e)
                    continuation.resume(Result.failure(e))
                }
        } catch (e: Exception) {
            Log.e("CHAPTER_DEBUG", "createChapter exception", e)
            Log.e("FIREBASE_DEBUG", "createChapter exception", e)
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun updateChapter(chapter: Chapter): Result<Unit> = suspendCoroutine { continuation ->
        chaptersCollection.document(chapter.id).set(chapter)
            .addOnSuccessListener {
                Log.d("CHAPTER_DEBUG", "updateChapter SUCCESS for doc: ${chapter.id}")
                continuation.resume(Result.success(Unit))
            }
            .addOnFailureListener { e ->
                Log.e("CHAPTER_DEBUG", "updateChapter FAILURE for doc: ${chapter.id}", e)
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun getChapterById(id: String): Result<Chapter?> = suspendCoroutine { continuation ->
        chaptersCollection.document(id).get()
            .addOnSuccessListener { document ->
                val c = document.toObject(Chapter::class.java)
                val chapter = c?.copy(id = c.id.ifEmpty { document.id })
                Log.d("CHAPTER_DEBUG", "getChapterById SUCCESS: ${chapter?.id}, Name: ${chapter?.name}")
                continuation.resume(Result.success(chapter))
            }
            .addOnFailureListener { e ->
                Log.e("CHAPTER_DEBUG", "getChapterById FAILURE for id: $id", e)
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun getTeacherChapters(teacherUid: String): Result<List<Chapter>> = suspendCoroutine { continuation ->
        Log.d("CHAPTER_DEBUG", "getTeacherChapters query started for teacherUid: $teacherUid")
        chaptersCollection.whereEqualTo("createdBy", teacherUid).get()
            .addOnSuccessListener { querySnapshot ->
                try {
                    val chapters = querySnapshot.documents.mapNotNull { doc ->
                        val c = doc.toObject(Chapter::class.java)
                        c?.copy(id = c.id.ifEmpty { doc.id })
                    }
                    Log.d("CHAPTER_DEBUG", "getTeacherChapters SUCCESS. Found ${chapters.size} chapters for teacher $teacherUid")
                    continuation.resume(Result.success(chapters))
                } catch (e: Exception) {
                    Log.e("CHAPTER_DEBUG", "getTeacherChapters deserialization error", e)
                    continuation.resume(Result.failure(e))
                }
            }
            .addOnFailureListener { e ->
                Log.e("CHAPTER_DEBUG", "getTeacherChapters query FAILED", e)
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun getAllChapters(): Result<List<Chapter>> = suspendCoroutine { continuation ->
        Log.d("CHAPTER_DEBUG", "getAllChapters direct get started")
        chaptersCollection.get()
            .addOnSuccessListener { querySnapshot ->
                try {
                    val chapters = querySnapshot.documents.mapNotNull { doc ->
                        val c = doc.toObject(Chapter::class.java)
                        c?.copy(id = c.id.ifEmpty { doc.id })
                    }
                    Log.d("CHAPTER_DEBUG", "getAllChapters direct get SUCCESS. Count: ${chapters.size}")
                    continuation.resume(Result.success(chapters))
                } catch (e: Exception) {
                    Log.e("CHAPTER_DEBUG", "getAllChapters deserialization error", e)
                    continuation.resume(Result.failure(e))
                }
            }
            .addOnFailureListener { e ->
                Log.e("CHAPTER_DEBUG", "getAllChapters direct get FAILED", e)
                Log.e("FIREBASE_DEBUG", "getAllChapters direct get FAILED: ${e.message}", e)
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun getChapters(): Result<List<Chapter>> = getAllChapters()

    fun getTeacherChaptersRealtime(teacherUid: String): Flow<Result<List<Chapter>>> = callbackFlow {
        Log.d("CHAPTER_DEBUG", "getTeacherChaptersRealtime started for teacherUid: $teacherUid")
        val listener = chaptersCollection
            .whereEqualTo("createdBy", teacherUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CHAPTER_DEBUG", "getTeacherChaptersRealtime error", error)
                    Log.e("FIREBASE_DEBUG", "getTeacherChaptersRealtime error: ${error.message}", error)
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    try {
                        val chapters = snapshot.documents.mapNotNull { doc ->
                            val c = doc.toObject(Chapter::class.java)
                            c?.copy(id = c.id.ifEmpty { doc.id })
                        }
                        Log.d("CHAPTER_DEBUG", "getTeacherChaptersRealtime updated: Count=${chapters.size}")
                        Log.d("FIREBASE_DEBUG", "Teacher Chapter Count: ${chapters.size}")
                        trySend(Result.success(chapters))
                    } catch (e: Exception) {
                        Log.e("CHAPTER_DEBUG", "Error deserializing in getTeacherChaptersRealtime", e)
                        trySend(Result.failure(e))
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    fun getAllChaptersRealtime(): Flow<Result<List<Chapter>>> = callbackFlow {
        val currentUser = auth.currentUser
        Log.d("CHAPTER_DEBUG", "getAllChaptersRealtime started. Student Auth UID: ${currentUser?.uid}, Email: ${currentUser?.email}")
        Log.d("FIREBASE_DEBUG", "getAllChaptersRealtime started for UID: ${currentUser?.uid}")
        val listener = chaptersCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CHAPTER_DEBUG", "getAllChaptersRealtime listener error", error)
                    Log.e("FIREBASE_DEBUG", "Student chapters query permission/network error: ${error.message}", error)
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    try {
                        val chapters = snapshot.documents.mapNotNull { doc ->
                            val c = doc.toObject(Chapter::class.java)
                            val finalChapter = c?.copy(id = c.id.ifEmpty { doc.id })
                            Log.d("CHAPTER_DEBUG", "Student Chapter loaded -> ID: ${doc.id}, Name: ${finalChapter?.name}")
                            finalChapter
                        }
                        Log.d("CHAPTER_DEBUG", "getAllChaptersRealtime total chapters count: ${chapters.size}")
                        Log.d("FIREBASE_DEBUG", "Student Chapter count: ${chapters.size}")
                        trySend(Result.success(chapters))
                    } catch (e: Exception) {
                        Log.e("CHAPTER_DEBUG", "Error deserializing in getAllChaptersRealtime", e)
                        trySend(Result.failure(e))
                    }
                }
            }
        awaitClose { listener.remove() }
    }
}
