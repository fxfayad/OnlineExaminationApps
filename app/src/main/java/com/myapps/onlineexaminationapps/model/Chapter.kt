package com.myapps.onlineexaminationapps.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class Chapter(
    val id: String = "",
    val chapterId: String = "",
    val name: String = "",
    val title: String = "",
    val description: String = "",
    val createdBy: String = "",
    val teacherId: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    val displayTitle: String
        get() = title.ifEmpty { name.ifEmpty { "Chapter" } }

    val effectiveTeacherId: String
        get() = teacherId.ifEmpty { createdBy }

    val effectiveId: String
        get() = chapterId.ifEmpty { id }

    val chapterNumber: Int
        get() {
            val titleMatch = Regex("""Chapter\s*(\d+)""", RegexOption.IGNORE_CASE).find(displayTitle)
            if (titleMatch != null) {
                return titleMatch.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
            }
            val idMatch = Regex("""ch_(\d+)""", RegexOption.IGNORE_CASE).find(effectiveId)
            if (idMatch != null) {
                return idMatch.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
            }
            return Int.MAX_VALUE
        }
}
