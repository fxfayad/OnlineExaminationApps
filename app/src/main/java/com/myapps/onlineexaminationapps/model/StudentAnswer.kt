package com.myapps.onlineexaminationapps.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class StudentAnswer(
    val id: String = "",
    val answerId: String = "",
    val submissionId: String = "",
    val studentId: String = "",
    val chapterId: String = "",
    val questionId: String = "",
    val answer: String = "",
    val questionType: String = "", // "MCQ" or "SHORT", "mcq" or "short"
    val marks: Int = 0,
    val obtainedMarks: Int = 0,
    val isCorrect: Boolean? = null,
    val status: String = "PENDING", // "PENDING" or "REVIEWED"
    val reviewedBy: String = "",
    @ServerTimestamp
    val reviewedAt: Timestamp? = null,
    @ServerTimestamp
    val submittedAt: Timestamp? = null
) {
    val effectiveAnswerId: String
        get() = answerId.ifEmpty { id }
}
