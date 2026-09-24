package com.myapps.onlineexaminationapps.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class Question(
    val id: String = "",
    val questionId: String = "",
    val chapterId: String = "",
    val teacherId: String = "",
    val createdBy: String = "",
    val questionText: String = "",
    val question: String = "",
    val questionType: String = "", // "MCQ" or "SHORT"
    val type: String = "", // "mcq" or "short"
    val optionA: String = "",
    val optionB: String = "",
    val optionC: String = "",
    val optionD: String = "",
    val options: List<String> = emptyList(),
    val correctAnswer: String = "",
    val expectedAnswer: String = "",
    val answer: String = "",
    val marks: Int = 1,
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    val effectiveId: String
        get() = questionId.ifEmpty { id }

    val effectiveTeacherId: String
        get() = teacherId.ifEmpty { createdBy }

    val effectiveQuestionText: String
        get() = questionText.ifEmpty { question }

    val effectiveType: String
        get() = if (questionType.equals("SHORT", ignoreCase = true) || type.equals("short", ignoreCase = true)) "SHORT" else "MCQ"

    val isMcq: Boolean
        get() = effectiveType == "MCQ"

    val effectiveOptionA: String
        get() = optionA.ifEmpty { options.getOrElse(0) { "" } }

    val effectiveOptionB: String
        get() = optionB.ifEmpty { options.getOrElse(1) { "" } }

    val effectiveOptionC: String
        get() = optionC.ifEmpty { options.getOrElse(2) { "" } }

    val effectiveOptionD: String
        get() = optionD.ifEmpty { options.getOrElse(3) { "" } }

    val effectiveAnswer: String
        get() = expectedAnswer.ifEmpty { answer.ifEmpty { correctAnswer } }

    val effectiveOptions: List<String>
        get() = if (options.isNotEmpty()) options else listOf(effectiveOptionA, effectiveOptionB, effectiveOptionC, effectiveOptionD).filter { it.isNotEmpty() }
}
