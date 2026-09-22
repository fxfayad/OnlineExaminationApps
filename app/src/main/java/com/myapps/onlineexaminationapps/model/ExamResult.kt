package com.myapps.onlineexaminationapps.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class ExamResult(
    val id: String = "", // submissionId
    val studentId: String = "",
    val chapterId: String = "",
    val chapterName: String = "",
    val totalQuestions: Int = 0,
    val totalMarks: Int = 0,
    val mcqTotalMarks: Int = 0,
    val mcqObtainedMarks: Int = 0,
    val shortQuestionTotalMarks: Int = 0,
    val shortQuestionObtainedMarks: Int = 0,
    val obtainedMarks: Int = 0,
    val status: String = "Pending Teacher Evaluation", // "Completed" or "Pending Teacher Evaluation"
    @ServerTimestamp
    val submittedAt: Timestamp? = null
) {
    val percentage: Double
        get() = if (totalMarks > 0) (obtainedMarks.toDouble() / totalMarks.toDouble()) * 100.0 else 0.0
}
