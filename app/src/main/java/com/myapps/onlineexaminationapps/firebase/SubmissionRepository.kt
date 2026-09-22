package com.myapps.onlineexaminationapps.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.myapps.onlineexaminationapps.model.ExamResult
import com.myapps.onlineexaminationapps.model.StudentAnswer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SubmissionRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val submissionsCollection = firestore.collection("submissions")
    private val studentAnswersCollection = firestore.collection("studentAnswers")
    private val usersCollection = firestore.collection("users")

    suspend fun hasStudentSubmittedChapter(studentId: String, chapterId: String): Result<Boolean> = suspendCoroutine { continuation ->
        submissionsCollection
            .whereEqualTo("studentId", studentId)
            .whereEqualTo("chapterId", chapterId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                continuation.resume(Result.success(!querySnapshot.isEmpty))
            }
            .addOnFailureListener { e ->
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun getStudentSubmissionForChapter(studentId: String, chapterId: String): Result<ExamResult?> = suspendCoroutine { continuation ->
        submissionsCollection
            .whereEqualTo("studentId", studentId)
            .whereEqualTo("chapterId", chapterId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val doc = querySnapshot.documents.first()
                    val res = doc.toObject(ExamResult::class.java)?.copy(id = doc.id)
                    continuation.resume(Result.success(res))
                } else {
                    continuation.resume(Result.success(null))
                }
            }
            .addOnFailureListener { e ->
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun submitExam(
        submission: ExamResult,
        answers: List<StudentAnswer>
    ): Result<String> = suspendCoroutine { continuation ->
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                continuation.resume(Result.failure(Exception("User not authenticated")))
                return@suspendCoroutine
            }

            val batch = firestore.batch()
            val subDocRef = submissionsCollection.document()
            val submissionId = subDocRef.id
            val now = Timestamp.now()

            val finalSubmission = submission.copy(
                id = submissionId,
                studentId = currentUser.uid,
                submittedAt = now
            )

            batch.set(subDocRef, finalSubmission)

            answers.forEach { ans ->
                val ansDocRef = studentAnswersCollection.document()
                val finalAns = ans.copy(
                    id = ansDocRef.id,
                    submissionId = submissionId,
                    studentId = currentUser.uid,
                    submittedAt = now
                )
                batch.set(ansDocRef, finalAns)
            }

            batch.commit()
                .addOnSuccessListener {
                    Log.d("SubmissionDebug", "Atomic batch commit success for submissionId: $submissionId")
                    continuation.resume(Result.success(submissionId))
                }
                .addOnFailureListener { e ->
                    Log.e("SubmissionDebug", "Atomic batch commit failed", e)
                    continuation.resume(Result.failure(e))
                }
        } catch (e: Exception) {
            Log.e("SubmissionDebug", "submitExam exception", e)
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun getStudentResult(submissionId: String): Result<ExamResult?> = suspendCoroutine { continuation ->
        submissionsCollection.document(submissionId).get()
            .addOnSuccessListener { document ->
                val res = document.toObject(ExamResult::class.java)?.copy(id = document.id)
                continuation.resume(Result.success(res))
            }
            .addOnFailureListener { e ->
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun getStudentSubmissions(studentId: String): Result<List<ExamResult>> = suspendCoroutine { continuation ->
        submissionsCollection
            .whereEqualTo("studentId", studentId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                try {
                    val list = querySnapshot.documents.mapNotNull { doc ->
                        doc.toObject(ExamResult::class.java)?.copy(id = doc.id)
                    }
                    continuation.resume(Result.success(list))
                } catch (e: Exception) {
                    continuation.resume(Result.failure(e))
                }
            }
            .addOnFailureListener { e ->
                continuation.resume(Result.failure(e))
            }
    }

    fun getStudentSubmissionsRealtime(studentId: String): Flow<Result<List<ExamResult>>> = callbackFlow {
        val listener = submissionsCollection
            .whereEqualTo("studentId", studentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    try {
                        val list = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(ExamResult::class.java)?.copy(id = doc.id)
                        }
                        trySend(Result.success(list))
                    } catch (e: Exception) {
                        trySend(Result.failure(e))
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    // --- STEP 8 TEACHER EVALUATION EXTENSIONS ---

    suspend fun getSubmissionsForChapter(chapterId: String): Result<List<ExamResult>> = suspendCoroutine { continuation ->
        Log.d("TEACHER_ANSWER_DEBUG", "getSubmissionsForChapter query for chapterId: $chapterId")
        submissionsCollection
            .whereEqualTo("chapterId", chapterId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                try {
                    val list = querySnapshot.documents.mapNotNull { doc ->
                        doc.toObject(ExamResult::class.java)?.copy(id = doc.id)
                    }
                    Log.d("TEACHER_ANSWER_DEBUG", "getSubmissionsForChapter count: ${list.size}")
                    continuation.resume(Result.success(list))
                } catch (e: Exception) {
                    Log.e("TEACHER_ANSWER_DEBUG", "getSubmissionsForChapter error", e)
                    continuation.resume(Result.failure(e))
                }
            }
            .addOnFailureListener { e ->
                Log.e("TEACHER_ANSWER_DEBUG", "getSubmissionsForChapter failure", e)
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun getStudentAnswersForSubmission(submissionId: String): Result<List<StudentAnswer>> = suspendCoroutine { continuation ->
        Log.d("TEACHER_ANSWER_DEBUG", "getStudentAnswersForSubmission query for submissionId: $submissionId")
        studentAnswersCollection
            .whereEqualTo("submissionId", submissionId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                try {
                    val list = querySnapshot.documents.mapNotNull { doc ->
                        doc.toObject(StudentAnswer::class.java)?.copy(id = doc.id)
                    }
                    Log.d("TEACHER_ANSWER_DEBUG", "getStudentAnswersForSubmission count: ${list.size}")
                    continuation.resume(Result.success(list))
                } catch (e: Exception) {
                    Log.e("TEACHER_ANSWER_DEBUG", "getStudentAnswersForSubmission error", e)
                    continuation.resume(Result.failure(e))
                }
            }
            .addOnFailureListener { e ->
                Log.e("TEACHER_ANSWER_DEBUG", "getStudentAnswersForSubmission failure", e)
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun getStudentName(studentId: String): Result<String> = suspendCoroutine { continuation ->
        if (studentId.isBlank()) {
            continuation.resume(Result.success("Student"))
            return@suspendCoroutine
        }
        usersCollection.document(studentId).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name")
                val email = doc.getString("email")
                val finalName = if (!name.isNullOrBlank()) name else if (!email.isNullOrBlank()) email else "Student"
                continuation.resume(Result.success(finalName))
            }
            .addOnFailureListener {
                continuation.resume(Result.success("Student"))
            }
    }

    suspend fun saveEvaluation(
        submissionId: String,
        answers: List<StudentAnswer>,
        shortQuestionObtainedMarks: Int,
        totalObtainedMarks: Int,
        isFullyEvaluated: Boolean
    ): Result<Unit> = suspendCoroutine { continuation ->
        try {
            Log.d("TEACHER_ANSWER_DEBUG", "saveEvaluation saving for submissionId: $submissionId, shortObtained: $shortQuestionObtainedMarks, totalObtained: $totalObtainedMarks, fullyEvaluated: $isFullyEvaluated")
            val batch = firestore.batch()

            answers.forEach { ans ->
                if (ans.id.isNotEmpty()) {
                    val docRef = studentAnswersCollection.document(ans.id)
                    batch.set(docRef, ans)
                }
            }

            val subDocRef = submissionsCollection.document(submissionId)
            val updates = mapOf(
                "shortQuestionObtainedMarks" to shortQuestionObtainedMarks,
                "obtainedMarks" to totalObtainedMarks,
                "status" to if (isFullyEvaluated) "Completed" else "Pending Teacher Evaluation"
            )
            batch.update(subDocRef, updates)

            batch.commit()
                .addOnSuccessListener {
                    Log.d("TEACHER_ANSWER_DEBUG", "saveEvaluation batch commit SUCCESS for submissionId: $submissionId")
                    continuation.resume(Result.success(Unit))
                }
                .addOnFailureListener { e ->
                    Log.e("TEACHER_ANSWER_DEBUG", "saveEvaluation batch commit FAILURE for submissionId: $submissionId", e)
                    continuation.resume(Result.failure(e))
                }
        } catch (e: Exception) {
            Log.e("TEACHER_ANSWER_DEBUG", "saveEvaluation exception", e)
            continuation.resume(Result.failure(e))
        }
    }
}
