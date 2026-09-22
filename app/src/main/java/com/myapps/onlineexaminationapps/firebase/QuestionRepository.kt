package com.myapps.onlineexaminationapps.firebase

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.myapps.onlineexaminationapps.model.Question
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class QuestionRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val questionsCollection = firestore.collection("questions")

    suspend fun createQuestion(
        question: Question
    ): Result<Unit> = suspendCoroutine { continuation ->
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                continuation.resume(Result.failure(Exception("User not authenticated")))
                return@suspendCoroutine
            }
            val docRef = questionsCollection.document()
            val questionWithId = question.copy(
                id = docRef.id,
                createdBy = currentUser.uid,
                createdAt = Timestamp.now()
            )
            docRef.set(questionWithId)
                .addOnSuccessListener {
                    continuation.resume(Result.success(Unit))
                }
                .addOnFailureListener { e ->
                    continuation.resume(Result.failure(e))
                }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun createQuestion(
        chapterId: String,
        question: Question
    ): Result<Unit> = createQuestion(question.copy(chapterId = chapterId))

    suspend fun getQuestionsByChapter(chapterId: String): Result<List<Question>> = suspendCoroutine { continuation ->
        questionsCollection.whereEqualTo("chapterId", chapterId).get()
            .addOnSuccessListener { querySnapshot ->
                try {
                    val questions = querySnapshot.documents.mapNotNull { doc ->
                        val q = doc.toObject(Question::class.java)
                        q?.copy(id = q.id.ifEmpty { doc.id })
                    }
                    continuation.resume(Result.success(questions))
                } catch (e: Exception) {
                    continuation.resume(Result.failure(e))
                }
            }
            .addOnFailureListener { e ->
                continuation.resume(Result.failure(e))
            }
    }

    fun getQuestionsByChapterRealtime(chapterId: String): Flow<Result<List<Question>>> = callbackFlow {
        val listener = questionsCollection
            .whereEqualTo("chapterId", chapterId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    try {
                        val questions = snapshot.documents.mapNotNull { doc ->
                            val q = doc.toObject(Question::class.java)
                            q?.copy(id = q.id.ifEmpty { doc.id })
                        }
                        trySend(Result.success(questions))
                    } catch (e: Exception) {
                        trySend(Result.failure(e))
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun getTeacherQuestions(teacherUid: String): Result<List<Question>> = suspendCoroutine { continuation ->
        questionsCollection.whereEqualTo("createdBy", teacherUid).get()
            .addOnSuccessListener { querySnapshot ->
                try {
                    val questions = querySnapshot.documents.mapNotNull { doc ->
                        val q = doc.toObject(Question::class.java)
                        q?.copy(id = q.id.ifEmpty { doc.id })
                    }
                    continuation.resume(Result.success(questions))
                } catch (e: Exception) {
                    continuation.resume(Result.failure(e))
                }
            }
            .addOnFailureListener { e ->
                continuation.resume(Result.failure(e))
            }
    }

    fun getTeacherQuestionsRealtime(teacherUid: String): Flow<Result<List<Question>>> = callbackFlow {
        val listener = questionsCollection
            .whereEqualTo("createdBy", teacherUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    try {
                        val questions = snapshot.documents.mapNotNull { doc ->
                            val q = doc.toObject(Question::class.java)
                            q?.copy(id = q.id.ifEmpty { doc.id })
                        }
                        trySend(Result.success(questions))
                    } catch (e: Exception) {
                        trySend(Result.failure(e))
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun getQuestions(chapterId: String): Result<List<Question>> = getQuestionsByChapter(chapterId)

    suspend fun updateQuestion(question: Question): Result<Unit> = suspendCoroutine { continuation ->
        questionsCollection.document(question.id).set(question)
            .addOnSuccessListener {
                continuation.resume(Result.success(Unit))
            }
            .addOnFailureListener { e ->
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun updateQuestion(chapterId: String, question: Question): Result<Unit> =
        updateQuestion(question.copy(chapterId = chapterId))

    suspend fun getQuestionById(questionId: String): Result<Question?> = suspendCoroutine { continuation ->
        questionsCollection.document(questionId).get()
            .addOnSuccessListener { document ->
                val q = document.toObject(Question::class.java)
                val question = q?.copy(id = q.id.ifEmpty { document.id })
                continuation.resume(Result.success(question))
            }
            .addOnFailureListener { e ->
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun getQuestionById(chapterId: String, questionId: String): Result<Question?> = getQuestionById(questionId)
}
