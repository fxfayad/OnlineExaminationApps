package com.myapps.onlineexaminationapps.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class StudentAnswer(
    val id: String = "",
    val submissionId: String = "",
    val studentId: String = "",
    val chapterId: String = "",
    val questionId: String = "",
    val answer: String = "",
    val questionType: String = "", // "mcq" or "short"
    val marks: Int = 0,
    val obtainedMarks: Int = 0,
    val isCorrect: Boolean? = null, // true/false for MCQ, null for short question
    @ServerTimestamp
    val submittedAt: Timestamp? = null
)
