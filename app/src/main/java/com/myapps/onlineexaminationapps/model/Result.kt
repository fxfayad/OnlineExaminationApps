package com.myapps.onlineexaminationapps.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class Result(
    val resultId: String = "",
    val id: String = "",
    val studentId: String = "",
    val chapterId: String = "",
    val chapterName: String = "",
    val totalQuestions: Int = 0,
    val mcqQuestions: Int = 0,
    val shortQuestions: Int = 0,
    val mcqMarks: Int = 0,
    val shortMarks: Int = 0,
    val totalMarks: Int = 0,
    val obtainedMarks: Int = 0,
    val pendingMarks: Int = 0,
    val status: String = "Pending Teacher Evaluation",
    @ServerTimestamp
    val submittedAt: Timestamp? = null
) {
    val effectiveResultId: String
        get() = resultId.ifEmpty { id }

    val percentage: Double
        get() = if (totalMarks > 0) (obtainedMarks.toDouble() / totalMarks) * 100.0 else 0.0
}
