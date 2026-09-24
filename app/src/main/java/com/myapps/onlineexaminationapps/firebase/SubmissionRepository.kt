package com.myapps.onlineexaminationapps.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.myapps.onlineexaminationapps.model.ExamResult
import com.myapps.onlineexaminationapps.model.Result as AppResult
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
    private val resultsCollection = firestore.collection("results")
    private val studentAnswersCollection = firestore.collection("studentAnswers")
    private val usersCollection = firestore.collection("users")

    suspend fun hasStudentSubmittedChapter(studentId: String, chapterId: String): Result<Boolean> = suspendCoroutine { continuation ->
        resultsCollection
            .whereEqualTo("studentId", studentId)
            .whereEqualTo("chapterId", chapterId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    continuation.resume(Result.success(true))
                } else {
                    submissionsCollection
                        .whereEqualTo("studentId", studentId)
                        .whereEqualTo("chapterId", chapterId)
                        .get()
                        .addOnSuccessListener { subSnapshot ->
                            continuation.resume(Result.success(!subSnapshot.isEmpty))
                        }
                        .addOnFailureListener {
                            continuation.resume(Result.success(false))
                        }
                }
            }
            .addOnFailureListener {
                continuation.resume(Result.success(false))
            }
    }

    suspend fun getStudentSubmissionForChapter(studentId: String, chapterId: String): Result<ExamResult?> = suspendCoroutine { continuation ->
        resultsCollection
            .whereEqualTo("studentId", studentId)
            .whereEqualTo("chapterId", chapterId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val doc = querySnapshot.documents.first()
                    val res = doc.toObject(ExamResult::class.java)?.copy(id = doc.id)
                    continuation.resume(Result.success(res))
                } else {
                    submissionsCollection
                        .whereEqualTo("studentId", studentId)
                        .whereEqualTo("chapterId", chapterId)
                        .get()
                        .addOnSuccessListener { subSnap ->
                            if (!subSnap.isEmpty) {
                                val doc = subSnap.documents.first()
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
            val resultDocRef = resultsCollection.document(submissionId)
            val now = Timestamp.now()

            val initialStatus = if (submission.shortQuestionTotalMarks > 0) "PENDING" else "COMPLETED"

            val finalSubmission = submission.copy(
                id = submissionId,
                studentId = currentUser.uid,
                status = initialStatus,
                submittedAt = now
            )

            val resultObj = AppResult(
                resultId = submissionId,
                id = submissionId,
                studentId = currentUser.uid,
                chapterId = submission.chapterId,
                chapterName = submission.chapterName,
                totalQuestions = submission.totalQuestions,
                mcqQuestions = submission.totalQuestions - (if (submission.shortQuestionTotalMarks > 0) 1 else 0),
                shortQuestions = if (submission.shortQuestionTotalMarks > 0) 1 else 0,
                mcqMarks = submission.mcqTotalMarks,
                shortMarks = submission.shortQuestionTotalMarks,
                totalMarks = submission.totalMarks,
                obtainedMarks = submission.mcqObtainedMarks,
                pendingMarks = submission.shortQuestionTotalMarks,
                status = initialStatus,
                submittedAt = now
            )

            batch.set(subDocRef, finalSubmission)
            batch.set(resultDocRef, resultObj)

            answers.forEach { ans ->
                val ansDocRef = studentAnswersCollection.document()
                val ansId = ansDocRef.id
                val isShort = ans.questionType.equals("SHORT", ignoreCase = true) || ans.questionType.equals("short", ignoreCase = true)
                val finalAns = ans.copy(
                    id = ansId,
                    answerId = ansId,
                    submissionId = submissionId,
                    studentId = currentUser.uid,
                    status = if (isShort) "PENDING" else "REVIEWED",
                    submittedAt = now
                )
                batch.set(ansDocRef, finalAns)
            }

            batch.commit()
                .addOnSuccessListener {
                    Log.d("SubmissionDebug", "Atomic batch commit success for resultId/submissionId: $submissionId")
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
        resultsCollection.document(submissionId).get()
            .addOnSuccessListener { resultDoc ->
                if (resultDoc.exists()) {
                    val appRes = resultDoc.toObject(AppResult::class.java)
                    if (appRes != null) {
                        val mapped = ExamResult(
                            id = appRes.effectiveResultId,
                            studentId = appRes.studentId,
                            chapterId = appRes.chapterId,
                            chapterName = appRes.chapterName,
                            totalQuestions = appRes.totalQuestions,
                            totalMarks = appRes.totalMarks,
                            mcqTotalMarks = appRes.mcqMarks,
                            mcqObtainedMarks = appRes.obtainedMarks - (appRes.totalMarks - appRes.mcqMarks - appRes.pendingMarks).coerceAtLeast(0),
                            shortQuestionTotalMarks = appRes.shortMarks,
                            shortQuestionObtainedMarks = appRes.shortMarks - appRes.pendingMarks,
                            obtainedMarks = appRes.obtainedMarks,
                            status = appRes.status,
                            submittedAt = appRes.submittedAt
                        )
                        continuation.resume(Result.success(mapped))
                        return@addOnSuccessListener
                    }
                }
                submissionsCollection.document(submissionId).get()
                    .addOnSuccessListener { document ->
                        val res = document.toObject(ExamResult::class.java)?.copy(id = document.id)
                        continuation.resume(Result.success(res))
                    }
                    .addOnFailureListener { e ->
                        continuation.resume(Result.failure(e))
                    }
            }
            .addOnFailureListener {
                submissionsCollection.document(submissionId).get()
                    .addOnSuccessListener { document ->
                        val res = document.toObject(ExamResult::class.java)?.copy(id = document.id)
                        continuation.resume(Result.success(res))
                    }
                    .addOnFailureListener { e ->
                        continuation.resume(Result.failure(e))
                    }
            }
    }

    suspend fun getStudentSubmissions(studentId: String): Result<List<ExamResult>> = suspendCoroutine { continuation ->
        resultsCollection
            .whereEqualTo("studentId", studentId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                try {
                    val list = querySnapshot.documents.mapNotNull { doc ->
                        val appRes = doc.toObject(AppResult::class.java)
                        if (appRes != null) {
                            ExamResult(
                                id = appRes.effectiveResultId,
                                studentId = appRes.studentId,
                                chapterId = appRes.chapterId,
                                chapterName = appRes.chapterName,
                                totalQuestions = appRes.totalQuestions,
                                totalMarks = appRes.totalMarks,
                                mcqTotalMarks = appRes.mcqMarks,
                                mcqObtainedMarks = appRes.obtainedMarks - (appRes.totalMarks - appRes.mcqMarks - appRes.pendingMarks).coerceAtLeast(0),
                                shortQuestionTotalMarks = appRes.shortMarks,
                                shortQuestionObtainedMarks = appRes.shortMarks - appRes.pendingMarks,
                                obtainedMarks = appRes.obtainedMarks,
                                status = appRes.status,
                                submittedAt = appRes.submittedAt
                            )
                        } else null
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
        val listener = resultsCollection
            .whereEqualTo("studentId", studentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    try {
                        val list = snapshot.documents.mapNotNull { doc ->
                            val appRes = doc.toObject(AppResult::class.java)
                            if (appRes != null) {
                                ExamResult(
                                    id = appRes.effectiveResultId,
                                    studentId = appRes.studentId,
                                    chapterId = appRes.chapterId,
                                    chapterName = appRes.chapterName,
                                    totalQuestions = appRes.totalQuestions,
                                    totalMarks = appRes.totalMarks,
                                    mcqTotalMarks = appRes.mcqMarks,
                                    mcqObtainedMarks = appRes.obtainedMarks - (appRes.totalMarks - appRes.mcqMarks - appRes.pendingMarks).coerceAtLeast(0),
                                    shortQuestionTotalMarks = appRes.shortMarks,
                                    shortQuestionObtainedMarks = appRes.shortMarks - appRes.pendingMarks,
                                    obtainedMarks = appRes.obtainedMarks,
                                    status = appRes.status,
                                    submittedAt = appRes.submittedAt
                                )
                            } else null
                        }
                        trySend(Result.success(list))
                    } catch (e: Exception) {
                        trySend(Result.failure(e))
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    // --- TEACHER EVALUATION EXTENSIONS ---

    suspend fun getSubmissionsForChapter(chapterId: String): Result<List<ExamResult>> = suspendCoroutine { continuation ->
        submissionsCollection
            .whereEqualTo("chapterId", chapterId)
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

    suspend fun getStudentAnswersForSubmission(submissionId: String): Result<List<StudentAnswer>> = suspendCoroutine { continuation ->
        studentAnswersCollection
            .whereEqualTo("submissionId", submissionId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                try {
                    val list = querySnapshot.documents.mapNotNull { doc ->
                        doc.toObject(StudentAnswer::class.java)?.copy(id = doc.id)
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
            val teacherUid = auth.currentUser?.uid ?: ""
            val now = Timestamp.now()
            val batch = firestore.batch()

            answers.forEach { ans ->
                val ansDocId = ans.id.ifEmpty { ans.answerId }
                if (ansDocId.isNotEmpty()) {
                    val isShort = ans.questionType.equals("SHORT", ignoreCase = true) || ans.questionType.equals("short", ignoreCase = true)
                    val updatedAns = ans.copy(
                        id = ansDocId,
                        answerId = ansDocId,
                        status = if (isShort) "REVIEWED" else ans.status,
                        reviewedBy = teacherUid,
                        reviewedAt = now
                    )
                    val docRef = studentAnswersCollection.document(ansDocId)
                    batch.set(docRef, updatedAns)
                }
            }

            val subDocRef = submissionsCollection.document(submissionId)
            val resultDocRef = resultsCollection.document(submissionId)

            val newStatus = if (isFullyEvaluated) "COMPLETED" else "PENDING"
            val totalShortPossible = answers.filter {
                it.questionType.equals("SHORT", ignoreCase = true) || it.questionType.equals("short", ignoreCase = true)
            }.sumOf { it.marks }

            val newPendingMarks = (totalShortPossible - shortQuestionObtainedMarks).coerceAtLeast(0)

            val updates = mapOf(
                "shortQuestionObtainedMarks" to shortQuestionObtainedMarks,
                "obtainedMarks" to totalObtainedMarks,
                "status" to newStatus
            )
            batch.update(subDocRef, updates)

            val resultUpdates = mapOf(
                "obtainedMarks" to totalObtainedMarks,
                "pendingMarks" to newPendingMarks,
                "shortMarks" to totalShortPossible,
                "status" to newStatus
            )
            batch.update(resultDocRef, resultUpdates)

            batch.commit()
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
}
