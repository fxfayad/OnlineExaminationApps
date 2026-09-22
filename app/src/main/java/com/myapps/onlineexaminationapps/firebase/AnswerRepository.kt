package com.myapps.onlineexaminationapps.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.myapps.onlineexaminationapps.model.Answer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AnswerRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val answersCollection = firestore.collection("answers")

    suspend fun submitAnswer(answer: Answer): Result<Unit> = suspendCoroutine { continuation ->
        try {
            val docRef = answersCollection.document()
            val answerWithId = answer.copy(id = docRef.id)
            docRef.set(answerWithId)
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

    suspend fun getStudentAnswers(): Result<List<Answer>> = suspendCoroutine { continuation ->
        answersCollection.get()
            .addOnSuccessListener { querySnapshot ->
                val answers = querySnapshot.toObjects(Answer::class.java)
                continuation.resume(Result.success(answers))
            }
            .addOnFailureListener { e ->
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun getAnswersForChapter(chapterId: String): Result<List<Answer>> = suspendCoroutine { continuation ->
        answersCollection.whereEqualTo("chapterId", chapterId).get()
            .addOnSuccessListener { querySnapshot ->
                val answers = querySnapshot.toObjects(Answer::class.java)
                continuation.resume(Result.success(answers))
            }
            .addOnFailureListener { e ->
                continuation.resume(Result.failure(e))
            }
    }
}
