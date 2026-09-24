package com.myapps.onlineexaminationapps.firebase

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
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

    private fun mapDocToQuestion(doc: DocumentSnapshot): Question? {
        val q = doc.toObject(Question::class.java) ?: return null
        val finalId = q.id.ifEmpty { q.questionId.ifEmpty { doc.id } }
        val finalTeacher = q.teacherId.ifEmpty { q.createdBy }
        val finalQuestion = q.questionText.ifEmpty { q.question }
        val finalType = if (q.questionType.equals("SHORT", ignoreCase = true) || q.type.equals("short", ignoreCase = true)) "SHORT" else "MCQ"
        val lowercaseType = if (finalType == "SHORT") "short" else "mcq"

        val optA = q.optionA.ifEmpty { q.options.getOrElse(0) { "" } }
        val optB = q.optionB.ifEmpty { q.options.getOrElse(1) { "" } }
        val optC = q.optionC.ifEmpty { q.options.getOrElse(2) { "" } }
        val optD = q.optionD.ifEmpty { q.options.getOrElse(3) { "" } }
        val optsList = if (q.options.isNotEmpty()) q.options else listOf(optA, optB, optC, optD).filter { it.isNotEmpty() }

        val expected = q.expectedAnswer.ifEmpty { q.answer }
        val correct = q.correctAnswer.ifEmpty { if (finalType == "SHORT") expected else "" }

        return q.copy(
            id = finalId,
            questionId = finalId,
            teacherId = finalTeacher,
            createdBy = finalTeacher,
            questionText = finalQuestion,
            question = finalQuestion,
            questionType = finalType,
            type = lowercaseType,
            optionA = optA,
            optionB = optB,
            optionC = optC,
            optionD = optD,
            options = optsList,
            correctAnswer = correct,
            expectedAnswer = expected,
            answer = expected
        )
    }

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
            val qId = docRef.id
            val teacherUid = currentUser.uid
            val text = question.effectiveQuestionText
            val typeUpper = question.effectiveType
            val typeLower = if (typeUpper == "SHORT") "short" else "mcq"

            val optA = question.effectiveOptionA
            val optB = question.effectiveOptionB
            val optC = question.effectiveOptionC
            val optD = question.effectiveOptionD
            val optsList = if (typeUpper == "MCQ") listOf(optA, optB, optC, optD) else emptyList()

            val expected = question.effectiveAnswer
            val correct = if (typeUpper == "MCQ") question.correctAnswer else expected

            val questionWithId = question.copy(
                id = qId,
                questionId = qId,
                chapterId = question.chapterId,
                teacherId = teacherUid,
                createdBy = teacherUid,
                questionText = text,
                question = text,
                questionType = typeUpper,
                type = typeLower,
                optionA = optA,
                optionB = optB,
                optionC = optC,
                optionD = optD,
                options = optsList,
                correctAnswer = correct,
                expectedAnswer = expected,
                answer = expected,
                marks = question.marks,
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
                    val questions = querySnapshot.documents.mapNotNull { mapDocToQuestion(it) }
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
                        val questions = snapshot.documents.mapNotNull { mapDocToQuestion(it) }
                        trySend(Result.success(questions))
                    } catch (e: Exception) {
                        trySend(Result.failure(e))
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun getTeacherQuestions(teacherUid: String): Result<List<Question>> = suspendCoroutine { continuation ->
        questionsCollection.get()
            .addOnSuccessListener { querySnapshot ->
                try {
                    val questions = querySnapshot.documents
                        .mapNotNull { mapDocToQuestion(it) }
                        .filter { it.effectiveTeacherId == teacherUid }
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
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    try {
                        val questions = snapshot.documents
                            .mapNotNull { mapDocToQuestion(it) }
                            .filter { it.effectiveTeacherId == teacherUid }
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
        questionsCollection.document(question.effectiveId).set(question)
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
                val question = if (document.exists()) mapDocToQuestion(document) else null
                continuation.resume(Result.success(question))
            }
            .addOnFailureListener { e ->
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun getQuestionById(chapterId: String, questionId: String): Result<Question?> = getQuestionById(questionId)

    suspend fun deleteQuestion(questionId: String): Result<Unit> = suspendCoroutine { continuation ->
        questionsCollection.document(questionId).delete()
            .addOnSuccessListener {
                continuation.resume(Result.success(Unit))
            }
            .addOnFailureListener { e ->
                continuation.resume(Result.failure(e))
            }
    }

    suspend fun deleteQuestionsByChapter(chapterId: String): Result<Unit> = suspendCoroutine { continuation ->
        questionsCollection.whereEqualTo("chapterId", chapterId).get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    continuation.resume(Result.success(Unit))
                    return@addOnSuccessListener
                }
                val batch = firestore.batch()
                for (doc in querySnapshot.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit()
                    .addOnSuccessListener {
                        continuation.resume(Result.success(Unit))
                    }
                    .addOnFailureListener { e ->
                        continuation.resume(Result.failure(e))
                    }
            }
            .addOnFailureListener { e ->
                continuation.resume(Result.failure(e))
            }
    }
}
