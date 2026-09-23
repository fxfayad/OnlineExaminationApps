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
}
