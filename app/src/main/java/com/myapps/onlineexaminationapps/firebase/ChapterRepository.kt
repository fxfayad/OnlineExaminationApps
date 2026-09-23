package com.myapps.onlineexaminationapps.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
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

    private fun mapDocToChapter(doc: DocumentSnapshot): Chapter? {
        val c = doc.toObject(Chapter::class.java) ?: return null
        val finalId = c.id.ifEmpty { c.chapterId.ifEmpty { doc.id } }
        val finalTitle = c.title.ifEmpty { c.name }
        val finalName = c.name.ifEmpty { c.title }
        val finalTeacher = c.teacherId.ifEmpty { c.createdBy }
        val finalCreatedBy = c.createdBy.ifEmpty { c.teacherId }

        return c.copy(
            id = finalId,
            chapterId = finalId,
            title = finalTitle,
            name = finalName,
            teacherId = finalTeacher,
            createdBy = finalCreatedBy
        )
    }

    suspend fun createChapter(
        name: String,
        description: String
    ): Result<Unit> = suspendCoroutine { continuation ->
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("CHAPTER_DEBUG", "createChapter failed: User not authenticated")
                continuation.resume(Result.failure(Exception("User not authenticated")))
                return@suspendCoroutine
            }
            val docRef = chaptersCollection.document()
            val chapterId = docRef.id
            val chapter = Chapter(
                id = chapterId,
                chapterId = chapterId,
                name = name,
                title = name,
                description = description,
                createdBy = currentUser.uid,
                teacherId = currentUser.uid,
                createdAt = Timestamp.now()
            )
            Log.d("CHAPTER_DEBUG", "createChapter saving to Firestore doc: $chapterId, title: '$name', teacherId: '${currentUser.uid}'")
            docRef.set(chapter)
                .addOnSuccessListener {
                    Log.d("CHAPTER_DEBUG", "createChapter write SUCCESS for doc: $chapterId")
                    continuation.resume(Result.success(Unit))
                }
                .addOnFailureListener { e ->
                    Log.e("CHAPTER_DEBUG", "createChapter write FAILURE for doc: $chapterId", e)
                    continuation.resume(Result.failure(e))
                }
        } catch (e: Exception) {
            Log.e("CHAPTER_DEBUG", "createChapter exception", e)
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun updateChapter(chapter: Chapter): Result<Unit> = suspendCoroutine { continuation ->
        val finalId = chapter.effectiveId
        val finalTitle = chapter.displayTitle
        val finalTeacher = chapter.effectiveTeacherId
        val updated = chapter.copy(
            id = finalId,
            chapterId = finalId,
            title = finalTitle,
            name = finalTitle,
            teacherId = finalTeacher,
            createdBy = finalTeacher
        )
        chaptersCollection.document(finalId).set(updated)
            .addOnSuccessListener {
                Log.d("CHAPTER_DEBUG", "updateChapter SUCCESS for doc: $finalId")
                continuation.resume(Result.success(Unit))
            }
            .addOnFailureListener { e ->
                Log.e("CHAPTER_DEBUG", "updateChapter FAILURE for doc: $finalId", e)
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun getChapterById(id: String): Result<Chapter?> = suspendCoroutine { continuation ->
        chaptersCollection.document(id).get()
            .addOnSuccessListener { document ->
                val chapter = if (document.exists()) mapDocToChapter(document) else null
                Log.d("CHAPTER_DEBUG", "getChapterById SUCCESS: ${chapter?.id}, Title: ${chapter?.displayTitle}")
                continuation.resume(Result.success(chapter))
            }
            .addOnFailureListener { e ->
                Log.e("CHAPTER_DEBUG", "getChapterById FAILURE for id: $id", e)
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun getTeacherChapters(teacherUid: String): Result<List<Chapter>> = suspendCoroutine { continuation ->
        Log.d("CHAPTER_DEBUG", "getTeacherChapters query started for teacherUid: $teacherUid")
        chaptersCollection.get()
            .addOnSuccessListener { querySnapshot ->
                try {
                    val chapters = querySnapshot.documents
                        .mapNotNull { mapDocToChapter(it) }
                        .filter { it.effectiveTeacherId == teacherUid }
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
                    val chapters = querySnapshot.documents.mapNotNull { mapDocToChapter(it) }
                    Log.d("CHAPTER_DEBUG", "getAllChapters direct get SUCCESS. Count: ${chapters.size}")
                    continuation.resume(Result.success(chapters))
                } catch (e: Exception) {
                    Log.e("CHAPTER_DEBUG", "getAllChapters deserialization error", e)
                    continuation.resume(Result.failure(e))
                }
            }
            .addOnFailureListener { e ->
                Log.e("CHAPTER_DEBUG", "getAllChapters direct get FAILED", e)
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun getChapters(): Result<List<Chapter>> = getAllChapters()

    fun getTeacherChaptersRealtime(teacherUid: String): Flow<Result<List<Chapter>>> = callbackFlow {
        Log.d("CHAPTER_DEBUG", "getTeacherChaptersRealtime started for teacherUid: $teacherUid")
        val listener = chaptersCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CHAPTER_DEBUG", "getTeacherChaptersRealtime error", error)
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    try {
                        val chapters = snapshot.documents
                            .mapNotNull { mapDocToChapter(it) }
                            .filter { it.effectiveTeacherId == teacherUid }
                        Log.d("CHAPTER_DEBUG", "getTeacherChaptersRealtime updated: Count=${chapters.size}")
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
        Log.d("CHAPTER_DEBUG", "getAllChaptersRealtime started")
        val listener = chaptersCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CHAPTER_DEBUG", "getAllChaptersRealtime listener error", error)
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    try {
                        val chapters = snapshot.documents.mapNotNull { mapDocToChapter(it) }
                        Log.d("CHAPTER_DEBUG", "getAllChaptersRealtime total chapters count: ${chapters.size}")
                        trySend(Result.success(chapters))
                    } catch (e: Exception) {
                        Log.e("CHAPTER_DEBUG", "Error deserializing in getAllChaptersRealtime", e)
                        trySend(Result.failure(e))
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun deleteChapter(chapterId: String): Result<Unit> = suspendCoroutine { continuation ->
        chaptersCollection.document(chapterId).delete()
            .addOnSuccessListener {
                Log.d("CHAPTER_DEBUG", "deleteChapter SUCCESS for doc: $chapterId")
                continuation.resume(Result.success(Unit))
            }
            .addOnFailureListener { e ->
                Log.e("CHAPTER_DEBUG", "deleteChapter FAILURE for doc: $chapterId", e)
                continuation.resume(Result.failure(e))
            }
    }
}
