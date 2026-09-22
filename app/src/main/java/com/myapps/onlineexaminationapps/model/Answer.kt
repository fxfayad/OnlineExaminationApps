package com.myapps.onlineexaminationapps.model

data class  Answer(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val chapterId: String = "",
    val questionId: String = "",
    val questionText: String = "",
    val answer: String = "",
    val correctAnswer: String = "",
    @field:JvmField
    val isCorrect: Boolean = false,
    val submittedAt: Long = 0L
)
