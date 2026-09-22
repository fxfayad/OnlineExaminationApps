package com.myapps.onlineexaminationapps.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class Question(
    val id: String = "",
    val chapterId: String = "",
    val question: String = "",
    val type: String = "", // "mcq" or "short"
    val options: List<String> = emptyList(),
    val correctAnswer: String = "",
    val marks: Int = 1,
    val createdBy: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    val optionA: String get() = options.getOrElse(0) { "" }
    val optionB: String get() = options.getOrElse(1) { "" }
    val optionC: String get() = options.getOrElse(2) { "" }
    val optionD: String get() = options.getOrElse(3) { "" }
    val answer: String get() = correctAnswer
}
